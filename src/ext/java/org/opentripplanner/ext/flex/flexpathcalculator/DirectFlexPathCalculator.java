package org.opentripplanner.ext.flex.flexpathcalculator;

import java.time.Duration;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.framework.geometry.GeometryUtils;
import org.opentripplanner.framework.geometry.SphericalDistanceLibrary;
import org.opentripplanner.standalone.config.sandbox.FlexConfig;
import org.opentripplanner.street.model.vertex.Vertex;

/**
 * Calculated driving times and distance based on direct distance and fixed average driving speed.
 */
public class DirectFlexPathCalculator implements FlexPathCalculator {

  private static final int DIRECT_EXTRA_TIME = 5 * 60;

  private final double flexSpeed;
  private final Duration maxFlexTripDuration;

  public DirectFlexPathCalculator(FlexConfig config) {
    this.flexSpeed = config.directFlexPathSpeed();
    this.maxFlexTripDuration = config.maxFlexTripDuration();
  }

  @Override
  public FlexPath calculateFlexPath(Vertex fromv, Vertex tov, int fromStopIndex, int toStopIndex) {
    double distance = SphericalDistanceLibrary.distance(fromv.getCoordinate(), tov.getCoordinate());
    double durationSeconds = (distance / flexSpeed) + DIRECT_EXTRA_TIME;

    if (maxFlexTripDuration.toSeconds() < durationSeconds) {
      return null;
    }

    LineString geometry = GeometryUtils
      .getGeometryFactory()
      .createLineString(new Coordinate[] { fromv.getCoordinate(), tov.getCoordinate() });

    return new FlexPath((int) distance, (int) durationSeconds, () -> geometry);
  }
}
