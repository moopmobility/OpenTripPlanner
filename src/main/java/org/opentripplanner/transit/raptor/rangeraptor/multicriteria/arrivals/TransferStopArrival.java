package org.opentripplanner.transit.raptor.rangeraptor.multicriteria.arrivals;

import org.opentripplanner.transit.raptor.api.transit.RaptorTransfer;
import org.opentripplanner.transit.raptor.api.transit.RaptorTripSchedule;
import org.opentripplanner.transit.raptor.api.transit.TransitArrival;
import org.opentripplanner.transit.raptor.api.view.TransferPathView;

/**
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public final class TransferStopArrival<T extends RaptorTripSchedule>
  extends AbstractStopArrival<T> {

  private final RaptorTransfer transfer;

  public TransferStopArrival(
    AbstractStopArrival<T> previousState,
    RaptorTransfer transferPath,
    int arrivalTime
  ) {
    super(
      previousState,
      1,
      transferPath.stop(),
      arrivalTime,
      previousState.cost() + transferPath.generalizedCost()
    );
    this.transfer = transferPath;
  }

  @Override
  public boolean arrivalMayBeTimeShifted() {
    return previous().arrivalMayBeTimeShifted();
  }

  @Override
  public AbstractStopArrival<T> timeShiftNewArrivalTime(int newRequestedArrivalTime) {
    int newPreviousArrivalTime = newRequestedArrivalTime - transfer.durationInSeconds();
    var previousTimeShifted = previous().timeShiftNewArrivalTime(newPreviousArrivalTime);
    if (previousTimeShifted == previous()) {
      return this;
    }

    int newDepartureTime = transfer.earliestDepartureTime(previousTimeShifted.arrivalTime());
    if (newDepartureTime == RaptorTransfer.UNAVAILABLE) {
      return this;
    }

    return new TransferStopArrival<>(
      previousTimeShifted,
      transfer,
      newDepartureTime + transfer.durationInSeconds()
    );
  }

  @Override
  public TransitArrival<T> mostRecentTransitArrival() {
    return previous().mostRecentTransitArrival();
  }

  @Override
  public boolean arrivedByTransfer() {
    return true;
  }

  @Override
  public TransferPathView transferPath() {
    return () -> transfer;
  }
}
