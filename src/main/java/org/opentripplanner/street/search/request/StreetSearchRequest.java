package org.opentripplanner.street.search.request;

import java.time.Instant;
import javax.annotation.Nonnull;
import org.opentripplanner.astar.spi.AStarRequest;
import org.opentripplanner.ext.dataoverlay.routing.DataOverlayContext;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.request.VehicleParkingRequest;
import org.opentripplanner.routing.api.request.request.VehicleRentalRequest;
import org.opentripplanner.street.search.intersection_model.IntersectionTraversalCalculator;

/**
 * This class contains all information from the {@link RouteRequest} class required for an A* search
 */
public class StreetSearchRequest implements AStarRequest {

  private static final StreetSearchRequest DEFAULT = new StreetSearchRequest();

  // the time at which the search started
  private final Instant startTime;
  private final RoutingPreferences preferences;
  private final StreetMode mode;
  private final boolean arriveBy;
  private final boolean wheelchair;
  private final VehicleParkingRequest parking;
  private final VehicleRentalRequest rental;

  private final GenericLocation from;
  private final GenericLocation to;

  private IntersectionTraversalCalculator intersectionTraversalCalculator =
    IntersectionTraversalCalculator.DEFAULT;

  private DataOverlayContext dataOverlayContext;

  /**
   * Constructor only used for creating a default instance.
   */
  private StreetSearchRequest() {
    this.startTime = Instant.now();
    this.preferences = new RoutingPreferences();
    this.mode = StreetMode.WALK;
    this.arriveBy = false;
    this.wheelchair = false;
    this.parking = new VehicleParkingRequest();
    this.rental = new VehicleRentalRequest();
    this.from = null;
    this.to = null;
  }

  StreetSearchRequest(StreetSearchRequestBuilder builder) {
    this.startTime = builder.startTime;
    this.preferences = builder.preferences;
    this.mode = builder.mode;
    this.arriveBy = builder.arriveBy;
    this.wheelchair = builder.wheelchair;
    this.parking = builder.parking;
    this.rental = builder.rental;
    this.from = builder.from;
    this.to = builder.to;
  }

  @Nonnull
  public static StreetSearchRequestBuilder of() {
    return new StreetSearchRequestBuilder(DEFAULT).withStartTime(Instant.now());
  }

  @Nonnull
  public static StreetSearchRequestBuilder copyOf(StreetSearchRequest original) {
    return new StreetSearchRequestBuilder(original);
  }

  public Instant startTime() {
    return startTime;
  }

  public RoutingPreferences preferences() {
    return preferences;
  }

  /**
   * The requested mode for the search. This contains information about all allowed transitions
   * between the different traverse modes, such as renting or parking a vehicle. Contrary to
   * currentMode, which can change when traversing edges, this is constant for a single search.
   */
  public StreetMode mode() {
    return mode;
  }

  public boolean arriveBy() {
    return arriveBy;
  }

  public boolean wheelchair() {
    return wheelchair;
  }

  public VehicleParkingRequest parking() {
    return parking;
  }

  public VehicleRentalRequest rental() {
    return rental;
  }

  public GenericLocation from() {
    return from;
  }

  public GenericLocation to() {
    return to;
  }

  public IntersectionTraversalCalculator intersectionTraversalCalculator() {
    return intersectionTraversalCalculator;
  }

  public DataOverlayContext dataOverlayContext() {
    return dataOverlayContext;
  }

  public StreetSearchRequestBuilder copyOfReversed(Instant time) {
    return copyOf(this).withStartTime(time).withArriveBy(!arriveBy);
  }

  public void setIntersectionTraversalCalculator(
    IntersectionTraversalCalculator intersectionTraversalCalculator
  ) {
    this.intersectionTraversalCalculator = intersectionTraversalCalculator;
  }

  public void setDataOverlayContext(DataOverlayContext dataOverlayContext) {
    this.dataOverlayContext = dataOverlayContext;
  }
}
