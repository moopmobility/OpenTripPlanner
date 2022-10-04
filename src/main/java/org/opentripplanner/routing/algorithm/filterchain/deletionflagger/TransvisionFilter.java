package org.opentripplanner.routing.algorithm.filterchain.deletionflagger;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Leg;

/**
 * Filter itineraries so that at most three itineraries a returned (least taxi, least transfers,
 * fastest).
 */
public class TransvisionFilter implements ItineraryDeletionFlagger {

  /**
   * Required for {@link org.opentripplanner.routing.algorithm.filterchain.ItineraryListFilterChain},
   * to know which filters removed
   */
  public static final String TAG = "transvision";

  private final Parameters parameters;

  public TransvisionFilter(Parameters parameters) {
    this.parameters = parameters;
  }

  @Override
  public String name() {
    return TAG;
  }

  @Override
  public List<Itinerary> flagForRemoval(List<Itinerary> itineraries) {
    var classifications = itineraries
      .stream()
      .filter(Itinerary::hasTransit)
      .map(Classification::classify)
      .toList();

    if (classifications.isEmpty()) {
      return itineraries;
    }

    var minTaxi = findItineraryWithMinimumTaxi(classifications);
    var minTransfers = findItineraryWithMinimumTransfers(minTaxi, classifications);
    var faster = findFasterItinerary(minTaxi, minTransfers, classifications);

    return itineraries
      .stream()
      .filter(itinerary -> {
        if (minTaxi.itinerary == itinerary) {
          return false;
        }

        if (minTransfers.isPresent() && minTransfers.get().itinerary == itinerary) {
          return false;
        }

        //noinspection RedundantIfStatement
        if (faster.isPresent() && faster.get().itinerary == itinerary) {
          return false;
        }

        return true;
      })
      .toList();
  }

  Classification findItineraryWithMinimumTaxi(List<Classification> classifications) {
    //noinspection OptionalGetWithoutIsPresent
    var minTaxi = classifications
      .stream()
      .mapToDouble(Classification::taxiDistance)
      .min()
      .getAsDouble();

    var withMinTaxi = classifications.stream().filter(c -> c.taxiDistance() == minTaxi).toList();

    //noinspection OptionalGetWithoutIsPresent
    var minDuration = withMinTaxi
      .stream()
      .mapToLong(c -> c.duration().toSeconds())
      .min()
      .getAsLong();

    var minTransfers = withMinTaxi.stream().mapToLong(Classification::transfers).min().getAsLong();

    //noinspection OptionalGetWithoutIsPresent
    return withMinTaxi
      .stream()
      .map(classified -> {
        // A simple scoring primarily on duration with a small penalty for transfers
        var score =
          Math.floor(
            (classified.itinerary.getDuration().toSeconds() - minDuration) /
            (double) parameters.minimumTaxiSecondGroups()
          ) +
          (classified.transfers() - minTransfers) *
          parameters.minimumTaxiTransferScore();
        return new Scored(classified, score);
      })
      .min(Comparator.comparing(Scored::score))
      .map(Scored::classification)
      .get();
  }

  Optional<Classification> findItineraryWithMinimumTransfers(
    Classification minTaxi,
    List<Classification> classifications
  ) {
    //noinspection OptionalGetWithoutIsPresent
    var minTransfers = classifications
      .stream()
      .mapToDouble(Classification::transfers)
      .min()
      .getAsDouble();

    if (minTransfers == minTaxi.transfers()) {
      // The min-transfer itinerary can only be better by duration, which would be the faster itinerary
      return Optional.empty();
    }

    var withMinTransfers = classifications
      .stream()
      .filter(classified -> classified.transfers() == minTransfers)
      .toList();

    //noinspection OptionalGetWithoutIsPresent
    var minDuration = withMinTransfers
      .stream()
      .mapToLong(c -> c.duration().toSeconds())
      .min()
      .getAsLong();

    return withMinTransfers
      .stream()
      .map(classified -> {
        // A simple scoring primarily on duration with a small penalty for taxi distance over the minimum
        var durationGroups = Math.floor(
          (classified.itinerary.getDuration().toSeconds() - minDuration) /
          (double) parameters.minimumTransfersSecondGroups()
        );
        var taxiGroups = Math.floor(
          (classified.taxiDistance() - minTaxi.taxiDistance()) /
          (double) parameters.minimumTransfersTaxiGroups()
        );
        return new Scored(classified, durationGroups + taxiGroups);
      })
      .min(Comparator.comparing(Scored::score))
      .map(Scored::classification);
  }

  Optional<Classification> findFasterItinerary(
    Classification minTaxi,
    Optional<Classification> minTransfers,
    List<Classification> classifications
  ) {
    var tempBestDuration = minTransfers.map(Classification::duration).orElse(minTaxi.duration());
    if (tempBestDuration.compareTo(minTaxi.duration()) > 0) {
      tempBestDuration = minTaxi.duration();
    }

    var bestDuration = tempBestDuration;

    var bestTransfers = minTransfers.map(Classification::transfers).orElse(minTaxi.transfers());

    var fasterItineraries = classifications
      .stream()
      .filter(classification ->
        bestDuration.toSeconds() -
        classification.duration().toSeconds() >=
        parameters.minimumSecondsForFasterItinerary()
      )
      .toList();

    return fasterItineraries
      .stream()
      .map(classified -> {
        // The chosen itinerary should be faster _without_ using significantly more taxi, the ratio
        // of "extra" taxi kilometers relative to the time saved should be small.

        var extraDistance = classified.taxiDistance() - minTaxi.taxiDistance();
        var extraDuration = bestDuration.toSeconds() - classified.duration().toSeconds();
        var extraTransfers =
          (classified.transfers() - bestTransfers) * parameters.fasterTransfersScore();
        var score = extraDistance / extraDuration + extraTransfers;

        return new Scored(classified, score);
      })
      .filter(scored -> scored.score() < parameters.maximumScoreFasterItinerary())
      .min(Comparator.comparing(Scored::score))
      .map(Scored::classification);
  }

  record Classification(
    Itinerary itinerary,
    double taxiDistance,
    Duration duration,
    long transfers
  ) {
    static Classification classify(Itinerary itinerary) {
      var taxiDistance = itinerary
        .getLegs()
        .stream()
        .filter(Leg::isFlexibleTrip)
        .mapToDouble(Leg::getDistanceMeters)
        .sum();

      var transfers = itinerary.getLegs().stream().filter(Leg::isScheduledTransitLeg).count();

      return new Classification(itinerary, taxiDistance, itinerary.getDuration(), transfers);
    }
  }

  private record Scored(Classification classification, double score) {}

  public record Parameters(
    int minimumTaxiSecondGroups,
    double minimumTaxiTransferScore,
    int minimumTransfersSecondGroups,
    int minimumTransfersTaxiGroups,
    double fasterTransfersScore,
    int minimumSecondsForFasterItinerary,
    double maximumScoreFasterItinerary
  ) {}
}
