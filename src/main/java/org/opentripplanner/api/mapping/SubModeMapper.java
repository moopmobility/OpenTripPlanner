package org.opentripplanner.api.mapping;

import org.opentripplanner.transit.model.basic.SubMode;

public class SubModeMapper {

  public static Integer mapToGtfsRouteType(SubMode subMode) {
    if (subMode.name().matches("^\\d+$")) {
      return Integer.parseInt(subMode.name());
    } else {
      return null;
    }
  }
}
