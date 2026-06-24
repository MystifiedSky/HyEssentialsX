package xyz.thelegacyvoyage.hyessentialsx.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilTest {

    @Test
    void parseDurationAcceptsFormattedMultiPartOutput() {
        assertEquals(3600L, TimeUtil.parseDurationSeconds("1h 0m 0s"));
        assertEquals(604800L, TimeUtil.parseDurationSeconds("1w 0d 0h 0m 0s"));
        assertEquals(2678400L, TimeUtil.parseDurationSeconds("1mo 1d"));
    }

    @Test
    void parseDurationStillAcceptsSingleTokenInput() {
        assertEquals(60L, TimeUtil.parseDurationSeconds("1m"));
        assertEquals(7200L, TimeUtil.parseDurationSeconds("2h"));
        assertEquals(0L, TimeUtil.parseDurationSeconds("0"));
    }
}
