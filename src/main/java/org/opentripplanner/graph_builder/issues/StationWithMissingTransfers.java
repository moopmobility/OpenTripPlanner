package org.opentripplanner.graph_builder.issues;

import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;

public class StationWithMissingTransfers implements DataImportIssue {

  private final String message;
  private final String htmlMessage;

  public StationWithMissingTransfers(
    String stationName,
    WgsCoordinate stationCoordinate,
    int childStopCount,
    long nokTransfers
  ) {
    message =
      "Station %s with %s child stops has missing transfers (%s / %s)".formatted(
          stationName,
          childStopCount,
          nokTransfers,
          nokTransfers * (nokTransfers - 1)
        );

    htmlMessage =
      "Station <a href='https://www.openstreetmap.org/#map=17/%s/%s' target='_blank'>%s</a> with %s child stops has missing transfers (%s / %s)".formatted(
          stationCoordinate.latitude(),
          stationCoordinate.longitude(),
          stationName,
          childStopCount,
          nokTransfers,
          childStopCount * (childStopCount - 1)
        );
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String getHTMLMessage() {
    return htmlMessage;
  }
}
