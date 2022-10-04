package org.opentripplanner.routing.algorithm.filterchain.deletionflagger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.model.plan.PlanTestConstants.A;
import static org.opentripplanner.model.plan.TestItineraryBuilder.newItinerary;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TransvisionFilterTest {

  @Test
  public void streetOnlyIsRemoved() {
    var subject = new TransvisionFilter(new TransvisionFilter.Parameters(0, 0, 0, 0, 0, 0, 0));

    var streetOnly = newItinerary(A).drive(0, 1, A).build();

    assertEquals(List.of(streetOnly), subject.flagForRemoval(List.of(streetOnly)));
  }

  @Test
  public void itinerariesAreRemoved() {
    var subject = new TransvisionFilter(new TransvisionFilter.Parameters(60, 1, 60, 1, 2, 60, 10));

    var streetOnly = newItinerary(A).drive(0, 1, A).build();

    var nonMinTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 10000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 10000, A)
      .build();

    var minTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 5000, A)
      .build();

    var minTransfers = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 21 * 60, A)
      .flexBus(1, 26 * 60, 31 * 60, 10000, A)
      .build();

    var faster = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 6000, A)
      .bus(1, 10 * 60, 12 * 60, A)
      .bus(1, 12 * 60, 15 * 60, A)
      .flexBus(1, 15 * 60, 20 * 60, 6000, A)
      .build();

    assertEquals(
      List.of(streetOnly, nonMinTaxi),
      subject.flagForRemoval(List.of(streetOnly, nonMinTaxi, minTaxi, minTransfers))
    );
  }

  @Test
  public void testMinTax() {
    var subjectWith0SecondGroup = new TransvisionFilter(
      new TransvisionFilter.Parameters(1, 20, 0, 0, 0, 0, 0)
    );
    var subjectWith90SecondGroup = new TransvisionFilter(
      new TransvisionFilter.Parameters(90, 20, 0, 0, 0, 0, 0)
    );
    var subjectWith600SecondGroup = new TransvisionFilter(
      new TransvisionFilter.Parameters(600, 20, 0, 0, 0, 0, 0)
    );
    var subjectWithNoTransferPenalty = new TransvisionFilter(
      new TransvisionFilter.Parameters(90, 0, 0, 0, 0, 0, 0)
    );

    var nonMinTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 10000, A)
      .bus(1, 10 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 10000, A)
      .build();

    var minTaxiHighScore5min = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 25 * 60, A)
      .flexBus(1, 30 * 60, 35 * 60, 5000, A)
      .build();

    var minTaxiHighScore2Transfer = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 5000, A)
      .build();

    var minTaxiLowScore1Min = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 26 * 60, A)
      .flexBus(1, 31 * 60, 31 * 60, 5000, A)
      .build();

    var minTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 5000, A)
      .build();

    var itineraries = Stream
      .of(nonMinTaxi, minTaxiHighScore5min, minTaxiHighScore2Transfer, minTaxiLowScore1Min, minTaxi)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    assertEquals(
      minTaxi,
      subjectWith0SecondGroup.findItineraryWithMinimumTaxi(itineraries).itinerary()
    );
    assertEquals(
      minTaxiLowScore1Min,
      subjectWith90SecondGroup.findItineraryWithMinimumTaxi(itineraries).itinerary()
    );
    assertEquals(
      minTaxiHighScore5min,
      subjectWith600SecondGroup.findItineraryWithMinimumTaxi(itineraries).itinerary()
    );
    assertEquals(
      minTaxiHighScore2Transfer,
      subjectWithNoTransferPenalty.findItineraryWithMinimumTaxi(itineraries).itinerary()
    );
  }

  @Test
  void testMinTransfers() {
    var subjectWith90SecondGroup = new TransvisionFilter(
      new TransvisionFilter.Parameters(0, 0, 90, 1, 0, 0, 0)
    );
    var subjectWith600SecondGroup = new TransvisionFilter(
      new TransvisionFilter.Parameters(0, 0, 600, 1, 0, 0, 0)
    );
    var subjectWithNoTaxiPenalty = new TransvisionFilter(
      new TransvisionFilter.Parameters(0, 0, 600, 0, 0, 0, 0)
    );

    var minTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 5000, A)
      .build();

    var fasterWithMoreTaxi = newItinerary(A, 0)
      .walk(60, A)
      .bus(1, 5 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 16 * 60, A)
      .flexBus(1, 21 * 60, 25 * 60, 15000, A)
      .build();

    var slowerMoreTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 11000, A)
      .build();

    var slower1Min = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 21 * 60, A)
      .flexBus(1, 26 * 60, 31 * 60, 10000, A)
      .build();

    var slower5Min = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 25 * 60, A)
      .flexBus(1, 30 * 60, 35 * 60, 10000, A)
      .build();

    var minTaxiClassified = TransvisionFilter.Classification.classify(minTaxi);

    var fasterItineraries = Stream
      .of(minTaxi, fasterWithMoreTaxi)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    var itineraries = Stream
      .of(minTaxi, fasterWithMoreTaxi, slowerMoreTaxi, slower5Min, slower1Min)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    assertTrue(
      subjectWith90SecondGroup
        .findItineraryWithMinimumTransfers(minTaxiClassified, fasterItineraries)
        .isEmpty()
    );

    assertEquals(
      slower1Min,
      subjectWith90SecondGroup
        .findItineraryWithMinimumTransfers(minTaxiClassified, itineraries)
        .get()
        .itinerary()
    );

    assertEquals(
      slower5Min,
      subjectWith600SecondGroup
        .findItineraryWithMinimumTransfers(minTaxiClassified, itineraries)
        .get()
        .itinerary()
    );

    assertEquals(
      slowerMoreTaxi,
      subjectWithNoTaxiPenalty
        .findItineraryWithMinimumTransfers(minTaxiClassified, itineraries)
        .get()
        .itinerary()
    );
  }

  @Test
  void testFasterItineraryWithMinTaxiDuration() {
    var subject = new TransvisionFilter(new TransvisionFilter.Parameters(0, 0, 0, 0, 1, 60, 10));

    var subjectWith600Seconds = new TransvisionFilter(
      new TransvisionFilter.Parameters(0, 0, 0, 0, 1, 600, 10)
    );

    var minTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 5000, A)
      .build();

    var minTransfers = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 50 * 60, A)
      .flexBus(1, 30 * 60, 35 * 60, 5000, A)
      .build();

    var slowerItinerary = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 25 * 60, A)
      .flexBus(1, 30 * 60, 35 * 60, 10000, A)
      .build();

    var fasterItinerary = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .flexBus(1, 20 * 60, 25 * 60, 6000, A)
      .build();

    var minTaxiClassified = TransvisionFilter.Classification.classify(minTaxi);
    var minTransfersClassified = Optional.of(
      TransvisionFilter.Classification.classify(minTransfers)
    );

    // minTaxi + minTransfer -> duration minTaxi
    var itinerariesWithoutFaster = Stream
      .of(minTaxi, minTransfers, slowerItinerary)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    var itineraries = Stream
      .of(minTaxi, minTransfers, slowerItinerary, fasterItinerary)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    assertTrue(
      subject
        .findFasterItinerary(minTaxiClassified, minTransfersClassified, itinerariesWithoutFaster)
        .isEmpty()
    );

    assertTrue(
      subjectWith600Seconds
        .findFasterItinerary(minTaxiClassified, minTransfersClassified, itineraries)
        .isEmpty()
    );

    assertEquals(
      fasterItinerary,
      subject
        .findFasterItinerary(minTaxiClassified, minTransfersClassified, itineraries)
        .get()
        .itinerary()
    );
  }

  @Test
  void testFasterItineraryWithMinTaxiTransfer() {
    var subject = new TransvisionFilter(new TransvisionFilter.Parameters(0, 0, 0, 0, 1, 60, 10));

    var minTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 20 * 60, A)
      .flexBus(1, 30 * 60, 35 * 60, 5000, A)
      .build();

    var minTransfers = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 5000, A)
      .build();

    var slowerItinerary = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 25 * 60, A)
      .flexBus(1, 30 * 60, 35 * 60, 10000, A)
      .build();

    var fasterItinerary = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .flexBus(1, 20 * 60, 25 * 60, 6000, A)
      .build();

    var minTaxiClassified = TransvisionFilter.Classification.classify(minTaxi);
    var minTransfersClassified = Optional.of(
      TransvisionFilter.Classification.classify(minTransfers)
    );

    // minTaxi + minTransfer -> duration minTaxi
    var itinerariesWithoutFaster = Stream
      .of(minTaxi, minTransfers, slowerItinerary)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    var itineraries = Stream
      .of(minTaxi, minTransfers, slowerItinerary, fasterItinerary)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    assertTrue(
      subject
        .findFasterItinerary(minTaxiClassified, minTransfersClassified, itinerariesWithoutFaster)
        .isEmpty()
    );

    assertEquals(
      fasterItinerary,
      subject
        .findFasterItinerary(minTaxiClassified, minTransfersClassified, itineraries)
        .get()
        .itinerary()
    );
  }

  @Test
  void testFasterItinerary() {
    var minTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .bus(1, 15 * 60, 20 * 60, A)
      .flexBus(1, 25 * 60, 30 * 60, 5000, A)
      .build();

    var minTransfers = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 25 * 60, A)
      .flexBus(1, 30 * 60, 35 * 60, 6000, A)
      .build();

    var fasterWithTaxi = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 5000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .flexBus(1, 15 * 60, 20 * 60, 10000, A)
      .build();

    var fasterWithTransit = newItinerary(A)
      .flexBus(1, 0, 5 * 60, 6000, A)
      .bus(1, 10 * 60, 15 * 60, A)
      .flexBus(1, 20 * 60, 25 * 60, 6000, A)
      .build();

    var minTaxiClassified = TransvisionFilter.Classification.classify(minTaxi);
    var minTransfersClassified = Optional.of(
      TransvisionFilter.Classification.classify(minTransfers)
    );

    var itineraries = Stream
      .of(minTaxi, minTransfers, fasterWithTaxi, fasterWithTransit)
      .map(TransvisionFilter.Classification::classify)
      .toList();

    var subject = new TransvisionFilter(new TransvisionFilter.Parameters(0, 0, 0, 0, 1, 60, 10));

    var subjectWithLowScore = new TransvisionFilter(
      new TransvisionFilter.Parameters(0, 0, 0, 0, 1, 60, 1)
    );

    assertTrue(
      subjectWithLowScore
        .findFasterItinerary(minTaxiClassified, minTransfersClassified, itineraries)
        .isEmpty()
    );

    assertEquals(
      fasterWithTransit,
      subject
        .findFasterItinerary(minTaxiClassified, minTransfersClassified, itineraries)
        .get()
        .itinerary()
    );
  }
}
