package org.opentripplanner.api.parameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Instant;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

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
