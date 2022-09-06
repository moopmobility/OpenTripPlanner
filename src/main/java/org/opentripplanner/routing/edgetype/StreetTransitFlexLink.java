package org.opentripplanner.routing.edgetype;

import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.vertextype.StreetVertex;
import org.opentripplanner.routing.vertextype.TransitStopVertex;

/**
 * This represents the connection between a street vertex and a transit vertex when boarding / alighting
 * a flex trip.
 */
public class StreetTransitFlexLink extends StreetTransitEntityLink<TransitStopVertex> {

  public StreetTransitFlexLink(StreetVertex fromv, TransitStopVertex tov) {
    super(fromv, tov, tov.getWheelchairAccessibility());
  }

  public StreetTransitFlexLink(TransitStopVertex fromv, StreetVertex tov) {
    super(fromv, tov, fromv.getWheelchairAccessibility());
  }

  protected int getStreetToStopTime() {
    return 0;
  }

  @Override
  public State traverse(State s0) {
    // Only allow access by car for flex trips
    if (
      TraverseMode.CAR != s0.getNonTransitMode() ||
      s0.getRoutingContext() == null ||
      s0.getRoutingContext().opt.flexParameters == null
    ) {
      return null;
    }

    return super.traverse(s0);
  }

  public String toString() {
    return "StreetTransitFlexLink(" + fromv + " -> " + tov + ")";
  }
}
