package org.opentripplanner.routing.api.request.preference;

import java.util.Objects;
import java.util.function.Consumer;
import org.opentripplanner.framework.tostring.ToStringBuilder;
import org.opentripplanner.routing.algorithm.filterchain.api.TransitGeneralizedCostFilterParams;
import org.opentripplanner.routing.algorithm.filterchain.deletionflagger.TransvisionFilter;
import org.opentripplanner.routing.api.request.framework.DoubleAlgorithmFunction;
import org.opentripplanner.routing.api.request.framework.RequestFunctions;
import org.opentripplanner.routing.api.request.framework.Units;

/**
 * Group by Similarity filter parameters. See the configuration for documentation of each field.
 *
 * <p>
 * THIS CLASS IS IMMUTABLE AND THREAD-SAFE.
 */
public final class ItineraryFilterPreferences {

  public static final ItineraryFilterPreferences DEFAULT = new ItineraryFilterPreferences();

  private final boolean accessibilityScore;
  private final double bikeRentalDistanceRatio;
  private final boolean debug;
  private final boolean filterItinerariesWithSameFirstOrLastTrip;
  private final double groupedOtherThanSameLegsMaxCostMultiplier;
  private final double groupSimilarityKeepOne;
  private final double groupSimilarityKeepThree;
  private final double minBikeParkingDistance;
  private final DoubleAlgorithmFunction nonTransitGeneralizedCostLimit;
  private final double parkAndRideDurationRatio;
  private final double flexToScheduledTransitDistanceRatio;
  private final double flexToScheduledTransitDurationRatio;
  private final boolean requireScheduledTransit;
  private final TransvisionFilter.Parameters transvision;
  private final boolean removeItinerariesWithSameRoutesAndStops;
  private final TransitGeneralizedCostFilterParams transitGeneralizedCostLimit;

  private ItineraryFilterPreferences() {
    this.accessibilityScore = false;
    this.bikeRentalDistanceRatio = 0.0;
    this.debug = false;
    this.filterItinerariesWithSameFirstOrLastTrip = false;
    this.groupedOtherThanSameLegsMaxCostMultiplier = 2.0;
    this.groupSimilarityKeepOne = 0.85;
    this.groupSimilarityKeepThree = 0.68;
    this.minBikeParkingDistance = 0;
    this.nonTransitGeneralizedCostLimit = RequestFunctions.createLinearFunction(3600, 2);
    this.parkAndRideDurationRatio = 0.0;
    this.flexToScheduledTransitDistanceRatio = 0.0;
    this.flexToScheduledTransitDurationRatio = 0.0;
    this.requireScheduledTransit = false;
    this.transvision = null;
    this.removeItinerariesWithSameRoutesAndStops = false;
    this.transitGeneralizedCostLimit =
      new TransitGeneralizedCostFilterParams(RequestFunctions.createLinearFunction(900, 1.5), 0.4);
  }

  private ItineraryFilterPreferences(Builder builder) {
    this.accessibilityScore = builder.accessibilityScore;
    this.bikeRentalDistanceRatio = Units.ratio(builder.bikeRentalDistanceRatio);
    this.debug = builder.debug;
    this.filterItinerariesWithSameFirstOrLastTrip =
      builder.filterItinerariesWithSameFirstOrLastTrip;
    this.groupedOtherThanSameLegsMaxCostMultiplier =
      Units.reluctance(builder.groupedOtherThanSameLegsMaxCostMultiplier);
    this.groupSimilarityKeepOne = Units.reluctance(builder.groupSimilarityKeepOne);
    this.groupSimilarityKeepThree = Units.reluctance(builder.groupSimilarityKeepThree);
    this.minBikeParkingDistance = builder.minBikeParkingDistance;
    this.nonTransitGeneralizedCostLimit =
      Objects.requireNonNull(builder.nonTransitGeneralizedCostLimit);
    this.parkAndRideDurationRatio = Units.ratio(builder.parkAndRideDurationRatio);
    this.flexToScheduledTransitDistanceRatio =
      Units.reluctance(builder.flexToScheduledTransitDistanceRatio);
    this.flexToScheduledTransitDurationRatio =
      Units.reluctance(builder.flexToScheduledTransitDurationRatio);
    this.requireScheduledTransit = builder.requireScheduledTransit;
    this.transvision = builder.transvision;
    this.removeItinerariesWithSameRoutesAndStops = builder.removeItinerariesWithSameRoutesAndStops;
    this.transitGeneralizedCostLimit = Objects.requireNonNull(builder.transitGeneralizedCostLimit);
  }

  public static Builder of() {
    return DEFAULT.copyOf();
  }

  public Builder copyOf() {
    return new Builder(this);
  }

  public boolean useAccessibilityScore() {
    return accessibilityScore;
  }

  public double bikeRentalDistanceRatio() {
    return bikeRentalDistanceRatio;
  }

  public boolean debug() {
    return debug;
  }

  public boolean filterItinerariesWithSameFirstOrLastTrip() {
    return filterItinerariesWithSameFirstOrLastTrip;
  }

  public double groupedOtherThanSameLegsMaxCostMultiplier() {
    return groupedOtherThanSameLegsMaxCostMultiplier;
  }

  public double groupSimilarityKeepOne() {
    return groupSimilarityKeepOne;
  }

  public double groupSimilarityKeepThree() {
    return groupSimilarityKeepThree;
  }

  public double minBikeParkingDistance() {
    return minBikeParkingDistance;
  }

  public DoubleAlgorithmFunction nonTransitGeneralizedCostLimit() {
    return nonTransitGeneralizedCostLimit;
  }

  public double parkAndRideDurationRatio() {
    return parkAndRideDurationRatio;
  }

  public double flexToScheduledTransitDistanceRatio() {
    return flexToScheduledTransitDistanceRatio;
  }

  public double flexToScheduledTransitDurationRatio() {
    return flexToScheduledTransitDurationRatio;
  }

  public boolean requireScheduledTransit() {
    return requireScheduledTransit;
  }

  public TransvisionFilter.Parameters transvision() {
    return transvision;
  }

  public boolean removeItinerariesWithSameRoutesAndStops() {
    return removeItinerariesWithSameRoutesAndStops;
  }

  public TransitGeneralizedCostFilterParams transitGeneralizedCostLimit() {
    return transitGeneralizedCostLimit;
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(ItineraryFilterPreferences.class)
      .addBoolIfTrue("accessibilityScore", accessibilityScore)
      .addNum("bikeRentalDistanceRatio", bikeRentalDistanceRatio, DEFAULT.bikeRentalDistanceRatio)
      .addBoolIfTrue("debug", debug)
      .addBoolIfTrue(
        "filterItinerariesWithSameFirstOrLastTrip",
        filterItinerariesWithSameFirstOrLastTrip
      )
      .addNum(
        "groupedOtherThanSameLegsMaxCostMultiplier",
        groupedOtherThanSameLegsMaxCostMultiplier,
        DEFAULT.groupedOtherThanSameLegsMaxCostMultiplier
      )
      .addNum("groupSimilarityKeepOne", groupSimilarityKeepOne, DEFAULT.groupSimilarityKeepOne)
      .addNum(
        "groupSimilarityKeepThree",
        groupSimilarityKeepThree,
        DEFAULT.groupSimilarityKeepThree
      )
      .addNum("minBikeParkingDistance", minBikeParkingDistance, DEFAULT.minBikeParkingDistance)
      .addObj(
        "nonTransitGeneralizedCostLimit",
        nonTransitGeneralizedCostLimit,
        DEFAULT.nonTransitGeneralizedCostLimit
      )
      .addNum(
        "parkAndRideDurationRatio",
        parkAndRideDurationRatio,
        DEFAULT.parkAndRideDurationRatio
      )
      .addNum(
        "flexToScheduledTransitDurationRatio",
        flexToScheduledTransitDurationRatio,
        DEFAULT.flexToScheduledTransitDurationRatio
      )
      .addObj(
        "transitGeneralizedCostLimit",
        transitGeneralizedCostLimit,
        DEFAULT.transitGeneralizedCostLimit
      )
      .addBoolIfTrue(
        "removeItinerariesWithSameRoutesAndStops",
        removeItinerariesWithSameRoutesAndStops
      )
      .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ItineraryFilterPreferences that = (ItineraryFilterPreferences) o;
    return (
      accessibilityScore == that.accessibilityScore &&
      Double.compare(that.bikeRentalDistanceRatio, bikeRentalDistanceRatio) == 0 &&
      debug == that.debug &&
      filterItinerariesWithSameFirstOrLastTrip == that.filterItinerariesWithSameFirstOrLastTrip &&
      Double.compare(
        that.groupedOtherThanSameLegsMaxCostMultiplier,
        groupedOtherThanSameLegsMaxCostMultiplier
      ) ==
      0 &&
      Double.compare(that.groupSimilarityKeepOne, groupSimilarityKeepOne) == 0 &&
      Double.compare(that.groupSimilarityKeepThree, groupSimilarityKeepThree) == 0 &&
      Double.compare(that.minBikeParkingDistance, minBikeParkingDistance) == 0 &&
      Double.compare(that.parkAndRideDurationRatio, parkAndRideDurationRatio) == 0 &&
      Double.compare(
        that.flexToScheduledTransitDurationRatio,
        flexToScheduledTransitDurationRatio
      ) ==
      0 &&
      removeItinerariesWithSameRoutesAndStops == that.removeItinerariesWithSameRoutesAndStops &&
      Objects.equals(nonTransitGeneralizedCostLimit, that.nonTransitGeneralizedCostLimit) &&
      Objects.equals(transitGeneralizedCostLimit, that.transitGeneralizedCostLimit)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      accessibilityScore,
      bikeRentalDistanceRatio,
      debug,
      filterItinerariesWithSameFirstOrLastTrip,
      groupedOtherThanSameLegsMaxCostMultiplier,
      groupSimilarityKeepOne,
      groupSimilarityKeepThree,
      minBikeParkingDistance,
      nonTransitGeneralizedCostLimit,
      parkAndRideDurationRatio,
      flexToScheduledTransitDurationRatio,
      removeItinerariesWithSameRoutesAndStops,
      transitGeneralizedCostLimit
    );
  }

  public static class Builder {

    private final ItineraryFilterPreferences original;
    private boolean accessibilityScore;
    private double bikeRentalDistanceRatio;
    private boolean debug;
    private boolean filterItinerariesWithSameFirstOrLastTrip;
    private double groupedOtherThanSameLegsMaxCostMultiplier;
    private double groupSimilarityKeepOne;
    private double groupSimilarityKeepThree;
    public double minBikeParkingDistance;
    private DoubleAlgorithmFunction nonTransitGeneralizedCostLimit;
    private double parkAndRideDurationRatio;
    private double flexToScheduledTransitDistanceRatio;
    private double flexToScheduledTransitDurationRatio;
    private boolean requireScheduledTransit;
    private TransvisionFilter.Parameters transvision;
    private boolean removeItinerariesWithSameRoutesAndStops;
    private TransitGeneralizedCostFilterParams transitGeneralizedCostLimit;

    public ItineraryFilterPreferences original() {
      return original;
    }

    public Builder withAccessibilityScore(boolean accessibilityScore) {
      this.accessibilityScore = accessibilityScore;
      return this;
    }

    public Builder withBikeRentalDistanceRatio(double bikeRentalDistanceRatio) {
      this.bikeRentalDistanceRatio = bikeRentalDistanceRatio;
      return this;
    }

    public Builder withDebug(boolean debug) {
      this.debug = debug;
      return this;
    }

    public Builder withFilterItinerariesWithSameFirstOrLastTrip(
      boolean filterItinerariesWithSameFirstOrLastTrip
    ) {
      this.filterItinerariesWithSameFirstOrLastTrip = filterItinerariesWithSameFirstOrLastTrip;
      return this;
    }

    public Builder withGroupedOtherThanSameLegsMaxCostMultiplier(
      double groupedOtherThanSameLegsMaxCostMultiplier
    ) {
      this.groupedOtherThanSameLegsMaxCostMultiplier = groupedOtherThanSameLegsMaxCostMultiplier;
      return this;
    }

    public Builder withGroupSimilarityKeepOne(double groupSimilarityKeepOne) {
      this.groupSimilarityKeepOne = groupSimilarityKeepOne;
      return this;
    }

    public Builder withGroupSimilarityKeepThree(double groupSimilarityKeepThree) {
      this.groupSimilarityKeepThree = groupSimilarityKeepThree;
      return this;
    }

    public Builder withMinBikeParkingDistance(double distance) {
      this.minBikeParkingDistance = distance;
      return this;
    }

    public Builder withNonTransitGeneralizedCostLimit(
      DoubleAlgorithmFunction nonTransitGeneralizedCostLimit
    ) {
      this.nonTransitGeneralizedCostLimit = nonTransitGeneralizedCostLimit;
      return this;
    }

    public Builder withParkAndRideDurationRatio(double parkAndRideDurationRatio) {
      this.parkAndRideDurationRatio = parkAndRideDurationRatio;
      return this;
    }

    public Builder withFlexToScheduledTransitDistanceRatio(
      double flexToScheduledTransitDistanceRatio
    ) {
      this.flexToScheduledTransitDistanceRatio = flexToScheduledTransitDistanceRatio;
      return this;
    }

    public Builder withFlexToScheduledTransitDurationRatio(
      double flexToScheduledTransitDurationRatio
    ) {
      this.flexToScheduledTransitDurationRatio = flexToScheduledTransitDurationRatio;
      return this;
    }

    public Builder withRequireScheduledTransit(boolean requireScheduledTransit) {
      this.requireScheduledTransit = requireScheduledTransit;
      return this;
    }

    public Builder withTransvision(TransvisionFilter.Parameters transvision) {
      this.transvision = transvision;
      return this;
    }

    public Builder withRemoveItinerariesWithSameRoutesAndStops(
      boolean removeItinerariesWithSameRoutesAndStops
    ) {
      this.removeItinerariesWithSameRoutesAndStops = removeItinerariesWithSameRoutesAndStops;
      return this;
    }

    public Builder withTransitGeneralizedCostLimit(
      TransitGeneralizedCostFilterParams transitGeneralizedCostLimit
    ) {
      this.transitGeneralizedCostLimit = transitGeneralizedCostLimit;
      return this;
    }

    public Builder(ItineraryFilterPreferences original) {
      this.original = original;
      this.accessibilityScore = original.accessibilityScore;
      this.bikeRentalDistanceRatio = original.bikeRentalDistanceRatio;
      this.debug = original.debug;
      this.filterItinerariesWithSameFirstOrLastTrip =
        original.filterItinerariesWithSameFirstOrLastTrip;
      this.groupedOtherThanSameLegsMaxCostMultiplier =
        original.groupedOtherThanSameLegsMaxCostMultiplier;
      this.groupSimilarityKeepOne = original.groupSimilarityKeepOne;
      this.groupSimilarityKeepThree = original.groupSimilarityKeepThree;
      this.minBikeParkingDistance = original.minBikeParkingDistance;
      this.nonTransitGeneralizedCostLimit = original.nonTransitGeneralizedCostLimit;
      this.parkAndRideDurationRatio = original.parkAndRideDurationRatio;
      this.flexToScheduledTransitDistanceRatio = original.flexToScheduledTransitDistanceRatio;
      this.flexToScheduledTransitDurationRatio = original.flexToScheduledTransitDurationRatio;
      this.requireScheduledTransit = original.requireScheduledTransit;
      this.transvision = original.transvision;
      this.removeItinerariesWithSameRoutesAndStops =
        original.removeItinerariesWithSameRoutesAndStops;
      this.transitGeneralizedCostLimit = original.transitGeneralizedCostLimit;
    }

    public Builder apply(Consumer<Builder> body) {
      body.accept(this);
      return this;
    }

    public ItineraryFilterPreferences build() {
      var value = new ItineraryFilterPreferences(this);
      return original.equals(value) ? original : value;
    }
  }
}
