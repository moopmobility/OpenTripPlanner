package org.opentripplanner.routing.algorithm.filterchain.deletionflagger;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opentripplanner.model.plan.Itinerary;

/**
 * Filter itineraries based on durationSeconds and generalizedCost, compared with an
 * on-street-all-the-way itinerary (if it exists). If an itinerary is both slower and has a higher
 * cost than the best all-the-way-on-street itinerary, then the transit itinerary is removed.
 */
public class RemoveTransitIfStreetOnlyIsBetterFilter implements ItineraryDeletionFlagger {

  /**
   * Required for {@link org.opentripplanner.routing.algorithm.filterchain.ItineraryListFilterChain},
   * to know which filters removed
   */
  public static final String TAG = "transit-vs-street-filter";

  @Override
  public String name() {
    return TAG;
  }

  @Override
  public List<Itinerary> flagForRemoval(List<Itinerary> itineraries) {
    // Find the best walk-all-the-way option
    Optional<Itinerary> bestStreetOp = itineraries
      .stream()
      .filter(Itinerary::isOnStreetAllTheWay)
      .min(Comparator.comparingInt(Itinerary::getGeneralizedCost));

    if (bestStreetOp.isEmpty()) {
      return List.of();
    }

    final long costLimit = bestStreetOp.get().getGeneralizedCost();
    final Duration timeLimit = bestStreetOp.get().getDuration();

    // Filter away itineraries that have higher cost and are slower than the best non-transit option.
    return itineraries
      .stream()
      .filter(it ->
        !(
          it.isOnStreetAllTheWay() ||
          it.getGeneralizedCost() < costLimit ||
          it.getDuration().compareTo(timeLimit) < 0
        )
      )
      .collect(Collectors.toList());
  }

  @Override
  public boolean skipAlreadyFlaggedItineraries() {
    return false;
  }
}
