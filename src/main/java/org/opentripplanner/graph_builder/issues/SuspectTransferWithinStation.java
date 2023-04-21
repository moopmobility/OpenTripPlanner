package org.opentripplanner.graph_builder.issues;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.framework.geometry.CoordinateArrayListSequence;
import org.opentripplanner.framework.geometry.GeometryUtils;
import org.opentripplanner.framework.geometry.PolylineEncoder;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;
import org.opentripplanner.street.model.edge.Edge;

public class SuspectTransferWithinStation implements DataImportIssue {

  private final String message;
  private final String htmlMessage;

  public SuspectTransferWithinStation(
    String stationName,
    WgsCoordinate stationCoordinate,
    String fromPlatform,
    String toPlatform,
    boolean nonAccessible,
    double length,
    double shortTransferLimit,
    Stream<List<Edge>> edgeGroups
  ) {
    var longTransfer = length >= shortTransferLimit;
    var missingType = longTransfer && nonAccessible
      ? "short and accessible"
      : longTransfer ? "short" : "accessible";

    message =
      "Missing %s transfer within station %s: %s -> %s (%s m)".formatted(
          missingType,
          stationName,
          fromPlatform,
          toPlatform,
          length
        );
    var links = edgeGroups
      .map(edges -> {
        var geometry = makeCoordinates(edges);
        var polyline = PolylineEncoder.encodeGeometry(geometry);
        var encoded = Base64.getEncoder().encodeToString(polyline.points().getBytes());

        return "<a href='https://leonard.io/polyline-visualiser/?base64=%s' target='_blank'>map</a>".formatted(
            encoded
          );
      })
      .collect(Collectors.joining(", "));

    htmlMessage =
      "Missing %s transfer within station <a href='https://www.openstreetmap.org/#map=17/%s/%s' target='_blank'>%s</a>: %s -> %s (%s m) (%s)".formatted(
          missingType,
          stationCoordinate.latitude(),
          stationCoordinate.longitude(),
          stationName,
          fromPlatform,
          toPlatform,
          length,
          links
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

  private static LineString makeCoordinates(List<Edge> edges) {
    CoordinateArrayListSequence coordinates = new CoordinateArrayListSequence();

    for (Edge edge : edges) {
      LineString geometry = edge.getGeometry();

      if (geometry != null) {
        if (coordinates.size() == 0) {
          coordinates.extend(geometry.getCoordinates());
        } else {
          coordinates.extend(geometry.getCoordinates(), 1); // Avoid duplications
        }
      }
    }

    return GeometryUtils.getGeometryFactory().createLineString(coordinates);
  }
}
