package org.opentripplanner.api.parameter;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Instant;

@Provider
public class InstantParamConverterProvider implements ParamConverterProvider {

  @Override
  public <T> ParamConverter<T> getConverter(
    Class<T> rawType,
    Type genericType,
    Annotation[] annotations
  ) {
    if (rawType.equals(Instant.class)) return (ParamConverter<T>) new InstantParamConverter();
    return null;
  }
}
