package org.opentripplanner.ext.flex;

import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.raptor.api.transit.RaptorTransfer;

public class FlexAccessEgress {

  public final RegularStop stop;
  public final int preFlexTime;
  public final int flexTime;
  public final int postFlexTime;
  private final int fromStopIndex;
  private final int toStopIndex;
  private final int differenceFromStartOfTime;
  private final FlexTrip trip;
  public final State lastState;
  public final boolean directToStop;

  public FlexAccessEgress(
    RegularStop stop,
    int preFlexTime,
    int flexTime,
    int postFlexTime,
    int fromStopIndex,
    int toStopIndex,
    int differenceFromStartOfTime,
    FlexTrip trip,
    State lastState,
    boolean directToStop
  ) {
    this.stop = stop;
    this.preFlexTime = preFlexTime;
    this.flexTime = flexTime;
    this.postFlexTime = postFlexTime;
    this.fromStopIndex = fromStopIndex;
    this.toStopIndex = toStopIndex;
    this.differenceFromStartOfTime = differenceFromStartOfTime;
    this.trip = trip;
    this.lastState = lastState;
    this.directToStop = directToStop;
  }

  public int earliestDepartureTime(int departureTime) {
    int requestedTransitDepartureTime = departureTime + preFlexTime - differenceFromStartOfTime;
    int earliestAvailableTransitDepartureTime = trip.earliestDepartureTime(
      requestedTransitDepartureTime,
      fromStopIndex,
      toStopIndex,
      flexTime
    );
    if (earliestAvailableTransitDepartureTime == RaptorTransfer.UNAVAILABLE) {
      return RaptorTransfer.UNAVAILABLE;
    }
    return earliestAvailableTransitDepartureTime - preFlexTime + differenceFromStartOfTime;
  }

  public int latestArrivalTime(int arrivalTime) {
    int requestedTransitArrivalTime = arrivalTime - postFlexTime - differenceFromStartOfTime;
    int latestAvailableTransitArrivalTime = trip.latestArrivalTime(
      requestedTransitArrivalTime,
      fromStopIndex,
      toStopIndex,
      flexTime
    );
    if (latestAvailableTransitArrivalTime == RaptorTransfer.UNAVAILABLE) {
      return RaptorTransfer.UNAVAILABLE;
    }
    return latestAvailableTransitArrivalTime + postFlexTime + differenceFromStartOfTime;
  }
}
