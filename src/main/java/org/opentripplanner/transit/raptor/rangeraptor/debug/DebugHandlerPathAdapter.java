package org.opentripplanner.transit.raptor.rangeraptor.debug;

import java.util.List;
import org.opentripplanner.transit.raptor.api.path.Path;
import org.opentripplanner.transit.raptor.api.request.DebugRequest;
import org.opentripplanner.transit.raptor.rangeraptor.internalapi.WorkerLifeCycle;

final class DebugHandlerPathAdapter extends AbstractDebugHandlerAdapter<Path<?>> {

  DebugHandlerPathAdapter(DebugRequest debug, WorkerLifeCycle lifeCycle) {
    super(debug, debug.pathFilteringListener(), lifeCycle);
  }

  @Override
  protected int stop(Path<?> path) {
    // A dummyPath() was constructed, ignore
    if (path.accessLeg() == null) {
      return Integer.MIN_VALUE;
    }
    return path.egressLeg() != null ? path.egressLeg().fromStop() : 0;
  }

  @Override
  protected List<Integer> stopsVisited(Path<?> path) {
    return path.listStops();
  }
}
