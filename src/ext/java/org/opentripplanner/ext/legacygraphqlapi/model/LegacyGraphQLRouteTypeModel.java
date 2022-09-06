package org.opentripplanner.ext.legacygraphqlapi.model;

import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.organization.Agency;

/**
 * Class for route types. If agency is defined, the object is the route for the specific agency.
 */
public class LegacyGraphQLRouteTypeModel {

  /**
   * If defined, this is the route type is only relevant for the agency.
   */
  private final Agency agency;

  /**
   * Route type (GTFS).
   */
  private final SubMode subMode;

  /**
   * Route type only covers routes of this feed.
   */
  private final String feedId;

  public LegacyGraphQLRouteTypeModel(Agency agency, SubMode subMode, String feedId) {
    this.agency = agency;
    this.subMode = subMode;
    this.feedId = feedId;
  }

  public Agency getAgency() {
    return agency;
  }

  public SubMode getRouteType() {
    return subMode;
  }

  public String getFeedId() {
    return feedId;
  }
}
