package org.opentripplanner.graph_builder.issues;

import org.opentripplanner.graph_builder.issue.api.DataImportIssue;
import org.opentripplanner.transit.model.site.Station;

public class ParentGroupOfStationsNotFound implements DataImportIssue {

  public static final String FMT =
    "Parent group-of-stations %s not found. Stop %s will not be linked to a " +
    "parent group-of-stations.";

  final String parentStop;

  final Station station;

  public ParentGroupOfStationsNotFound(Station station, String parentStop) {
    this.station = station;
    this.parentStop = parentStop;
  }

  @Override
  public String getMessage() {
    return String.format(FMT, parentStop, station);
  }
}
