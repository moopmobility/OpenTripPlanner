package org.opentripplanner.graph_builder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** THIS CLASS IS THREAD-SAFE */
class IssueColors {

  /**
   * We use a concurrent hash map for thread safety. It is not necessary since the report writer is
   * not writing files in parallel, but it make this class thread-safe in case we want to speed up
   * the issue report generation.
   */
  private static final Map<String, Integer> ASSIGNED_COLOR = new ConcurrentHashMap<>();

  /**
   * List of back-ground-colors, should work with black text on top. The order should give dissent
   * contract for two neighboring pairs.
   */
  private static final Integer[] BG_COLORS = {
    0xFFFF80,
    0xFFD0FF,
    0x90C8FF,
    0xFFE060,
    0xB0FF40,
    0xFF80FF,
    0x70A0FF,
    0xFFC000,
    0x80FF40,
    0xFFB0FF,
    0x90E0FF,
    0xFFA000,
    0xE0FF70,
    0xD0A0FF,
    0xB0FFFF,
    0xFF8080,
    0x40FF40,
    0xA090FF,
    0x90FFE0,
    0xFF60B0,
    0x70FFB0,
    0xFFFF40,
  };

  /** Get and return color a in hex format: {@code "#FF00FF"} */
  static String rgb(String issueType) {
    // The '& 0xFFFFFF' is needed to remove the alpha value
    return String.format("#%06X", backgroundColor(issueType));
  }

  private static Integer backgroundColor(String issueType) {
    return ASSIGNED_COLOR.computeIfAbsent(issueType, key -> nextColor());
  }

  private static Integer nextColor() {
    // Use modulo to start over if the number of issues is larger then the list of colors
    return BG_COLORS[ASSIGNED_COLOR.size() % BG_COLORS.length];
  }
}
