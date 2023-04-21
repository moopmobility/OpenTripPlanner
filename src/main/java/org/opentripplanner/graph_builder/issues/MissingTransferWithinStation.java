package org.opentripplanner.graph_builder.issues;

import java.util.Base64;
import java.util.List;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.framework.geometry.CoordinateArrayListSequence;
import org.opentripplanner.framework.geometry.GeometryUtils;
import org.opentripplanner.framework.geometry.PolylineEncoder;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;
import org.opentripplanner.street.model.edge.Edge;

public class MissingTransferWithinStation implements DataImportIssue {

  private final String message;
  private final String htmlMessage;

  public MissingTransferWithinStation(
    String stationName,
    WgsCoordinate stationCoordinate,
    String fromPlatform,
    WgsCoordinate fromCoordinate,
    String toPlatform,
    WgsCoordinate toCoordinate
  ) {
    message =
      "Missing transfer within station %s: %s -> %s".formatted(
          stationName,
          fromPlatform,
          toPlatform
        );

    var geometry = GeometryUtils.makeLineString(
      List.of(fromCoordinate.asJtsCoordinate(), toCoordinate.asJtsCoordinate())
    );
    var polyline = PolylineEncoder.encodeGeometry(geometry);
    var encoded = Base64.getEncoder().encodeToString(polyline.points().getBytes());

    var link =
      "<a href='https://leonard.io/polyline-visualiser/?base64=%s' target='_blank'>map</a>".formatted(
          encoded
        );

    htmlMessage =
      "Missing transfer within station <a href='https://www.openstreetmap.org/#map=17/%s/%s' target='_blank'>%s</a>: %s -> %s (%s)".formatted(
          stationCoordinate.latitude(),
          stationCoordinate.longitude(),
          stationName,
          fromPlatform,
          toPlatform,
          link
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
