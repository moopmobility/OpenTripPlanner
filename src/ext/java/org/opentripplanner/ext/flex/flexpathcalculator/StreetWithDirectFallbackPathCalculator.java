package org.opentripplanner.ext.flex.flexpathcalculator;

import javax.annotation.Nullable;
import org.opentripplanner.standalone.config.sandbox.FlexConfig;
import org.opentripplanner.street.model.vertex.Vertex;

public class StreetWithDirectFallbackPathCalculator implements FlexPathCalculator {

  private final DirectFlexPathCalculator directFlexPathCalculator;
  private final StreetFlexPathCalculator streetFlexPathCalculator;

  public StreetWithDirectFallbackPathCalculator(boolean reverseDirection, FlexConfig config) {
    directFlexPathCalculator = new DirectFlexPathCalculator(config);
    streetFlexPathCalculator = new StreetFlexPathCalculator(reverseDirection, config);
  }

  @Nullable
  @Override
  public FlexPath calculateFlexPath(Vertex fromv, Vertex tov, int fromStopIndex, int toStopIndex) {
    var path = streetFlexPathCalculator.calculateFlexPath(fromv, tov, fromStopIndex, toStopIndex);
    return path == null
      ? directFlexPathCalculator.calculateFlexPath(fromv, tov, fromStopIndex, toStopIndex)
      : path;
  }
}
