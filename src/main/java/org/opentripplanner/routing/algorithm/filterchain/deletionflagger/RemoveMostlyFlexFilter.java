package org.opentripplanner.routing.algorithm.filterchain.deletionflagger;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;

/**
 * This is used to filter out flex itineraries that contain long flex trips with only little
 * scheduled transit. The value describes the minimum ratio of the duration of flex legs to
 * scheduled transit to allow the itinerary.
 * <p>
 * This filter is turned off by default (flexToScheduledTransitDurationRatio == 0)
 */
public class RemoveMostlyFlexFilter implements ItineraryDeletionFlagger {

  private final double flexToScheduledTransitDurationRatio;

  public RemoveMostlyFlexFilter(double ratio) {
    this.flexToScheduledTransitDurationRatio = ratio;
  }

  @Override
  public String name() {
    return "flex-vs-scheduled-transit-filter";
  }

  @Override
  public Predicate<Itinerary> shouldBeFlaggedForRemoval() {
    return itinerary -> {
      var containsFlexTransit = itinerary
        .getLegs()
        .stream()
        .anyMatch(l -> l != null && l.isFlexibleTrip());

      var containsScheduledTransit = itinerary
        .getLegs()
        .stream()
        .anyMatch(l -> l != null && l.isScheduledTransitLeg());

      double flexDuration = itinerary
        .getLegs()
        .stream()
        .filter(Leg::isFlexibleTrip)
        .mapToDouble(l -> l.getDuration().toSeconds())
        .sum();

      double scheduledDuration = itinerary
        .getLegs()
        .stream()
        .filter(Leg::isScheduledTransitLeg)
        .mapToDouble(l -> l.getDuration().toSeconds())
        .sum();

      return (
        containsFlexTransit &&
        containsScheduledTransit &&
        scheduledDuration != 0 &&
        (flexDuration / scheduledDuration) > flexToScheduledTransitDurationRatio
      );
    };
  }

  @Override
  public List<Itinerary> flagForRemoval(List<Itinerary> itineraries) {
    if (itineraries.size() == 1) {
      return List.of();
    }

    return itineraries.stream().filter(shouldBeFlaggedForRemoval()).collect(Collectors.toList());
  }
}
