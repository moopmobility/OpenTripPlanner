package org.opentripplanner.api.parameter;

import jakarta.ws.rs.ext.ParamConverter;
import java.time.Instant;

public class InstantParamConverter implements ParamConverter<Instant> {

  @Override
  public Instant fromString(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    if (s.matches("^\\d+$")) {
      return Instant.ofEpochMilli(Long.parseLong(s));
    } else {
      return Instant.parse(s);
    }
  }

  @Override
  public String toString(Instant instant) {
    return instant.toString();
  }
}
