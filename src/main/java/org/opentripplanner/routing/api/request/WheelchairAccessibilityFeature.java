package org.opentripplanner.routing.api.request;

/**
 * A container for how to treat trips or stops with which don't have a wheelchair accessibility of
 * POSSIBLE.
 *
 * @param onlyConsiderAccessible Whether to include trips or stops which don't have a wheelchair accessibility of POSSIBLE.
 * @param unknownCost The extra cost to add when using a trip/stop which has an accessibility of UNKNOWN.
 * @param inaccessibleCost The extra cost to add when using a trip/stop which has an accessibility of NOT_POSSIBLE.
 */
public record WheelchairAccessibilityFeature(
  boolean onlyConsiderAccessible,
  int unknownCost,
  int inaccessibleCost
) {
  private static final int NOT_SET = -1;

  private static final WheelchairAccessibilityFeature ONLY_CONSIDER_ACCESSIBLE = new WheelchairAccessibilityFeature(
    true,
    NOT_SET,
    NOT_SET
  );

  /**
   * Create a feature which only considers wheelchair-accessible trips/stops.
   */
  public static WheelchairAccessibilityFeature ofOnlyAccessible() {
    return ONLY_CONSIDER_ACCESSIBLE;
  }

  /**
   * Create a feature which considers trips/stops that don't have an accessibility of POSSIBLE.
   */
  public static WheelchairAccessibilityFeature ofCost(int unknownCost, int inaccessibleCost) {
    return new WheelchairAccessibilityFeature(false, unknownCost, inaccessibleCost);
  }
}
