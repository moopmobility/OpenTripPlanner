package org.opentripplanner.ext.flex;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.ext.flex.edgetype.FlexTripEdge;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.framework.lang.DoubleUtils;
import org.opentripplanner.framework.tostring.ToStringBuilder;
import org.opentripplanner.model.BookingInfo;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.model.fare.FareProductUse;
import org.opentripplanner.model.plan.Leg;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.StopArrival;
import org.opentripplanner.model.plan.TransitLeg;
import org.opentripplanner.routing.alertpatch.TransitAlert;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.AbstractTransitEntity;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * One leg of a trip -- that is, a temporally continuous piece of the journey that takes place on a
 * particular vehicle, which is running on flexible trip, i.e. not using fixed schedule and stops.
 */
public class FlexibleTransitLeg implements TransitLeg {

  private final Trip trip;

  private final LocalDate serviceDate;

  private final LineString legGeometry;

  private final int fromStopIndex;

  private final int toStopIndex;

  private final Place from;

  private final Place to;

  private final PickDrop boardRule;

  private final PickDrop alightRule;

  private final BookingInfo pickupBookingInfo;

  private final BookingInfo dropoffBookingInfo;

  private final double distanceMeters;

  private final ZonedDateTime startTime;

  private final ZonedDateTime endTime;

  private final Set<TransitAlert> transitAlerts = new HashSet<>();

  private final boolean unscheduled;
  private final int generalizedCost;
  private List<FareProductUse> fareProducts;

  public FlexibleTransitLeg(
    FlexTripEdge flexTripEdge,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    int generalizedCost
  ) {
    this(
      flexTripEdge.getFlexTrip().getTrip(),
      flexTripEdge.getFlexTrip().getBoardRule(flexTripEdge.flexTemplate.fromStopIndex),
      flexTripEdge.getFlexTrip().getBoardRule(flexTripEdge.flexTemplate.toStopIndex),
      flexTripEdge.getFlexTrip().getPickupBookingInfo(flexTripEdge.flexTemplate.fromStopIndex),
      flexTripEdge.getFlexTrip().getPickupBookingInfo(flexTripEdge.flexTemplate.toStopIndex),
      flexTripEdge.getDistanceMeters(),
      flexTripEdge.getGeometry(),
      flexTripEdge.flexTemplate.serviceDate,
      flexTripEdge.flexTemplate.fromStopIndex,
      flexTripEdge.flexTemplate.toStopIndex,
      Place.forFlexStop(flexTripEdge.s1, flexTripEdge.getFromVertex()),
      Place.forFlexStop(flexTripEdge.s2, flexTripEdge.getToVertex()),
      startTime,
      endTime,
      generalizedCost,
      flexTripEdge.getFlexTrip() instanceof UnscheduledTrip
    );
  }

  public FlexibleTransitLeg(
    Trip trip,
    PickDrop boardRule,
    PickDrop alightRule,
    BookingInfo pickupBookingInfo,
    BookingInfo dropoffBookingInfo,
    double distanceMeters,
    LineString legGeometry,
    LocalDate serviceDate,
    int fromStopIndex,
    int toStopIndex,
    Place from,
    Place to,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    int generalizedCost,
    boolean unscheduled
  ) {
    this.trip = trip;
    this.boardRule = boardRule;
    this.alightRule = alightRule;
    this.pickupBookingInfo = pickupBookingInfo;
    this.dropoffBookingInfo = dropoffBookingInfo;
    this.distanceMeters = distanceMeters;
    this.serviceDate = serviceDate;
    this.legGeometry = legGeometry;
    this.fromStopIndex = fromStopIndex;
    this.toStopIndex = toStopIndex;
    this.from = from;
    this.to = to;

    this.startTime = startTime;
    this.endTime = endTime;

    this.generalizedCost = generalizedCost;
    this.unscheduled = unscheduled;
  }

  @Override
  public Agency getAgency() {
    return trip.getRoute().getAgency();
  }

  @Override
  public Operator getOperator() {
    return trip.getOperator();
  }

  @Override
  public Route getRoute() {
    return trip.getRoute();
  }

  @Override
  public Trip getTrip() {
    return trip;
  }

  @Override
  public Accessibility getTripWheelchairAccessibility() {
    return trip.getWheelchairBoarding();
  }

  @Override
  @Nonnull
  public TransitMode getMode() {
    return trip.getMode();
  }

  @Override
  public ZonedDateTime getStartTime() {
    return startTime;
  }

  @Override
  public ZonedDateTime getEndTime() {
    return endTime;
  }

  @Override
  public boolean isFlexibleTrip() {
    return true;
  }

  @Override
  public double getDistanceMeters() {
    return DoubleUtils.roundTo2Decimals(distanceMeters);
  }

  @Override
  public Integer getRouteType() {
    return trip.getRoute().getGtfsType();
  }

  @Override
  public I18NString getHeadsign() {
    return trip.getHeadsign();
  }

  @Override
  public LocalDate getServiceDate() {
    return serviceDate;
  }

  @Override
  public Place getFrom() {
    return from;
  }

  @Override
  public Place getTo() {
    return to;
  }

  @Override
  public List<StopArrival> getIntermediateStops() {
    return List.of();
  }

  @Override
  public LineString getLegGeometry() {
    return legGeometry;
  }

  @Override
  public Set<TransitAlert> getTransitAlerts() {
    return transitAlerts;
  }

  @Override
  public PickDrop getBoardRule() {
    return boardRule;
  }

  @Override
  public PickDrop getAlightRule() {
    return alightRule;
  }

  @Override
  public BookingInfo getDropOffBookingInfo() {
    return dropoffBookingInfo;
  }

  @Override
  public BookingInfo getPickupBookingInfo() {
    return pickupBookingInfo;
  }

  @Override
  public Integer getBoardStopPosInPattern() {
    return fromStopIndex;
  }

  @Override
  public Integer getAlightStopPosInPattern() {
    return toStopIndex;
  }

  @Override
  public int getGeneralizedCost() {
    return generalizedCost;
  }

  public void addAlert(TransitAlert alert) {
    transitAlerts.add(alert);
  }

  @Override
  public Leg withTimeShift(Duration duration) {
    FlexibleTransitLeg copy = new FlexibleTransitLeg(
      trip,
      boardRule,
      alightRule,
      pickupBookingInfo,
      dropoffBookingInfo,
      distanceMeters,
      legGeometry,
      serviceDate,
      fromStopIndex,
      toStopIndex,
      from,
      to,
      startTime.plus(duration),
      endTime.plus(duration),
      generalizedCost,
      unscheduled
    );

    for (TransitAlert alert : transitAlerts) {
      copy.addAlert(alert);
    }

    return copy;
  }

  @Override
  public void setFareProducts(List<FareProductUse> products) {
    this.fareProducts = List.copyOf(products);
  }

  @Override
  public List<FareProductUse> fareProducts() {
    return fareProducts;
  }

  public boolean isPartiallySameTransitLeg(Leg other) {
    var same = TransitLeg.super.isPartiallySameTransitLeg(other);
    // flexible trips have all the same trip id, so we have to check that the start times
    // are not equal
    if (same) {
      if (other instanceof FlexibleTransitLeg flexibleTransitLeg && unscheduled) {
        return (
          this.startTime.equals(flexibleTransitLeg.startTime) &&
          this.getFrom().sameLocation(flexibleTransitLeg.getFrom()) &&
          this.getTo().sameLocation(flexibleTransitLeg.getTo())
        );
      }
    }
    return false;
  }

  /**
   * Should be used for debug logging only
   */
  @Override
  public String toString() {
    return ToStringBuilder
      .of(FlexibleTransitLeg.class)
      .addObj("from", getFrom())
      .addObj("to", getTo())
      .addTime("startTime", startTime)
      .addTime("endTime", endTime)
      .addNum("distance", getDistanceMeters(), "m")
      .addNum("cost", generalizedCost)
      .addObjOp("agencyId", getAgency(), AbstractTransitEntity::getId)
      .addObjOp("routeId", getRoute(), AbstractTransitEntity::getId)
      .addObjOp("tripId", trip, AbstractTransitEntity::getId)
      .addObj("serviceDate", getServiceDate())
      .addObj("legGeometry", getLegGeometry())
      .addCol("transitAlerts", transitAlerts)
      .addNum("boardingStopIndex", getBoardStopPosInPattern())
      .addNum("alightStopIndex", getAlightStopPosInPattern())
      .addEnum("boardRule", getBoardRule())
      .addEnum("alightRule", getAlightRule())
      .addObj("pickupBookingInfo", getPickupBookingInfo())
      .addObj("dropOffBookingInfo", getDropOffBookingInfo())
      .toString();
  }
}
