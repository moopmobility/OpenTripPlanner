package org.opentripplanner.routing.algorithm.raptoradapter.router;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.framework.application.OTPRequestTimeoutException;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.path.RaptorPath;
import org.opentripplanner.raptor.api.response.RaptorResponse;
import org.opentripplanner.routing.algorithm.mapping.RaptorPathToItineraryMapper;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressRouter;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.FlexAccessEgressRouter;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DefaultAccessEgress;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitLayer;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.AccessEgressMapper;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorRequestMapper;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RouteRequestTransitDataProviderFilter;
import org.opentripplanner.routing.algorithm.transferoptimization.configure.TransferOptimizationServiceConfigurator;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.routing.framework.DebugTimingAggregator;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.street.model.vertex.TransitStopVertex;
import org.opentripplanner.street.search.TemporaryVerticesContainer;
import org.opentripplanner.street.search.request.StreetSearchRequestMapper;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;

public class TransitRouter {

  public static final int NOT_SET = -1;

  private final RouteRequest request;
  private final OtpServerRequestContext serverContext;
  private final DebugTimingAggregator debugTimingAggregator;
  private final ZonedDateTime transitSearchTimeZero;
  private final AdditionalSearchDays additionalSearchDays;

  private TransitRouter(
    RouteRequest request,
    OtpServerRequestContext serverContext,
    ZonedDateTime transitSearchTimeZero,
    AdditionalSearchDays additionalSearchDays,
    DebugTimingAggregator debugTimingAggregator
  ) {
    this.request = request;
    this.serverContext = serverContext;
    this.transitSearchTimeZero = transitSearchTimeZero;
    this.additionalSearchDays = additionalSearchDays;
    this.debugTimingAggregator = debugTimingAggregator;
  }

  public static TransitRouterResult route(
    RouteRequest request,
    OtpServerRequestContext serverContext,
    ZonedDateTime transitSearchTimeZero,
    AdditionalSearchDays additionalSearchDays,
    DebugTimingAggregator debugTimingAggregator
  ) {
    var transitRouter = new TransitRouter(
      request,
      serverContext,
      transitSearchTimeZero,
      additionalSearchDays,
      debugTimingAggregator
    );
    try (
      var temporaryVertices = new TemporaryVerticesContainer(
        serverContext.graph(),
        request,
        request.journey().access().mode(),
        request.journey().egress().mode()
      )
    ) {
      return transitRouter.route(temporaryVertices);
    }
  }

  private TransitRouterResult route(TemporaryVerticesContainer temporaryVertices) {
    if (!request.journey().transit().enabled()) {
      return new TransitRouterResult(List.of(), null);
    }

    if (!serverContext.transitService().transitFeedCovers(request.dateTime())) {
      throw new RoutingValidationException(
        List.of(new RoutingError(RoutingErrorCode.OUTSIDE_SERVICE_PERIOD, InputField.DATE_TIME))
      );
    }

    var transitLayer = request.preferences().transit().ignoreRealtimeUpdates()
      ? serverContext.transitService().getTransitLayer()
      : serverContext.transitService().getRealtimeTransitLayer();

    var requestTransitDataProvider = createRequestTransitDataProvider(transitLayer);

    debugTimingAggregator.finishedPatternFiltering();

    var accessEgresses = getAccessEgresses(transitLayer, temporaryVertices);

    debugTimingAggregator.finishedAccessEgress(
      accessEgresses.getAccesses().size(),
      accessEgresses.getEgresses().size()
    );

    // Prepare transit search
    var raptorRequest = RaptorRequestMapper.mapRequest(
      request,
      transitSearchTimeZero,
      serverContext.raptorConfig().isMultiThreaded(),
      accessEgresses.getAccesses(),
      accessEgresses.getEgresses(),
      serverContext.meterRegistry()
    );

    // Route transit
    var raptorService = new RaptorService<>(serverContext.raptorConfig());
    var transitResponse = raptorService.route(raptorRequest, requestTransitDataProvider);

    checkIfTransitConnectionExists(transitResponse);

    debugTimingAggregator.finishedRaptorSearch();

    Collection<RaptorPath<TripSchedule>> paths = transitResponse.paths();

    if (OTPFeature.OptimizeTransfers.isOn() && !transitResponse.containsUnknownPaths()) {
      paths =
        TransferOptimizationServiceConfigurator
          .createOptimizeTransferService(
            transitLayer::getStopByIndex,
            requestTransitDataProvider.stopNameResolver(),
            serverContext.transitService().getTransferService(),
            requestTransitDataProvider,
            transitLayer.getStopBoardAlightCosts(),
            request.preferences().transfer().optimization()
          )
          .optimize(transitResponse.paths());
    }

    // Create itineraries

    RaptorPathToItineraryMapper<TripSchedule> itineraryMapper = new RaptorPathToItineraryMapper<>(
      serverContext.graph(),
      serverContext.transitService(),
      transitLayer,
      transitSearchTimeZero,
      request
    );

    List<Itinerary> itineraries = paths.stream().map(itineraryMapper::createItinerary).toList();

    debugTimingAggregator.finishedItineraryCreation();

    return new TransitRouterResult(itineraries, transitResponse.requestUsed().searchParams());
  }

  private AccessEgresses getAccessEgresses(
    TransitLayer transitLayer,
    TemporaryVerticesContainer temporaryVertices
  ) {
    var accessEgressMapper = new AccessEgressMapper();
    var accessList = new ArrayList<DefaultAccessEgress>();
    var egressList = new ArrayList<DefaultAccessEgress>();

    var accessCalculator = (Runnable) () -> {
      debugTimingAggregator.startedAccessCalculating();
      accessList.addAll(
        getAccessEgresses(transitLayer, accessEgressMapper, temporaryVertices, false)
      );
      debugTimingAggregator.finishedAccessCalculating();
    };

    var egressCalculator = (Runnable) () -> {
      debugTimingAggregator.startedEgressCalculating();
      egressList.addAll(
        getAccessEgresses(transitLayer, accessEgressMapper, temporaryVertices, true)
      );
      debugTimingAggregator.finishedEgressCalculating();
    };

    if (OTPFeature.ParallelRouting.isOn()) {
      List<Callable<Object>> tasks = List.of(
        Executors.callable(accessCalculator),
        Executors.callable(egressCalculator)
      );
      try {
        serverContext.raptorConfig().threadPool().invokeAll(tasks);
      } catch (InterruptedException e) {
        throw new OTPRequestTimeoutException();
      }
    } else {
      accessCalculator.run();
      egressCalculator.run();
    }

    verifyAccessEgress(accessList, egressList);

    return new AccessEgresses(accessList, egressList);
  }

  private Collection<DefaultAccessEgress> getAccessEgresses(
    TransitLayer transitLayer,
    AccessEgressMapper accessEgressMapper,
    TemporaryVerticesContainer temporaryVertices,
    boolean isEgress
  ) {
    var streetRequest = isEgress ? request.journey().egress() : request.journey().access();

    // Prepare access/egress lists
    RouteRequest accessRequest = request.clone();

    // If a stop/station was explicitly provided only allow arriving by scheduled transit
    var place = isEgress ? request.to() : request.from();
    if (
      place.stopId != null && request.preferences().system().allowOnlyScheduledTransitDirectToStop()
    ) {
      return createDirectStopAccessEgress(isEgress, temporaryVertices, streetRequest);
    }

    if (!isEgress) {
      accessRequest.journey().rental().setAllowArrivingInRentedVehicleAtDestination(false);
    }

    var nearbyStops = AccessEgressRouter.streetSearch(
      accessRequest,
      temporaryVertices,
      serverContext.transitService(),
      streetRequest,
      serverContext.dataOverlayContext(accessRequest),
      isEgress,
      accessRequest.preferences().street().maxAccessEgressDuration().valueOf(streetRequest.mode())
    );

    var results = new ArrayList<>(accessEgressMapper.mapNearbyStops(nearbyStops, isEgress));

    // Special handling of flex accesses
    if (OTPFeature.FlexRouting.isOn() && streetRequest.mode() == StreetMode.FLEXIBLE) {
      var flexAccessList = FlexAccessEgressRouter.routeAccessEgress(
        accessRequest,
        temporaryVertices,
        serverContext,
        additionalSearchDays,
        serverContext.flexConfig(),
        serverContext.dataOverlayContext(accessRequest),
        isEgress
      );

      results.addAll(accessEgressMapper.mapFlexAccessEgresses(flexAccessList, isEgress));

      if (place.forcedStopId != null) {
        var stopIds = serverContext
          .transitService()
          .findStopOrChildStops(place.forcedStopId)
          .stream()
          .filter(RegularStop.class::isInstance)
          .map(RegularStop.class::cast)
          .map(StopLocation::getIndex)
          .collect(Collectors.toSet());

        results.removeIf(item -> !stopIds.contains(item.stop()));
      }

      var flexConfig = serverContext.flexConfig();
      if (flexConfig.allowOnlyStopReachedOnBoard()) {
        // Remove any flex trips where the stop was not reached on-board
        results.removeIf(item -> item.hasRides() && !item.stopReachedOnBoard());
      }

      if (flexConfig.minimumStreetDistanceForFlex() > 0) {
        var stopsWithShortWalk = results
          .stream()
          .filter(item ->
            !item.hasRides() &&
            item.getLastState().getWalkDistance() <= flexConfig.minimumStreetDistanceForFlex()
          )
          .map(DefaultAccessEgress::stop)
          .map(transitLayer::getStopByIndex)
          .filter(Objects::nonNull)
          .flatMap(stop ->
            stop.getHighestParentStation() != null
              ? stop.getHighestParentStation().getChildStops().stream()
              : Stream.of(stop)
          )
          .map(StopLocation::getIndex)
          .collect(Collectors.toSet());

        // Remove all flex items, which are reachable on-street with a short walk (or have stations which are reachable)
        results.removeIf(item -> item.hasRides() && stopsWithShortWalk.contains(item.stop()));
      }

      if (flexConfig.maximumStreetDistanceForWalkingIfFlexAvailable() > 0) {
        var stopsWithLongWalk = results
          .stream()
          .filter(item ->
            !item.hasRides() &&
            item.getLastState().getWalkDistance() >=
            flexConfig.maximumStreetDistanceForWalkingIfFlexAvailable()
          )
          .map(DefaultAccessEgress::stop)
          .map(transitLayer::getStopByIndex)
          .filter(Objects::nonNull)
          .flatMap(stop ->
            stop.getHighestParentStation() != null
              ? stop.getHighestParentStation().getChildStops().stream()
              : Stream.of(stop)
          )
          .map(StopLocation::getIndex)
          .collect(Collectors.toSet());

        var stopsWithFlex = results
          .stream()
          .filter(RaptorAccessEgress::hasRides)
          .map(DefaultAccessEgress::stop)
          .map(transitLayer::getStopByIndex)
          .filter(Objects::nonNull)
          .flatMap(stop ->
            stop.getHighestParentStation() != null
              ? stop.getHighestParentStation().getChildStops().stream()
              : Stream.of(stop)
          )
          .map(StopLocation::getIndex)
          .collect(Collectors.toSet());

        // Remove walking to stations that have long walks and are accessible by flex
        results.removeIf(item ->
          !item.hasRides() &&
          stopsWithLongWalk.contains(item.stop()) &&
          stopsWithFlex.contains(item.stop())
        );
      }

      if (flexConfig.removeWalkingIfFlexIsFaster()) {
        var minFlexTimeForStop = results
          .stream()
          .filter(DefaultAccessEgress::hasRides)
          .collect(
            Collectors.toMap(
              DefaultAccessEgress::stop,
              item -> item.getLastState().getElapsedTimeSeconds(),
              Math::min
            )
          )
          .entrySet()
          .stream()
          .flatMap(entry -> {
            var stop = transitLayer.getStopByIndex(entry.getKey());
            if (stop != null && stop.getHighestParentStation() != null) {
              return stop
                .getHighestParentStation()
                .getChildStops()
                .stream()
                .map(childStop -> Map.entry(childStop.getIndex(), entry.getValue()));
            } else {
              return Stream.of(entry);
            }
          })
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Math::min));

        // Remove all walk items, which have a faster flex option
        results.removeIf(item ->
          !item.hasRides() &&
          minFlexTimeForStop.getOrDefault(item.stop(), Long.MAX_VALUE) <
          item.getLastState().getElapsedTimeSeconds()
        );
      }
    }

    return results;
  }

  private Collection<DefaultAccessEgress> createDirectStopAccessEgress(
    boolean isEgress,
    TemporaryVerticesContainer temporaryVertices,
    StreetRequest streetRequest
  ) {
    var vertices = isEgress != request.arriveBy()
      ? temporaryVertices.getToVertices()
      : temporaryVertices.getFromVertices();

    var streetSearchRequest = StreetSearchRequestMapper
      .map(request)
      .withMode(streetRequest.mode())
      .withArriveBy(request.arriveBy())
      .build();

    return vertices
      .stream()
      .filter(TransitStopVertex.class::isInstance)
      .map(TransitStopVertex.class::cast)
      .map(vertex ->
        new DefaultAccessEgress(vertex.getStop().getIndex(), new State(vertex, streetSearchRequest))
      )
      .toList();
  }

  private RaptorRoutingRequestTransitData createRequestTransitDataProvider(
    TransitLayer transitLayer
  ) {
    return new RaptorRoutingRequestTransitData(
      transitLayer,
      transitSearchTimeZero,
      additionalSearchDays.additionalSearchDaysInPast(),
      additionalSearchDays.additionalSearchDaysInFuture(),
      new RouteRequestTransitDataProviderFilter(request),
      request
    );
  }

  private void verifyAccessEgress(Collection<?> access, Collection<?> egress) {
    boolean accessExist = !access.isEmpty();
    boolean egressExist = !egress.isEmpty();

    if (accessExist && egressExist) {
      return;
    }

    List<RoutingError> routingErrors = new ArrayList<>();
    if (!accessExist) {
      routingErrors.add(
        new RoutingError(RoutingErrorCode.NO_STOPS_IN_RANGE, InputField.FROM_PLACE)
      );
    }
    if (!egressExist) {
      routingErrors.add(new RoutingError(RoutingErrorCode.NO_STOPS_IN_RANGE, InputField.TO_PLACE));
    }

    throw new RoutingValidationException(routingErrors);
  }

  /**
   * If no paths or search window is found, we assume there is no transit connection between the
   * origin and destination.
   */
  private void checkIfTransitConnectionExists(RaptorResponse<TripSchedule> response) {
    int searchWindowUsed = response.requestUsed().searchParams().searchWindowInSeconds();
    if (searchWindowUsed <= 0 && response.paths().isEmpty()) {
      throw new RoutingValidationException(
        List.of(new RoutingError(RoutingErrorCode.NO_TRANSIT_CONNECTION, null))
      );
    }
  }
}
