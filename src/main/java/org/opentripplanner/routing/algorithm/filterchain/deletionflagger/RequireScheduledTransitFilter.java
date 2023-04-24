package org.opentripplanner.routing.algorithm.filterchain.deletionflagger;

import java.util.List;
import org.opentripplanner.model.plan.Itinerary;

/**
 * Filter itineraries so that at most three itineraries a returned (least taxi, least transfers,
 * fastest).
 */
public class RequireScheduledTransitFilter implements ItineraryDeletionFlagger {

  /**
   * Required for {@link org.opentripplanner.routing.algorithm.filterchain.ItineraryListFilterChain},
   * to know which filters removed
   */
  public static final String TAG = "scheduled-transit";

  @Override
  public String name() {
    return TAG;
  }

  @Override
  public List<Itinerary> flagForRemoval(List<Itinerary> itineraries) {
    return itineraries.stream().filter(it -> !it.hasScheduledTransit()).toList();
  }
}
