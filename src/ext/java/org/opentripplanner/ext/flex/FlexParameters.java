package org.opentripplanner.ext.flex;

import java.time.Duration;

public record FlexParameters(
  double maxVehicleSpeed,
  double streetTimeFactor,
  double maxTransferMeters,
  Duration maxTripDuration
) {
  public double getMaxVehicleSpeed() {
    return maxVehicleSpeed;
  }

  public double getStreetTimeFactor() {
    return streetTimeFactor;
  }

  public double getMaxTransferMeters() {
    return maxTransferMeters;
  }

  public Duration getMaxTripDuration() {
    return maxTripDuration;
  }
}
