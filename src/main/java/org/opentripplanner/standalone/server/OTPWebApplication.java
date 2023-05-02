package org.opentripplanner.standalone.server;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jersey.server.DefaultJerseyTagsProvider;
import io.micrometer.core.instrument.binder.jersey.server.MetricsApplicationEventListener;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.ws.rs.core.Application;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.Binder;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.server.ServerProperties;
import org.opentripplanner.api.common.OTPExceptionMapper;
import org.opentripplanner.api.configuration.APIEndpoints;
import org.opentripplanner.api.json.JSONObjectMapperProvider;
import org.opentripplanner.api.parameter.InstantParamConverterProvider;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * A JAX-RS Application subclass which provides hard-wired configuration of an OTP server. Avoids
 * auto-scanning of any kind, and keeps injection to a bare minimum using HK2, the injection library
 * Jersey itself uses.
 * <p>
 * Jersey has its own ResourceConfig class which is a subclass of Application. We can get away with
 * not using any Jersey-specific "conveniences" and stick with stock JAX-RS.
 */
public class OTPWebApplication extends Application {

  /* This object groups together all the modules for a single running OTP server. */
  private final Supplier<OtpServerRequestContext> contextProvider;

  static {
    // Remove existing handlers attached to the j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    // Bridge j.u.l (used by Jersey) to the SLF4J root logger, so all logging goes through the same
    // API
    SLF4JBridgeHandler.install();
  }

  public OTPWebApplication(Supplier<OtpServerRequestContext> contextProvider) {
    this.contextProvider = contextProvider;
  }

  /**
   * This method registers classes with Jersey to define web resources and enable custom features.
   * These are classes (not instances) that will be instantiated by Jersey for each request (they
   * are request-scoped). Types that have been confirmed to work are: annotated resources, {@code
   * ContextResolver<ObjectMapper>} implementation, ContainerResponseFilter and
   * ContainerRequestFilter. Note that the listed classes do not need to be annotated with @Provider
   * -- that is for scanning config.
   */
  @Override
  public Set<Class<?>> getClasses() {
    // Add API Endpoints defined in the api package
    Set<Class<?>> classes = new HashSet<>(APIEndpoints.listAPIEndpoints());

    /* Features and Filters: extend Jersey, manipulate requests and responses. */
    classes.addAll(Set.of(CorsFilter.class, EtagRequestFilter.class, VaryRequestFilter.class));

    return classes;
  }

  /**
   * Like getClasses, this method declares web resources, providers, and features to the JAX-RS
   * implementation. However, these are single instances that will be reused for all requests (they
   * are singleton-scoped).
   * <p>
   * See https://jersey.java.net/apidocs/latest/jersey/javax/ws/rs/core/Application.html#getSingletons()
   * Leave {@code <Object>} out of method signature to avoid confusing the Guava type inference.
   */
  @Override
  public Set<Object> getSingletons() {
    var singletons = new HashSet<>(
      List.of(
        // Show exception messages in responses
        new OTPExceptionMapper(),
        // Enable Jackson JSON response serialization
        new JacksonJsonProvider(),
        // Serialize POJOs (unannotated) JSON using Jackson
        new JSONObjectMapperProvider(),
        // Supports converting ISO8601 timestamps to Instants
        new InstantParamConverterProvider(),
        // Allow injecting the OTP server object into Jersey resource classes
        makeBinder(contextProvider),
        // Add performance instrumentation of Jersey requests to micrometer
        getMetricsApplicationEventListener()
      )
    );

    if (OTPFeature.ActuatorAPI.isOn()) {
      singletons.add(getBoundPrometheusRegistry());
    }

    return singletons;
  }

  /**
   * Enabling tracing allows us to see how web resource names were matched from the client, in
   * headers. Disable auto-discovery of features because it's extremely obnoxious to debug and
   * interacts in confusing ways with manually registered features.
   */
  @Override
  public Map<String, Object> getProperties() {
    Map<String, Object> props = new HashMap<>();
    props.put(ServerProperties.TRACING, Boolean.TRUE);
    props.put(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE);
    return props;
  }

  /**
   * Return an HK2 Binder that injects this specific OtpServerContext instance into Jersey web
   * resources. This should be registered in the ResourceConfig (Jersey) or Application (JAX-RS) as
   * a singleton. Jersey forces us to use injection to get application context into HTTP method
   * handlers, but in OTP we always just inject this OTP server context and grab anything else we
   * need (graph and other application components) from this single object.
   * <p>
   * More on custom injection in Jersey 2:
   * http://jersey.576304.n2.nabble.com/Custom-providers-in-Jersey-2-tp7580699p7580715.html
   */
  private Binder makeBinder(Supplier<OtpServerRequestContext> contextProvider) {
    return new AbstractBinder() {
      @Override
      protected void configure() {
        bindFactory(contextProvider).to(OtpServerRequestContext.class);
      }
    };
  }

  private MetricsApplicationEventListener getMetricsApplicationEventListener() {
    return new MetricsApplicationEventListener(
      Metrics.globalRegistry,
      new DefaultJerseyTagsProvider(),
      "http.server.requests",
      true
    );
  }

  /**
   * Instantiate and add the prometheus micrometer registry to the global composite registry.
   *
   * @return A AbstractBinder, which can be used to inject the registry into the Actuator API calls
   */
  private Binder getBoundPrometheusRegistry() {
    PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(
      PrometheusConfig.DEFAULT
    );

    Metrics.globalRegistry.add(prometheusRegistry);

    return new AbstractBinder() {
      @Override
      protected void configure() {
        bind(prometheusRegistry).to(PrometheusMeterRegistry.class);
      }
    };
  }
}
