package org.opentripplanner.gtfs.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.transit.model.basic.TransitMode.*;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.opentripplanner.test.support.VariableSource;
import org.opentripplanner.transit.model.basic.TransitMode;

class TransitModeMapperTest {

  static Stream<Arguments> testCases = Stream.of(
    Arguments.of(1501, TAXI1),
    Arguments.of(1502, TAXI2),
    Arguments.of(1551, CARPOOL),
    Arguments.of(1555, CARPOOL),
    Arguments.of(1560, CARPOOL)
  );

  @ParameterizedTest(name = "{0} should map to {1}")
  @VariableSource("testCases")
  void map(int mode, TransitMode expectedMode) {
    assertEquals(expectedMode, TransitModeMapper.mapMode(mode));
  }
}
