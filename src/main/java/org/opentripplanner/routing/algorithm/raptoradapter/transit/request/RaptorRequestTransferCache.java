package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransferIndex;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.Transfer;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.WheelchairAccessibilityRequest;
import org.opentripplanner.routing.core.BicycleOptimizeType;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.util.OTPFeature;
import org.opentripplanner.util.lang.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaptorRequestTransferCache {

  private static final Logger LOG = LoggerFactory.getLogger(RaptorRequestTransferCache.class);

  private final LoadingCache<CacheKey, RaptorTransferIndex> transferCache;
  private final ForkJoinPool threadPool;

  public RaptorRequestTransferCache(int maximumSize, int maximumThreads) {
    transferCache = CacheBuilder.newBuilder().maximumSize(maximumSize).build(cacheLoader());
    if (OTPFeature.ParallelRouting.isOn() && maximumThreads > 0) {
      var factory = new ForkJoinPool.ForkJoinWorkerThreadFactory() {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
          var worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
          worker.setName(
            RaptorRequestTransferCache.class.getSimpleName() + "-" + worker.getPoolIndex()
          );
          return worker;
        }
      };
      threadPool = new ForkJoinPool(maximumThreads, factory, null, false);
    } else {
      threadPool = null;
    }
  }

  public LoadingCache<CacheKey, RaptorTransferIndex> getTransferCache() {
    return transferCache;
  }

  public RaptorTransferIndex get(
    List<List<Transfer>> transfersByStopIndex,
    RoutingContext routingContext
  ) {
    try {
      return transferCache.get(new CacheKey(transfersByStopIndex, routingContext));
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to get item from transfer cache", e);
    }
  }

  private CacheLoader<CacheKey, RaptorTransferIndex> cacheLoader() {
    return new CacheLoader<>() {
      @Override
      public RaptorTransferIndex load(@javax.annotation.Nonnull CacheKey cacheKey) {
        LOG.info("Adding request to cache: {}", cacheKey.options);
        if (threadPool != null) {
          try {
            return threadPool
              .submit(() ->
                RaptorTransferIndex.create(cacheKey.transfersByStopIndex, cacheKey.routingContext)
              )
              .get();
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to calculate RaptorTransferIndex for request", e);
          }
        } else {
          return RaptorTransferIndex.create(cacheKey.transfersByStopIndex, cacheKey.routingContext);
        }
      }
    };
  }

  private static class CacheKey {

    private final List<List<Transfer>> transfersByStopIndex;
    private final RoutingContext routingContext;
    private final StreetRelevantOptions options;

    private CacheKey(List<List<Transfer>> transfersByStopIndex, RoutingContext routingContext) {
      this.transfersByStopIndex = transfersByStopIndex;
      this.routingContext = routingContext;
      this.options = new StreetRelevantOptions(routingContext.opt);
    }

    @Override
    public int hashCode() {
      // transfersByStopIndex is ignored on purpose since it should not change (there is only
      // one instance per graph) and calculating the hashCode() would be expensive
      return options.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CacheKey cacheKey = (CacheKey) o;
      // transfersByStopIndex is checked using == on purpose since the instance should not change
      // (there is only one instance per graph)
      return (
        transfersByStopIndex == cacheKey.transfersByStopIndex && options.equals(cacheKey.options)
      );
    }
  }

  /**
   * This contains an extract of the parameters which may influence transfers. The possible values
   * are somewhat limited by rounding in {@link Transfer#prepareTransferRoutingRequest(RoutingRequest)}.
   */
  static class StreetRelevantOptions {

    private final StreetMode transferMode;
    private final BicycleOptimizeType optimize;
    private final double bikeTriangleSafetyFactor;
    private final double bikeTriangleSlopeFactor;
    private final double bikeTriangleTimeFactor;
    private final WheelchairAccessibilityRequest wheelchairAccessibility;
    private final double walkSpeed;
    private final double bikeSpeed;
    private final double bikeWalkingSpeed;
    private final double walkReluctance;
    private final double stairsReluctance;
    private final double stairsTimeFactor;
    private final double turnReluctance;
    private final double bikeWalkingReluctance;
    private final int elevatorBoardCost;
    private final int elevatorBoardTime;
    private final int elevatorHopCost;
    private final int elevatorHopTime;
    private final int bikeSwitchCost;
    private final int bikeSwitchTime;

    public StreetRelevantOptions(RoutingRequest routingRequest) {
      this.transferMode = routingRequest.modes.transferMode;

      this.optimize = routingRequest.bicycleOptimizeType;
      this.bikeTriangleSafetyFactor = routingRequest.bikeTriangleSafetyFactor;
      this.bikeTriangleSlopeFactor = routingRequest.bikeTriangleSlopeFactor;
      this.bikeTriangleTimeFactor = routingRequest.bikeTriangleTimeFactor;
      this.bikeSwitchCost = routingRequest.bikeSwitchCost;
      this.bikeSwitchTime = routingRequest.bikeSwitchTime;

      this.wheelchairAccessibility = routingRequest.wheelchairAccessibility.round();

      this.walkSpeed = routingRequest.walkSpeed;
      this.bikeSpeed = routingRequest.bikeSpeed;
      this.bikeWalkingSpeed = routingRequest.bikeWalkingSpeed;

      this.walkReluctance = routingRequest.walkReluctance;
      this.stairsReluctance = routingRequest.stairsReluctance;
      this.stairsTimeFactor = routingRequest.stairsTimeFactor;
      this.turnReluctance = routingRequest.turnReluctance;
      this.bikeWalkingReluctance = routingRequest.bikeWalkingReluctance;

      this.elevatorBoardCost = routingRequest.elevatorBoardCost;
      this.elevatorBoardTime = routingRequest.elevatorBoardTime;
      this.elevatorHopCost = routingRequest.elevatorHopCost;
      this.elevatorHopTime = routingRequest.elevatorHopTime;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
        transferMode,
        optimize,
        bikeTriangleSafetyFactor,
        bikeTriangleSlopeFactor,
        bikeTriangleTimeFactor,
        wheelchairAccessibility,
        walkSpeed,
        bikeSpeed,
        bikeWalkingSpeed,
        walkReluctance,
        bikeWalkingReluctance,
        stairsReluctance,
        turnReluctance,
        elevatorBoardCost,
        elevatorBoardTime,
        elevatorHopCost,
        elevatorHopTime,
        bikeSwitchCost,
        bikeSwitchTime,
        stairsTimeFactor
      );
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final StreetRelevantOptions that = (StreetRelevantOptions) o;
      return (
        Double.compare(that.bikeTriangleSafetyFactor, bikeTriangleSafetyFactor) == 0 &&
        Double.compare(that.bikeTriangleSlopeFactor, bikeTriangleSlopeFactor) == 0 &&
        Double.compare(that.bikeTriangleTimeFactor, bikeTriangleTimeFactor) == 0 &&
        Double.compare(that.walkSpeed, walkSpeed) == 0 &&
        Double.compare(that.bikeSpeed, bikeSpeed) == 0 &&
        Double.compare(that.bikeWalkingSpeed, bikeWalkingSpeed) == 0 &&
        Double.compare(that.walkReluctance, walkReluctance) == 0 &&
        Double.compare(that.bikeWalkingReluctance, bikeWalkingReluctance) == 0 &&
        Double.compare(that.stairsReluctance, stairsReluctance) == 0 &&
        Double.compare(that.stairsTimeFactor, stairsTimeFactor) == 0 &&
        Double.compare(that.turnReluctance, turnReluctance) == 0 &&
        wheelchairAccessibility.equals(that.wheelchairAccessibility) &&
        elevatorBoardCost == that.elevatorBoardCost &&
        elevatorBoardTime == that.elevatorBoardTime &&
        elevatorHopCost == that.elevatorHopCost &&
        elevatorHopTime == that.elevatorHopTime &&
        bikeSwitchCost == that.bikeSwitchCost &&
        bikeSwitchTime == that.bikeSwitchTime &&
        transferMode == that.transferMode &&
        optimize == that.optimize
      );
    }

    @Override
    public String toString() {
      return ToStringBuilder
        .of(StreetRelevantOptions.class)
        .addEnum("transferMode", transferMode)
        .addEnum("optimize", optimize)
        .addNum("bikeTriangleSafetyFactor", bikeTriangleSafetyFactor)
        .addNum("bikeTriangleSlopeFactor", bikeTriangleSlopeFactor)
        .addNum("bikeTriangleTimeFactor", bikeTriangleTimeFactor)
        .addObj("wheelchairAccessible", wheelchairAccessibility)
        .addNum("walkSpeed", walkSpeed)
        .addNum("bikeSpeed", bikeSpeed)
        .addNum("bikeWalkingSpeed", bikeWalkingSpeed)
        .addNum("walkReluctance", walkReluctance)
        .addNum("bikeWalkReluctance", bikeWalkingReluctance)
        .addNum("stairsReluctance", stairsReluctance)
        .addNum("stairsTimeFactor", stairsTimeFactor)
        .addNum("turnReluctance", turnReluctance)
        .addNum("elevatorBoardCost", elevatorBoardCost)
        .addNum("elevatorBoardTime", elevatorBoardTime)
        .addNum("elevatorHopCost", elevatorHopCost)
        .addNum("elevatorHopTime", elevatorHopTime)
        .addNum("bikeSwitchCost", bikeSwitchCost)
        .addNum("bikeSwitchTime", bikeSwitchTime)
        .toString();
    }
  }
}
