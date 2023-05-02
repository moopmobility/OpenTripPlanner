package org.opentripplanner.standalone.config.sandbox;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.*;

import java.time.Duration;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

public class FlexConfig {

  public static final FlexConfig DEFAULT = new FlexConfig();

  public static final String ACCESS_EGRESS_DESCRIPTION =
    """
    If you have multiple overlapping flex zones the high default value can lead to performance problems.
    A lower value means faster routing.
    
    Depending on your service this might be what you want to do anyway: many flex services are used
    by passengers with mobility problems so offering a long walk might be problematic. In other words,
    if you can walk 45 minutes to a flex stop/zone you're unlikely to be the target audience for those
    services.
    """;

  private final Duration maxTransferDuration;
  private final Duration maxFlexTripDuration;
  private final Duration maxAccessWalkDuration;
  private final Duration maxEgressWalkDuration;

  private final double maxVehicleSpeed;
  private final double streetTimeFactor;
  private final boolean allowOnlyStopReachedOnBoard;
  private final int minimumStreetDistanceForFlex;
  private final boolean removeWalkingIfFlexIsFaster;

  private FlexConfig() {
    maxTransferDuration = Duration.ofMinutes(5);
    maxFlexTripDuration = Duration.ofMinutes(45);
    maxAccessWalkDuration = Duration.ofMinutes(45);
    maxEgressWalkDuration = Duration.ofMinutes(45);
    maxVehicleSpeed = 29.; // 104 km/h
    streetTimeFactor = 1.25; // taking the bus/taxi is 25% slower than the car
    allowOnlyStopReachedOnBoard = false;
    minimumStreetDistanceForFlex = 0;
    removeWalkingIfFlexIsFaster = false;
  }

  public FlexConfig(NodeAdapter root, String parameterName) {
    var json = root
      .of(parameterName)
      .since(V2_1)
      .summary("Configuration for flex routing.")
      .asObject();

    this.maxTransferDuration =
      json
        .of("maxTransferDuration")
        .since(V2_3)
        .summary(
          "How long should a passenger be allowed to walk after getting out of a flex vehicle " +
          "and transferring to a flex or transit one."
        )
        .description(
          """
            This was mainly introduced to improve performance which is also the reason for not
            using the existing value with the same name: fixed schedule transfers are computed
            during the graph build but flex ones are calculated at request time and are more
            sensitive to slowdown.
            
            A lower value means that the routing is faster.
            """
        )
        .asDuration(DEFAULT.maxTransferDuration);

    maxFlexTripDuration =
      json
        .of("maxFlexTripDuration")
        .since(V2_3)
        .summary("How long can a non-scheduled flex trip at maximum be.")
        .description(
          "This is used for all trips which are of type `UnscheduledTrip`. The value includes " +
          "the access/egress duration to the boarding/alighting of the flex trip, as well as the " +
          "connection to the transit stop."
        )
        .asDuration(DEFAULT.maxFlexTripDuration);

    maxAccessWalkDuration =
      json
        .of("maxAccessWalkDuration")
        .since(V2_3)
        .summary(
          "The maximum duration the passenger will be allowed to walk to reach a flex stop or zone."
        )
        .description(ACCESS_EGRESS_DESCRIPTION)
        .asDuration(DEFAULT.maxAccessWalkDuration);

    maxEgressWalkDuration =
      json
        .of("maxEgressWalkDuration")
        .since(V2_3)
        .summary(
          "The maximum duration the passenger will be allowed to walk after leaving the flex vehicle at the final destination."
        )
        .description(ACCESS_EGRESS_DESCRIPTION)
        .asDuration(DEFAULT.maxEgressWalkDuration);

    streetTimeFactor =
      json
        .of("streetTimeFactor")
        .since(V_TV)
        .summary("Multiplier for how much slower the bus travels compared to cars.")
        .asDouble(DEFAULT.streetTimeFactor);

    maxVehicleSpeed =
      json
        .of("maxVehicleSpeed")
        .since(V_TV)
        .summary("The maximum vehicle speed (car speed)")
        .asDouble(DEFAULT.maxVehicleSpeed);

    allowOnlyStopReachedOnBoard =
      json
        .of("allowOnlyStopReachedOnBoard")
        .since(V_TV)
        .summary("Require flex trips terminate at a _normal_ stop, without walking.")
        .asBoolean(DEFAULT.allowOnlyStopReachedOnBoard);

    minimumStreetDistanceForFlex =
      json
        .of("minimumStreetDistanceForFlex")
        .since(V_TV)
        .summary("Minimum distance to travel on a flex vehicle.")
        .asInt(DEFAULT.minimumStreetDistanceForFlex);

    removeWalkingIfFlexIsFaster =
      json
        .of("removeWalkingIfFlexIsFaster")
        .since(V_TV)
        .summary("Removing walking access/egress options to a stop if flex is faster.")
        .asBoolean(DEFAULT.allowOnlyStopReachedOnBoard);
  }

  public Duration maxFlexTripDuration() {
    return maxFlexTripDuration;
  }

  public Duration maxTransferDuration() {
    return maxTransferDuration;
  }

  public Duration maxAccessWalkDuration() {
    return maxAccessWalkDuration;
  }

  public Duration maxEgressWalkDuration() {
    return maxEgressWalkDuration;
  }

  public double streetTimeFactor() {
    return streetTimeFactor;
  }

  public double maxVehicleSpeed() {
    return maxVehicleSpeed;
  }

  public boolean allowOnlyStopReachedOnBoard() {
    return allowOnlyStopReachedOnBoard;
  }

  public int minimumStreetDistanceForFlex() {
    return minimumStreetDistanceForFlex;
  }

  public boolean removeWalkingIfFlexIsFaster() {
    return removeWalkingIfFlexIsFaster;
  }
}
