package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.RaptorCostConverter;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TransferWithDuration;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.transit.raptor.api.transit.RaptorTransfer;

public class Transfer {

  private final int toStop;

  private final int distanceMeters;

  private final List<Edge> edges;

  public Transfer(int toStop, List<Edge> edges) {
    this.toStop = toStop;
    this.edges = edges;
    this.distanceMeters = (int) edges.stream().mapToDouble(Edge::getDistanceMeters).sum();
  }

  public Transfer(int toStopIndex, int distanceMeters) {
    this.toStop = toStopIndex;
    this.distanceMeters = distanceMeters;
    this.edges = null;
  }

  public static RoutingRequest prepareTransferRoutingRequest(RoutingRequest request) {
    RoutingRequest rr = request.getStreetSearchRequest(request.modes.transferMode);

    rr.arriveBy = false;
    rr.setDateTime(Instant.ofEpochSecond(0));
    rr.from = null;
    rr.to = null;

    rr.bikeTriangleSafetyFactor = request.bikeTriangleSafetyFactor;
    rr.bikeTriangleSlopeFactor = request.bikeTriangleSlopeFactor;
    rr.bikeTriangleTimeFactor = request.bikeTriangleTimeFactor;
    rr.bikeSwitchCost = request.bikeSwitchCost;
    rr.bikeSwitchTime = request.bikeSwitchTime;

    // it's a record (immutable) so can be safely reused
    rr.wheelchairAccessibility = request.wheelchairAccessibility;

    rr.walkSpeed = request.walkSpeed;
    rr.bikeSpeed = request.bikeSpeed;

    rr.walkReluctance = request.walkReluctance;
    rr.stairsReluctance = request.stairsReluctance;
    rr.stairsTimeFactor = request.stairsTimeFactor;
    rr.turnReluctance = request.turnReluctance;
    rr.walkSafetyFactor = request.walkSafetyFactor;

    rr.elevatorBoardCost = request.elevatorBoardCost;
    rr.elevatorBoardTime = request.elevatorBoardTime;
    rr.elevatorHopCost = request.elevatorHopCost;
    rr.elevatorHopTime = request.elevatorHopTime;

    return rr;
  }

  public List<Coordinate> getCoordinates() {
    List<Coordinate> coordinates = new ArrayList<>();
    if (edges == null) {
      return coordinates;
    }
    for (Edge edge : edges) {
      if (edge.getGeometry() != null) {
        coordinates.addAll((Arrays.asList(edge.getGeometry().getCoordinates())));
      }
    }
    return coordinates;
  }

  public int getToStop() {
    return toStop;
  }

  public int getDistanceMeters() {
    return distanceMeters;
  }

  public List<Edge> getEdges() {
    return edges;
  }

  public Optional<RaptorTransfer> asRaptorTransfer(RoutingContext routingContext) {
    RoutingRequest routingRequest = routingContext.opt;
    if (edges == null || edges.isEmpty()) {
      double durationSeconds = distanceMeters / routingRequest.walkSpeed;
      return Optional.of(
        new TransferWithDuration(
          this,
          (int) Math.ceil(durationSeconds),
          RaptorCostConverter.toRaptorCost(durationSeconds * routingRequest.walkReluctance)
        )
      );
    }

    StateEditor se = new StateEditor(routingContext, edges.get(0).getFromVertex());
    se.setTimeSeconds(0);

    State s = se.makeState();
    for (Edge e : edges) {
      s = e.traverse(s);
      if (s == null) {
        return Optional.empty();
      }
    }

    return Optional.of(
      new TransferWithDuration(
        this,
        (int) s.getElapsedTimeSeconds(),
        RaptorCostConverter.toRaptorCost(s.getWeight())
      )
    );
  }

  private static double roundToHalf(double input) {
    return ((int) (input * 2 + 0.5)) / 2.0;
  }

  private static double roundTo(double input, int decimals) {
    return Math.round(input * Math.pow(10, decimals)) / Math.pow(10, decimals);
  }

  private static int roundTo100(int input) {
    if (input > 0 && input < 100) {
      return 100;
    }

    return ((input + 50) / 100) * 100;
  }
}
