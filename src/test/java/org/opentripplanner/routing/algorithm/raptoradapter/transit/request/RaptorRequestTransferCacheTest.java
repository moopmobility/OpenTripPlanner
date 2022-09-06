package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.routing.api.request.WheelchairAccessibilityFeature;
import org.opentripplanner.routing.api.request.WheelchairAccessibilityRequest;

class RaptorRequestTransferCacheTest {

  @Test
  void testKeyEquality() {
    var firstRoutingRequest = new RoutingRequest();
    firstRoutingRequest.wheelchairAccessibility =
      new WheelchairAccessibilityRequest(
        true,
        WheelchairAccessibilityFeature.ofOnlyAccessible(),
        WheelchairAccessibilityFeature.ofCost(0, 0),
        WheelchairAccessibilityFeature.ofCost(0, 0),
        1.0,
        1.0,
        1.0,
        1.0
      );

    var secondRoutingRequest = new RoutingRequest();
    secondRoutingRequest.wheelchairAccessibility =
      new WheelchairAccessibilityRequest(
        true,
        WheelchairAccessibilityFeature.ofOnlyAccessible(),
        WheelchairAccessibilityFeature.ofCost(0, 0),
        WheelchairAccessibilityFeature.ofCost(0, 0),
        1.0,
        1.0,
        1.0,
        1.0
      );

    var first = new RaptorRequestTransferCache.StreetRelevantOptions(firstRoutingRequest);
    var second = new RaptorRequestTransferCache.StreetRelevantOptions(secondRoutingRequest);

    assertEquals(first, second);
  }
}
