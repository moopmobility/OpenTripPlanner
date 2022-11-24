package org.opentripplanner.graph_builder.module;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.opentripplanner.graph_builder.DataImportIssueStore;
import org.opentripplanner.graph_builder.issues.MissingTransferWithinStation;
import org.opentripplanner.graph_builder.issues.StationWithMissingTransfers;
import org.opentripplanner.graph_builder.issues.StopNotLinkedForTransfers;
import org.opentripplanner.graph_builder.issues.SuspectTransferWithinStation;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.Transfer;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.routing.edgetype.PathwayEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.routing.vertextype.TransitStopVertex;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.site.StopLocationsGroup;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitModel;
import org.opentripplanner.util.OTPFeature;
import org.opentripplanner.util.logging.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link GraphBuilderModule} module that links up the stops of a transit network among themselves.
 * This is necessary for routing in long-distance mode.
 * <p>
 * It will use the street network if OSM data has already been loaded into the graph. Otherwise it
 * will use straight-line distance between stops.
 */
public class DirectTransferGenerator implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(DirectTransferGenerator.class);

  private final Duration radiusByDuration;

  private final List<RoutingRequest> transferRequests;
  private final Graph graph;
  private final TransitModel transitModel;
  private final DataImportIssueStore issueStore;
  private final boolean limitTransfersToWithinStations;

  public DirectTransferGenerator(
    Graph graph,
    TransitModel transitModel,
    DataImportIssueStore issueStore,
    Duration radiusByDuration,
    boolean limitTransfersToWithinStations,
    List<RoutingRequest> transferRequests
  ) {
    this.graph = graph;
    this.transitModel = transitModel;
    this.issueStore = issueStore;
    this.radiusByDuration = radiusByDuration;
    this.limitTransfersToWithinStations = limitTransfersToWithinStations;
    this.transferRequests = transferRequests;
  }

  @Override
  public void buildGraph() {
    /* Initialize transit model index which is needed by the nearby stop finder. */
    if (transitModel.getTransitModelIndex() == null) {
      transitModel.index();
    }

    /* The linker will use streets if they are available, or straight-line distance otherwise. */
    NearbyStopFinder nearbyStopFinder = new NearbyStopFinder(
      graph,
      new DefaultTransitService(transitModel),
      radiusByDuration
    );
    if (nearbyStopFinder.useStreets) {
      LOG.info("Creating direct transfer edges between stops using the street network from OSM...");
    } else {
      LOG.info(
        "Creating direct transfer edges between stops using straight line distance (not streets)..."
      );
    }

    List<TransitStopVertex> stops = graph.getVerticesOfType(TransitStopVertex.class);

    ProgressTracker progress = ProgressTracker.track(
      "Create transfer edges for stops",
      1000,
      stops.size()
    );

    AtomicInteger nTransfersTotal = new AtomicInteger();
    AtomicInteger nLinkedStops = new AtomicInteger();

    // This is a synchronizedMultimap so that a parallel stream may be used to insert elements.
    var transfersByStop = Multimaps.<StopLocation, PathTransfer>synchronizedMultimap(
      HashMultimap.create()
    );

    var stopPairsWithTransfers = Multimaps.<StopPair, PathTransfer>synchronizedMultimap(
      HashMultimap.create()
    );

    stops
      .stream()
      .parallel()
      .forEach(ts0 -> {
        /* Make transfers to each nearby stop that has lowest weight on some trip pattern.
         * Use map based on the list of edges, so that only distinct transfers are stored. */
        Map<TransferKey, PathTransfer> distinctTransfers = new HashMap<>();
        RegularStop stop = ts0.getStop();
        LOG.debug("Linking stop '{}' {}", stop, ts0);

        for (RoutingRequest transferProfile : transferRequests) {
          RoutingRequest streetRequest = Transfer.prepareTransferRoutingRequest(transferProfile);

          for (NearbyStop sd : findNearbyStops(nearbyStopFinder, ts0, streetRequest, false)) {
            // Skip the origin stop, loop transfers are not needed.
            if (sd.stop == stop) {
              continue;
            }
            distinctTransfers.put(
              new TransferKey(stop, sd.stop, sd.edges),
              new PathTransfer(stop, sd.stop, sd.distance, sd.edges)
            );
          }
          if (OTPFeature.FlexRouting.isOn()) {
            // This code is for finding transfers from AreaStops to Stops, transfers
            // from Stops to AreaStops and between Stops are already covered above.
            for (NearbyStop sd : findNearbyStops(nearbyStopFinder, ts0, streetRequest, true)) {
              // Skip the origin stop, loop transfers are not needed.
              if (sd.stop == stop) {
                continue;
              }
              if (sd.stop instanceof RegularStop) {
                continue;
              }
              distinctTransfers.put(
                new TransferKey(sd.stop, stop, sd.edges),
                new PathTransfer(sd.stop, stop, sd.distance, sd.edges)
              );
            }
          }
        }

        LOG.debug(
          "Linked stop {} with {} transfers to stops with different patterns.",
          stop,
          distinctTransfers.size()
        );
        if (distinctTransfers.isEmpty()) {
          issueStore.add(new StopNotLinkedForTransfers(ts0));
        } else {
          distinctTransfers
            .values()
            .stream()
            .filter(pathTransfer ->
              !limitTransfersToWithinStations ||
              pathTransfer.to.getParentStation() == pathTransfer.from.getParentStation()
            )
            .forEach(transfer -> transfersByStop.put(transfer.from, transfer));

          distinctTransfers.forEach((transferKey, pathTransfer) ->
            stopPairsWithTransfers.put(
              new StopPair(transferKey.source, transferKey.target),
              pathTransfer
            )
          );

          nLinkedStops.incrementAndGet();
          nTransfersTotal.addAndGet(distinctTransfers.size());
        }

        //Keep lambda! A method-ref would causes incorrect class and line number to be logged
        //noinspection Convert2MethodRef
        progress.step(m -> LOG.info(m));
      });

    transitModel.addAllTransfersByStops(transfersByStop);

    LOG.info(progress.completeMessage());
    LOG.info(
      "Done connecting stops to one another. Created a total of {} transfers from {} stops.",
      nTransfersTotal,
      nLinkedStops
    );

    var shorTransferLimit = 850;
    var stopPairsWithShortAccessibleTransfers = new HashSet<DirectTransferGenerator.StopPair>();

    stopPairsWithTransfers
      .keySet()
      .stream()
      .sorted(
        Comparator.comparing(stopPair ->
          "%s-%s-%s-%s".formatted(
              stopPair.from.getName(),
              stopPair.from.getId().toString(),
              stopPair.to.getName(),
              stopPair.to.getId().toString()
            )
        )
      )
      .forEach(stopPair -> {
        var hasWheelchairAccessibleTransfer = stopPairsWithTransfers
          .get(stopPair)
          .stream()
          .anyMatch(pathTransfer ->
            Optional
              .ofNullable(pathTransfer.getEdges())
              .stream()
              .flatMap(List::stream)
              .noneMatch(edge ->
                (edge instanceof StreetEdge && ((StreetEdge) edge).isStairs()) ||
                (edge instanceof StreetEdge && !((StreetEdge) edge).isWheelchairAccessible()) ||
                (edge instanceof PathwayEdge && !((PathwayEdge) edge).isWheelchairAccessible())
              )
          );

        var hasShortTransfer = stopPairsWithTransfers
          .get(stopPair)
          .stream()
          .anyMatch(pathTransfer -> pathTransfer.getDistanceMeters() < shorTransferLimit);

        if (hasWheelchairAccessibleTransfer && hasShortTransfer) {
          stopPairsWithShortAccessibleTransfers.add(stopPair);
        }
      });

    transitModel
      .getStopModel()
      .listStopLocationGroups()
      .stream()
      .sorted(
        Comparator
          .<StopLocationsGroup, Integer>comparing(stopLocationsGroup ->
            stopLocationsGroup.getChildStops().size()
          )
          .reversed()
      )
      .forEach(stopLocationsGroup -> {
        var size = stopLocationsGroup.getChildStops().size();

        var nokChildTransfers = stopLocationsGroup
          .getChildStops()
          .stream()
          .sorted(Comparator.comparing(stopLocation -> stopLocation.getId().toString()))
          .mapToLong(stopA ->
            stopLocationsGroup
              .getChildStops()
              .stream()
              .sorted(Comparator.comparing(stopLocation -> stopLocation.getId().toString()))
              .filter(stopB -> stopA != stopB)
              .filter(stopB -> {
                var pair = new StopPair(stopA, stopB);
                if (!stopPairsWithTransfers.containsKey(pair)) {
                  issueStore.add(
                    new MissingTransferWithinStation(
                      stopLocationsGroup.getName().toString(),
                      stopLocationsGroup.getCoordinate(),
                      Optional
                        .ofNullable(stopA.getPlatformCode())
                        .orElse(stopA.getName().toString()),
                      stopA.getCoordinate(),
                      Optional
                        .ofNullable(stopB.getPlatformCode())
                        .orElse(stopB.getName().toString()),
                      stopB.getCoordinate()
                    )
                  );
                  return true;
                } else if (!stopPairsWithShortAccessibleTransfers.contains(pair)) {
                  issueStore.add(
                    new SuspectTransferWithinStation(
                      stopLocationsGroup.getName().toString(),
                      stopLocationsGroup.getCoordinate(),
                      Optional
                        .ofNullable(stopA.getPlatformCode())
                        .orElse(stopA.getName().toString()),
                      Optional
                        .ofNullable(stopB.getPlatformCode())
                        .orElse(stopB.getName().toString()),
                      stopPairsWithTransfers
                        .get(pair)
                        .stream()
                        .findFirst()
                        .map(PathTransfer::getEdges)
                        .map(Collection::stream)
                        .stream()
                        .flatMap(Function.identity())
                        .noneMatch(edge ->
                          (edge instanceof StreetEdge && ((StreetEdge) edge).isStairs()) ||
                          (
                            edge instanceof StreetEdge &&
                            !((StreetEdge) edge).isWheelchairAccessible()
                          ) ||
                          (
                            edge instanceof PathwayEdge &&
                            !((PathwayEdge) edge).isWheelchairAccessible()
                          )
                        ),
                      stopPairsWithTransfers
                        .get(pair)
                        .stream()
                        .mapToDouble(PathTransfer::getDistanceMeters)
                        .min()
                        .orElse(Double.NaN),
                      shorTransferLimit,
                      stopPairsWithTransfers.get(pair).stream().map(PathTransfer::getEdges)
                    )
                  );
                  return true;
                }

                return false;
              })
              .count()
          )
          .sum();

        if (nokChildTransfers > 0) {
          issueStore.add(
            new StationWithMissingTransfers(
              stopLocationsGroup.getName().toString(),
              stopLocationsGroup.getCoordinate(),
              size,
              nokChildTransfers
            )
          );
        }
      });
  }

  @Override
  public void checkInputs() {
    // No inputs
  }

  private static Iterable<NearbyStop> findNearbyStops(
    NearbyStopFinder nearbyStopFinder,
    Vertex vertex,
    RoutingRequest routingRequest,
    boolean reverseDirection
  ) {
    return OTPFeature.ConsiderPatternsForDirectTransfers.isOn()
      ? nearbyStopFinder.findNearbyStopsConsideringPatterns(
        vertex,
        routingRequest,
        reverseDirection
      )
      : nearbyStopFinder.findNearbyStops(vertex, routingRequest, reverseDirection);
  }

  private record TransferKey(StopLocation source, StopLocation target, List<Edge> edges) {}

  private record StopPair(StopLocation from, StopLocation to) {}
}
