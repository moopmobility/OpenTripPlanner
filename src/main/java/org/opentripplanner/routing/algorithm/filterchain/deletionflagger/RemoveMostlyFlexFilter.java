package org.opentripplanner.routing.algorithm.filterchain.deletionflagger;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
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
public class RemoveMostlyFlexFilter<V> implements ItineraryDeletionFlagger {

  private final double flexToScheduledTransitRatio;
  private final ToDoubleFunction<Leg> valueExtractor;
  private final String name;

  public RemoveMostlyFlexFilter(double ratio, ToDoubleFunction<Leg> valueExtractor, String name) {
    this.flexToScheduledTransitRatio = ratio;
    this.valueExtractor = valueExtractor;
    this.name = name;
  }

  @Override
  public String name() {
    return this.name;
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

      double flexValue = itinerary
        .getLegs()
        .stream()
        .filter(Leg::isFlexibleTrip)
        .mapToDouble(this.valueExtractor)
        .sum();

      double scheduledValue = itinerary
        .getLegs()
        .stream()
        .filter(Leg::isScheduledTransitLeg)
        .mapToDouble(this.valueExtractor)
        .sum();

      return (
        containsFlexTransit &&
        containsScheduledTransit &&
        scheduledValue != 0 &&
        (flexValue / scheduledValue) > flexToScheduledTransitRatio
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

  public static RemoveMostlyFlexFilter<Double> ofDistance(double ratio) {
    return new RemoveMostlyFlexFilter<>(
      ratio,
      Leg::getDistanceMeters,
      "flex-vs-scheduled-transit-distance-filter"
    );
  }

  public static RemoveMostlyFlexFilter<Duration> ofDuration(double ratio) {
    return new RemoveMostlyFlexFilter<>(
      ratio,
      leg -> leg.getDuration().toSeconds(),
      "flex-vs-scheduled-transit-duration-filter"
    );
  }
}
