package org.opentripplanner.api.mapping;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

public class DateTimeMapper {

  public static Instant ofNullableDate(Date date) {
    return Optional.ofNullable(date).map(Date::toInstant).orElse(null);
  }
}
