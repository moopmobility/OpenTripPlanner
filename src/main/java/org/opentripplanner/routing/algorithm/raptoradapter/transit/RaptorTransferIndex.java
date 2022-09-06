package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.transit.raptor.api.transit.RaptorTransfer;
import org.opentripplanner.transit.raptor.util.ReversedRaptorTransfer;
import org.opentripplanner.util.OTPFeature;
import org.opentripplanner.util.logging.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaptorTransferIndex {

  private static final Logger LOG = LoggerFactory.getLogger(RaptorTransferIndex.class);

  private final List<RaptorTransfer>[] forwardTransfers;

  private final List<RaptorTransfer>[] reversedTransfers;

  @SuppressWarnings("unchecked")
  public RaptorTransferIndex(
    List<List<RaptorTransfer>> forwardTransfers,
    List<List<RaptorTransfer>> reversedTransfers
  ) {
    // Create immutable copies of the lists for each stop to make them immutable and faster to iterate
    this.forwardTransfers = forwardTransfers.stream().map(List::copyOf).toArray(List[]::new);
    this.reversedTransfers = reversedTransfers.stream().map(List::copyOf).toArray(List[]::new);
  }

  public static RaptorTransferIndex create(
    List<List<Transfer>> transfersByStopIndex,
    RoutingContext routingContext
  ) {
    var count = transfersByStopIndex.stream().mapToInt(Collection::size).sum();

    var progress = ProgressTracker.track("Creating new raptor transfer index", 1000, count);

    LOG.info(progress.startMessage());

    var forwardTransfers = new ArrayList<List<RaptorTransfer>>(transfersByStopIndex.size());
    var reversedTransfers = new ArrayList<List<RaptorTransfer>>(transfersByStopIndex.size());

    for (int i = 0; i < transfersByStopIndex.size(); i++) {
      forwardTransfers.add(Collections.synchronizedList(new ArrayList<>()));
      reversedTransfers.add(Collections.synchronizedList(new ArrayList<>()));
    }

    // The transfers are filtered so that there is only one possible directional transfer
    // for a stop pair.
    var stream = IntStream.range(0, transfersByStopIndex.size());
    if (OTPFeature.ParallelRouting.isOn()) {
      stream = stream.parallel();
    }

    stream.forEach(fromStop -> {
      var stopStream = transfersByStopIndex.get(fromStop).stream();

      if (OTPFeature.ParallelRouting.isOn()) {
        stopStream = stopStream.parallel();
      }

      var transfers = stopStream
        .flatMap(s -> s.asRaptorTransfer(routingContext).stream())
        .collect(
          toMap(
            RaptorTransfer::stop,
            Function.identity(),
            (a, b) -> a.generalizedCost() < b.generalizedCost() ? a : b
          )
        )
        .values();

      forwardTransfers.get(fromStop).addAll(transfers);

      for (RaptorTransfer forwardTransfer : transfers) {
        reversedTransfers
          .get(forwardTransfer.stop())
          .add(new ReversedRaptorTransfer(fromStop, forwardTransfer));
      }
      //noinspection Convert2MethodRef
      progress.steps(transfersByStopIndex.get(fromStop).size(), message -> LOG.info(message));
    });

    LOG.info(progress.completeMessage());

    return new RaptorTransferIndex(forwardTransfers, reversedTransfers);
  }

  public List<RaptorTransfer> getForwardTransfers(int stopIndex) {
    return forwardTransfers[stopIndex];
  }

  public List<RaptorTransfer> getReversedTransfers(int stopIndex) {
    return reversedTransfers[stopIndex];
  }
}
