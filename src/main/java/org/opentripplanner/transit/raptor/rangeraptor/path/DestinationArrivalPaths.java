package org.opentripplanner.transit.raptor.rangeraptor.path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import org.opentripplanner.transit.raptor.api.path.Path;
import org.opentripplanner.transit.raptor.api.path.PathLeg;
import org.opentripplanner.transit.raptor.api.transit.CostCalculator;
import org.opentripplanner.transit.raptor.api.transit.RaptorStopNameResolver;
import org.opentripplanner.transit.raptor.api.transit.RaptorTransfer;
import org.opentripplanner.transit.raptor.api.transit.RaptorTripSchedule;
import org.opentripplanner.transit.raptor.api.view.ArrivalView;
import org.opentripplanner.transit.raptor.rangeraptor.debug.DebugHandlerFactory;
import org.opentripplanner.transit.raptor.rangeraptor.internalapi.DebugHandler;
import org.opentripplanner.transit.raptor.rangeraptor.internalapi.SlackProvider;
import org.opentripplanner.transit.raptor.rangeraptor.internalapi.WorkerLifeCycle;
import org.opentripplanner.transit.raptor.rangeraptor.transit.AccessEgressFunctions;
import org.opentripplanner.transit.raptor.rangeraptor.transit.TransitCalculator;
import org.opentripplanner.transit.raptor.util.paretoset.ParetoComparator;
import org.opentripplanner.transit.raptor.util.paretoset.ParetoSet;
import org.opentripplanner.util.lang.OtpNumberFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The responsibility of this class is to collect result paths for destination arrivals. It does so
 * using a pareto set. The comparator is passed in as an argument to the constructor. This make is
 * possible to collect different sets in different scenarios.
 * <p/>
 * Depending on the pareto comparator passed into the constructor this class grantee that the best
 * paths with respect to <em>arrival time</em>, <em>rounds</em> and <em>travel duration</em> are
 * found. You may also add <em>cost</em> as a criteria (multi-criteria search).
 * <p/>
 * This class is a thin wrapper around a ParetoSet of {@link Path}s. Before paths are added the
 * arrival time is checked against the arrival time limit.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public class DestinationArrivalPaths<T extends RaptorTripSchedule> {

  private static final Logger LOG = LoggerFactory.getLogger(DestinationArrivalPaths.class);
  private static final Logger LOG_MISS_MATCH = LOG; //ThrottleLogger.throttle(LOG);

  private final ParetoSet<Path<T>> paths;
  private final TransitCalculator<T> transitCalculator;

  @Nullable
  private final CostCalculator<T> costCalculator;

  private final SlackProvider slackProvider;
  private final PathMapper<T> pathMapper;
  private final DebugHandler<Path<?>> debugPathHandler;
  private final RaptorStopNameResolver stopNameResolver;
  private boolean reachedCurrentRound = false;
  private int iterationDepartureTime = -1;

  public DestinationArrivalPaths(
    ParetoComparator<Path<T>> paretoComparator,
    TransitCalculator<T> transitCalculator,
    @Nullable CostCalculator<T> costCalculator,
    SlackProvider slackProvider,
    PathMapper<T> pathMapper,
    DebugHandlerFactory<T> debugHandlerFactory,
    RaptorStopNameResolver stopNameResolver,
    WorkerLifeCycle lifeCycle
  ) {
    this.paths =
      new ParetoSet<>(paretoComparator, debugHandlerFactory.paretoSetDebugPathListener());
    this.transitCalculator = transitCalculator;
    this.costCalculator = costCalculator;
    this.slackProvider = slackProvider;
    this.pathMapper = pathMapper;
    this.debugPathHandler = debugHandlerFactory.debugPathArrival();
    this.stopNameResolver = stopNameResolver;
    lifeCycle.onPrepareForNextRound(round -> clearReachedCurrentRoundFlag());
    lifeCycle.onSetupIteration(this::setRangeRaptorIterationDepartureTime);
  }

  public void add(ArrivalView<T> stopArrival, RaptorTransfer egressPath) {
    var destArrival = createDestinationArrivalView(stopArrival, egressPath);

    if (destArrival == null) {
      return;
    }

    if (transitCalculator.exceedsTimeLimit(destArrival.arrivalTime())) {
      debugRejectByTimeLimitOptimization(destArrival);
    } else {
      Path<T> path = pathMapper.mapToPath(destArrival);

      assertGeneralizedCostIsCalculatedCorrectByMapper(destArrival, path);

      boolean added = paths.add(path);
      if (added) {
        reachedCurrentRound = true;
      }
    }
  }

  /**
   * Check if destination was reached in the current round.
   */
  public boolean isReachedCurrentRound() {
    return reachedCurrentRound;
  }

  public void setRangeRaptorIterationDepartureTime(int iterationDepartureTime) {
    this.iterationDepartureTime = iterationDepartureTime;
  }

  public boolean isEmpty() {
    return paths.isEmpty();
  }

  public boolean qualify(int departureTime, int arrivalTime, int numberOfTransfers, int cost) {
    return paths.qualify(
      Path.dummyPath(iterationDepartureTime, departureTime, arrivalTime, numberOfTransfers, cost)
    );
  }

  public Collection<Path<T>> listPaths() {
    return paths;
  }

  public void debugReject(ArrivalView<T> stopArrival, RaptorTransfer egress, String reason) {
    if (isDebugOn()) {
      debugReject(createDestinationArrivalView(stopArrival, egress), reason);
    }
  }

  public void debugReject(DestinationArrival<T> arrival, String reason) {
    if (isDebugOn()) {
      var path = pathMapper.mapToPath(arrival);
      debugPathHandler.reject(path, null, reason);
    }
  }

  @Override
  public String toString() {
    return paths.toString(p -> p.toString(stopNameResolver));
  }

  public final boolean isDebugOn() {
    return debugPathHandler != null;
  }

  /* private methods */

  private void clearReachedCurrentRoundFlag() {
    reachedCurrentRound = false;
  }

  private void debugRejectByTimeLimitOptimization(DestinationArrival<T> destArrival) {
    if (isDebugOn()) {
      debugReject(destArrival, transitCalculator.exceedsTimeLimitReason());
    }
  }

  private DestinationArrival<T> createDestinationArrivalView(
    ArrivalView<T> stopArrival,
    RaptorTransfer egressPath
  ) {
    int departureTime = AccessEgressFunctions.calculateEgressDepartureTime(
      stopArrival.arrivalTime(),
      egressPath,
      slackProvider,
      transitCalculator
    );

    if (departureTime == RaptorTransfer.UNAVAILABLE) {
      return null;
    }

    int arrivalTime = transitCalculator.plusDuration(departureTime, egressPath.durationInSeconds());

    int waitTimeInSeconds = Math.abs(departureTime - stopArrival.arrivalTime());

    // If the aggregatedCost is zero(StdRaptor), then cost calculation is skipped.
    // If the aggregatedCost exist(McRaptor), then the cost of waiting is added.
    int additionalCost = 0;

    if (costCalculator != null) {
      additionalCost += costCalculator.waitCost(waitTimeInSeconds);
      additionalCost += costCalculator.costEgress(egressPath);
    }

    return new DestinationArrival<>(egressPath, stopArrival, arrivalTime, additionalCost);
  }

  /**
   * If the total cost generated by the mapper is not equal to the total cost calculated by Raptor,
   * there is probably a mistake in the mapper! This is a rather critical error and should be fixed.
   * To avoid dropping legal paths from the result set, we log this as an error and allow the path
   * to be included in the result!!!
   * <p>
   * The path mapper might not map the cost to each leg exactly as the Raptor does but the total
   * should be the same. Raptor only have stop-arrival, while the path have legs. A transit leg
   * alight BEFORE the transit stop arrival due to alight-slack.
   */
  private void assertGeneralizedCostIsCalculatedCorrectByMapper(
    DestinationArrival<T> destArrival,
    Path<T> path
  ) {
    if (path.generalizedCost() != destArrival.cost()) {
      // TODO - Bug: Cost mismatch stop-arrivals and paths #3623
      LOG_MISS_MATCH.warn(
        "Cost mismatch of {} - Mapper: {}, stop-arrivals: {}, path: {}",
        OtpNumberFormat.formatCost(path.generalizedCost() - destArrival.cost()),
        pathCostAsString(path),
        raptorCostsAsString(destArrival),
        path.toStringDetailed(stopNameResolver)
      );
    }
  }

  /**
   * Return the cost of all path legs.
   */
  private String pathCostAsString(Path<T> path) {
    var pathCosts = new ArrayList<String>();
    var it = (PathLeg<T>) path.accessLeg();
    while (it != null) {
      pathCosts.add(OtpNumberFormat.formatCost(it.generalizedCost()));
      it = it.isEgressLeg() ? null : it.nextLeg();
    }
    // Remove decimals if zero
    return (
      "%s (%s)".formatted(
          OtpNumberFormat.formatCost(path.generalizedCost()),
          String.join(" ", pathCosts)
        )
    ).replaceAll("\\.00", "");
  }

  /**
   * Return the cost of all stop arrivals.
   */
  private String raptorCostsAsString(DestinationArrival<T> destArrival) {
    var arrivals = new ArrayList<ArrivalView<?>>();
    ArrivalView<?> it = destArrival;
    while (it != null) {
      arrivals.add(it);
      it = it.previous();
    }

    Collections.reverse(arrivals);

    int previous = 0;
    var arrivalCosts = new ArrayList<String>();
    for (var arrival : arrivals) {
      arrivalCosts.add(OtpNumberFormat.formatCost(arrival.cost() - previous));
      previous = arrival.cost();
    }

    // Remove decimals if zero
    return (
      "%s (%s)".formatted(
          OtpNumberFormat.formatCost(destArrival.cost()),
          String.join(" ", arrivalCosts)
        )
    ).replaceAll("\\.00", "");
  }
}
