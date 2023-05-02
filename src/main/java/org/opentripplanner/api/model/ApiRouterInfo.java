package org.opentripplanner.api.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.opentripplanner.api.mapping.ModeMapper;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vehicle_parking.VehicleParkingService;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.service.worldenvelope.model.WorldEnvelope;
import org.opentripplanner.transit.service.TransitService;

@SuppressWarnings("unused")
public class ApiRouterInfo {

  /** Keep ref to domain object, but avoid exposing it */
  private final WorldEnvelope envelope;
  public final boolean hasBikePark;
  public final boolean hasCarPark;
  public final boolean hasVehicleParking;
  public String routerId;
  public ApiPolygon polygon;
  public Instant buildTime;
  public long transitServiceStarts;
  public long transitServiceEnds;
  public List<String> transitModes;
  public boolean hasParkRide;
  public boolean hasBikeSharing;
  public List<ApiTravelOption> travelOptions;

  /** TODO: Do not pass in the graph here, do this in a mapper instead. */
  public ApiRouterInfo(
    String routerId,
    Graph graph,
    TransitService transitService,
    VehicleRentalService vehicleRentalService,
    WorldEnvelope envelope
  ) {
    VehicleParkingService vehicleParkingService = graph.getVehicleParkingService();

    this.routerId = routerId;
    this.polygon = mapToApi((Polygon) graph.getConvexHull());
    this.buildTime = graph.buildTime;
    this.transitServiceStarts = transitService.getTransitServiceStarts().toEpochSecond();
    this.transitServiceEnds = transitService.getTransitServiceEnds().toEpochSecond();
    this.transitModes = ModeMapper.mapToApi(transitService.getTransitModes());
    this.envelope = envelope;
    this.hasBikeSharing = mapHasBikeSharing(vehicleRentalService);
    this.hasBikePark = mapHasBikePark(vehicleParkingService);
    this.hasCarPark = mapHasCarPark(vehicleParkingService);
    this.hasParkRide = this.hasCarPark;
    this.hasVehicleParking = mapHasVehicleParking(vehicleParkingService);
    this.travelOptions =
      ApiTravelOptionsMaker.makeOptions(graph, vehicleRentalService, transitService);
  }

  public boolean mapHasBikeSharing(VehicleRentalService service) {
    if (service == null) {
      return false;
    }

    //at least 2 bike sharing stations are needed for useful bike sharing
    return service.getVehicleRentalPlaces().size() > 1;
  }

  public boolean mapHasBikePark(VehicleParkingService service) {
    if (service == null) {
      return false;
    }
    return service.getBikeParks().findAny().isPresent();
  }

  public boolean mapHasCarPark(VehicleParkingService service) {
    if (service == null) {
      return false;
    }
    return service.getCarParks().findAny().isPresent();
  }

  public boolean mapHasVehicleParking(VehicleParkingService service) {
    if (service == null) {
      return false;
    }
    return service.getVehicleParkings().findAny().isPresent();
  }

  public double getLowerLeftLatitude() {
    return envelope.lowerLeft().latitude();
  }

  public double getLowerLeftLongitude() {
    return envelope.lowerLeft().longitude();
  }

  public double getUpperRightLatitude() {
    return envelope.upperRight().latitude();
  }

  public double getUpperRightLongitude() {
    return envelope.upperRight().longitude();
  }

  public double getCenterLatitude() {
    return envelope.center().latitude();
  }

  public double getCenterLongitude() {
    return envelope.center().longitude();
  }

  private ApiPolygon mapToApi(Polygon convexHull) {
    var coordinates = new ArrayList<List<Double>>();
    for (Coordinate coordinate : convexHull.getExteriorRing().getCoordinates()) {
      coordinates.add(List.of(coordinate.getX(), coordinate.getY()));
    }

    var api = new ApiPolygon();
    api.coordinates = List.of(coordinates);

    return api;
  }

  public static class ApiPolygon {

    public String type = "Polygon";
    public List<List<List<Double>>> coordinates;
  }
}
