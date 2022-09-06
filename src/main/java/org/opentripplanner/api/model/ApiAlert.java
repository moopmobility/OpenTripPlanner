package org.opentripplanner.api.model;

import java.time.Instant;

public class ApiAlert {

  public String alertHeaderText;
  public String alertDescriptionText;
  public String alertUrl;
  /** null means unknown */
  public Instant effectiveStartDate;
  public Instant effectiveEndDate;
}
