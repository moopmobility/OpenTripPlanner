package org.opentripplanner.standalone.config.sandbox;

import java.time.Duration;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.standalone.config.NodeAdapter;

public class FlexConfig {

  public static final int DEFAULT_MAX_TRANSFER_SECONDS = 60 * 5; // 5 minutes
  public static final int DEFAULT_MAX_TRANSFER_METERS = 300;
  public static final Duration DEFAULT_MAX_TRIP_DURATION = Duration.ofMinutes(45);
  public static final Duration DEFAULT_MAX_FORCED_TRIP_DURATION = Duration.ofHours(2);
  public static final double DEFAULT_MAX_VEHICLE_SPEED = 29.; // ~104 km/h
  public static final double DEFAULT_STREET_TIME_FACTOR = 1.25; // Taking the bus/taxi is 25% slower than the car

  public final int maxTransferSeconds;
  public final Duration maxTripDuration;
  public final Duration maxForcedTripDuration;
  public final double maxVehicleSpeed;
  public final double streetTimeFactor;
  public final boolean allowOnlyStopReachedOnBoard;
  public final int minimumStreetDistanceForFlex;
  public final boolean removeWalkingIfFlexFaster;

  public FlexConfig(NodeAdapter json) {
    maxTransferSeconds = json.asInt("maxTransferDurationSeconds", DEFAULT_MAX_TRANSFER_SECONDS);
    maxTripDuration = json.asDuration("maxTripDuration", DEFAULT_MAX_TRIP_DURATION);
    maxForcedTripDuration =
      json.asDuration("maxForcedTripDuration", DEFAULT_MAX_FORCED_TRIP_DURATION);
    maxVehicleSpeed = json.asDouble("maxVehicleSpeed", DEFAULT_MAX_VEHICLE_SPEED);
    streetTimeFactor = json.asDouble("streetTimeFactor", DEFAULT_STREET_TIME_FACTOR);
    allowOnlyStopReachedOnBoard = json.asBoolean("allowOnlyStopReachedOnBoard", false);
    minimumStreetDistanceForFlex = json.asInt("minimumStreetDistanceForFlex", 0);
    removeWalkingIfFlexFaster = json.asBoolean("removeWalkingIfFlexFaster", false);
  }

  public FlexParameters toFlexParameters(RoutingRequest request, boolean useMaxTripDuration) {
    return new FlexParameters(
      maxVehicleSpeed,
      streetTimeFactor,
      (maxTransferSeconds * request.walkSpeed),
      useMaxTripDuration ? maxTripDuration : maxForcedTripDuration
    );
  }
}
