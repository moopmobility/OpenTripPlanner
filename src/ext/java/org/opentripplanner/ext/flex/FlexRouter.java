package org.opentripplanner.ext.flex;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opentripplanner.ext.flex.flexpathcalculator.DirectFlexPathCalculator;
import org.opentripplanner.ext.flex.flexpathcalculator.FlexPathCalculator;
import org.opentripplanner.ext.flex.flexpathcalculator.StreetFlexPathCalculator;
import org.opentripplanner.ext.flex.flexpathcalculator.StreetWithDirectFallbackPathCalculator;
import org.opentripplanner.ext.flex.template.FlexAccessTemplate;
import org.opentripplanner.ext.flex.template.FlexEgressTemplate;
import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.framework.time.ServiceDateUtils;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.mapping.GraphPathToItineraryMapper;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.standalone.config.sandbox.FlexConfig;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;

public class FlexRouter {

  /* Transit data */

  private final Graph graph;
  private final TransitService transitService;
  private final FlexConfig config;
  private final Collection<NearbyStop> streetAccesses;
  private final Collection<NearbyStop> streetEgresses;
  private final FlexIndex flexIndex;
  private final FlexPathCalculator accessFlexPathCalculator;
  private final FlexPathCalculator egressFlexPathCalculator;
  private final GraphPathToItineraryMapper graphPathToItineraryMapper;

  /* Request data */
  private final ZonedDateTime startOfTime;
  private final int departureTime;
  private final boolean arriveBy;

  private final FlexServiceDate[] dates;

  /* State */
  private List<FlexAccessTemplate> flexAccessTemplates = null;
  private List<FlexEgressTemplate> flexEgressTemplates = null;

  public FlexRouter(
    Graph graph,
    TransitService transitService,
    FlexConfig config,
    Instant searchInstant,
    boolean arriveBy,
    int additionalPastSearchDays,
    int additionalFutureSearchDays,
    Collection<NearbyStop> streetAccesses,
    Collection<NearbyStop> egressTransfers
  ) {
    this.graph = graph;
    this.transitService = transitService;
    this.config = config;
    this.streetAccesses = streetAccesses;
    this.streetEgresses = egressTransfers;
    this.flexIndex = transitService.getFlexIndex();
    this.graphPathToItineraryMapper =
      new GraphPathToItineraryMapper(
        transitService.getTimeZone(),
        graph.streetNotesService,
        graph.ellipsoidToGeoidDifference
      );

    switch (config.calculatorType()) {
      case STREET -> {
        this.accessFlexPathCalculator = new StreetFlexPathCalculator(false, config);
        this.egressFlexPathCalculator = new StreetFlexPathCalculator(true, config);
      }
      case STREET_WITH_DIRECT_FALLBACK -> {
        this.accessFlexPathCalculator = new StreetWithDirectFallbackPathCalculator(false, config);
        this.egressFlexPathCalculator = new StreetWithDirectFallbackPathCalculator(true, config);
      }
      default -> {
        // this is only really useful in tests. in real world scenarios you're unlikely to get useful
        // results if you don't have streets
        this.accessFlexPathCalculator = new DirectFlexPathCalculator(config);
        this.egressFlexPathCalculator = new DirectFlexPathCalculator(config);
      }
    }

    ZoneId tz = transitService.getTimeZone();
    LocalDate searchDate = LocalDate.ofInstant(searchInstant, tz);
    this.startOfTime = ServiceDateUtils.asStartOfService(searchDate, tz);
    this.departureTime = ServiceDateUtils.secondsSinceStartOfTime(startOfTime, searchInstant);
    this.arriveBy = arriveBy;

    int totalDays = additionalPastSearchDays + 1 + additionalFutureSearchDays;

    this.dates = new FlexServiceDate[totalDays];

    for (int d = -additionalPastSearchDays; d <= additionalFutureSearchDays; ++d) {
      LocalDate date = searchDate.plusDays(d);
      int index = d + additionalPastSearchDays;
      dates[index] =
        new FlexServiceDate(
          date,
          ServiceDateUtils.secondsSinceStartOfTime(startOfTime, date),
          transitService.getServiceCodesRunningForDate(date)
        );
    }
  }

  public Collection<Itinerary> createFlexOnlyItineraries() {
    calculateFlexAccessTemplates();
    calculateFlexEgressTemplates();

    Multimap<StopLocation, NearbyStop> streetEgressByStop = HashMultimap.create();
    streetEgresses.forEach(it -> streetEgressByStop.put(it.stop, it));

    Collection<Itinerary> itineraries = new ArrayList<>();

    for (FlexAccessTemplate template : this.flexAccessTemplates) {
      StopLocation transferStop = template.getTransferStop();
      if (
        this.flexEgressTemplates.stream()
          .anyMatch(t -> t.getAccessEgressStop().equals(transferStop))
      ) {
        for (NearbyStop egress : streetEgressByStop.get(transferStop)) {
          Itinerary itinerary = template.createDirectGraphPath(
            egress,
            arriveBy,
            departureTime,
            startOfTime,
            graphPathToItineraryMapper
          );
          if (itinerary != null) {
            itineraries.add(itinerary);
          }
        }
      }
    }

    return itineraries;
  }

  public Collection<FlexAccessEgress> createFlexAccesses() {
    calculateFlexAccessTemplates();

    return this.flexAccessTemplates.stream()
      .flatMap(template -> template.createFlexAccessEgressStream(graph, transitService))
      .collect(Collectors.toList());
  }

  public Collection<FlexAccessEgress> createFlexEgresses() {
    calculateFlexEgressTemplates();

    return this.flexEgressTemplates.stream()
      .flatMap(template -> template.createFlexAccessEgressStream(graph, transitService))
      .collect(Collectors.toList());
  }

  private void calculateFlexAccessTemplates() {
    if (this.flexAccessTemplates != null) {
      return;
    }

    // Fetch the closest flexTrips reachable from the access stops
    this.flexAccessTemplates =
      getClosestFlexTrips(streetAccesses, true)
        .filter(it -> TransitMode.TAXI1.equals(it.flexTrip().getTrip().getMode()))
        // For each date the router has data for
        .flatMap(it ->
          Arrays
            .stream(dates)
            // Discard if service is not running on date
            .filter(date -> date.isFlexTripRunning(it.flexTrip(), this.transitService))
            // Create templates from trip, boarding at the nearbyStop
            .flatMap(date ->
              it
                .flexTrip()
                .getFlexAccessTemplates(it.accessEgress(), date, accessFlexPathCalculator, config)
            )
        )
        .collect(Collectors.toList());
  }

  private void calculateFlexEgressTemplates() {
    if (this.flexEgressTemplates != null) {
      return;
    }

    // Fetch the closest flexTrips reachable from the egress stops
    this.flexEgressTemplates =
      getClosestFlexTrips(streetEgresses, false)
        .filter(it -> TransitMode.TAXI2.equals(it.flexTrip().getTrip().getMode()))
        // For each date the router has data for
        .flatMap(it ->
          Arrays
            .stream(dates)
            // Discard if service is not running on date
            .filter(date -> date.isFlexTripRunning(it.flexTrip(), this.transitService))
            // Create templates from trip, alighting at the nearbyStop
            .flatMap(date ->
              it
                .flexTrip()
                .getFlexEgressTemplates(it.accessEgress(), date, egressFlexPathCalculator, config)
            )
        )
        .collect(Collectors.toList());
  }

  private Stream<AccessEgressAndNearbyStop> getClosestFlexTrips(
    Collection<NearbyStop> nearbyStops,
    boolean pickup
  ) {
    // Find all trips reachable from the nearbyStops
    Stream<AccessEgressAndNearbyStop> flexTripsReachableFromNearbyStops = nearbyStops
      .stream()
      .flatMap(accessEgress ->
        flexIndex
          .getFlexTripsByStop(accessEgress.stop)
          .stream()
          .filter(flexTrip ->
            pickup
              ? flexTrip.isBoardingPossible(accessEgress)
              : flexTrip.isAlightingPossible(accessEgress)
          )
          .map(flexTrip -> new AccessEgressAndNearbyStop(accessEgress, flexTrip))
      );

    // Group all (NearbyStop, FlexTrip) tuples by flexTrip
    Collection<List<AccessEgressAndNearbyStop>> groupedReachableFlexTrips = flexTripsReachableFromNearbyStops
      .collect(Collectors.groupingBy(AccessEgressAndNearbyStop::flexTrip))
      .values();

    // Get the tuple with least walking time from each group
    return groupedReachableFlexTrips
      .stream()
      .map(t2s ->
        t2s
          .stream()
          .min(Comparator.comparingLong(t2 -> t2.accessEgress().state.getElapsedTimeSeconds()))
      )
      .flatMap(Optional::stream);
  }

  private record AccessEgressAndNearbyStop(NearbyStop accessEgress, FlexTrip<?, ?> flexTrip) {}
}
