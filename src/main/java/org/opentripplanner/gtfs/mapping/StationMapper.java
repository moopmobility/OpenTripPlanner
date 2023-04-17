package org.opentripplanner.gtfs.mapping;

import static org.opentripplanner.gtfs.mapping.AgencyAndIdMapper.mapAgencyAndId;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issues.ParentGroupOfStationsNotFound;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StationBuilder;
import org.opentripplanner.transit.model.site.StopTransferPriority;

/**
 * Responsible for mapping GTFS Stop into the OTP model.
 * <p>
 * NOTE! This class has state. This class also holds a index of all mapped stops to avoid mapping
 * the same stop twice. We do this because the library (onebusaway) return transfers with Stop
 * object references, not stop ids. Instead of looking up the Stops by id in the {@link
 * TransferMapper} we just use the this class to cache stops. This way, the order of which stops and
 * transfers are mapped does not matter.
 */
class StationMapper {

  /** @see StationMapper (this class JavaDoc) for way we need this. */
  private final Map<org.onebusaway.gtfs.model.Stop, Station> mappedStops = new HashMap<>();

  private final TranslationHelper translationHelper;
  private final StopTransferPriority stationTransferPreference;
  private final Function<FeedScopedId, Station> stationLookup;
  private final DataImportIssueStore issueStore;

  StationMapper(
    TranslationHelper translationHelper,
    StopTransferPriority stationTransferPreference,
    Function<FeedScopedId, Station> stationLookup,
    DataImportIssueStore issueStore
  ) {
    this.translationHelper = translationHelper;
    this.stationTransferPreference = stationTransferPreference;
    this.stationLookup = stationLookup;
    this.issueStore = issueStore;
  }

  /** Map from GTFS to OTP model, {@code null} safe. */
  Station map(org.onebusaway.gtfs.model.Stop orginal) {
    return orginal == null ? null : mappedStops.computeIfAbsent(orginal, this::doMap);
  }

  public void mapGroupOfStations(org.onebusaway.gtfs.model.Stop rhs) {
    var station = stationLookup.apply(mapAgencyAndId(rhs.getId()));
    var parentStation = stationLookup.apply(
      new FeedScopedId(rhs.getId().getAgencyId(), rhs.getParentStation())
    );

    if (parentStation != null) {
      parentStation.addChildStation(station);
    } else {
      issueStore.add(new ParentGroupOfStationsNotFound(station, rhs.getParentStation()));
    }
  }

  private Station doMap(org.onebusaway.gtfs.model.Stop rhs) {
    if (rhs.getLocationType() != org.onebusaway.gtfs.model.Stop.LOCATION_TYPE_STATION) {
      throw new IllegalArgumentException(
        "Expected type " +
        org.onebusaway.gtfs.model.Stop.LOCATION_TYPE_STATION +
        ", but got " +
        rhs.getLocationType()
      );
    }
    StationBuilder builder = Station
      .of(mapAgencyAndId(rhs.getId()))
      .withCoordinate(WgsCoordinateMapper.mapToDomain(rhs))
      .withCode(rhs.getCode());

    builder.withName(
      translationHelper.getTranslation(
        org.onebusaway.gtfs.model.Stop.class,
        "name",
        rhs.getId().getId(),
        rhs.getName()
      )
    );

    builder.withDescription(
      translationHelper.getTranslation(
        org.onebusaway.gtfs.model.Stop.class,
        "desc",
        rhs.getId().getId(),
        rhs.getDesc()
      )
    );

    builder.withUrl(
      translationHelper.getTranslation(
        org.onebusaway.gtfs.model.Stop.class,
        "url",
        rhs.getId().getId(),
        rhs.getUrl()
      )
    );

    builder.withPriority(stationTransferPreference);

    if (rhs.getTimezone() != null) {
      builder.withTimezone(ZoneId.of(rhs.getTimezone()));
    }
    return builder.build();
  }
}
