package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.placeholder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderIntegrationTokenTest {
    @Test
    void detectsBothTokenFormatsIncludingNestedOpenersAndEmptyPrefixes() {
        for (String text : new String[]{"{player}", "Welcome %player_name%!", "{}{world}",
                "%%player%", "{{}", "prefix {a%b} suffix", "%{player}%"}) {
            assertTrue(PlaceholderIntegration.containsPlaceholderTokens(text), text);
        }
        for (String text : new String[]{"", "plain text", "{}", "%%", "{unfinished", "%unfinished", "}closed"}) {
            assertFalse(PlaceholderIntegration.containsPlaceholderTokens(text), text);
        }
    }

    @Test
    void preservesWholeLineMatchingWithLineBreaksInsideTokensOnly() {
        for (char separator : new char[]{'\n', '\r', '\u0085', '\u2028', '\u2029'}) {
            assertTrue(PlaceholderIntegration.containsPlaceholderTokens("prefix {a" + separator + "b} suffix"));
            assertTrue(PlaceholderIntegration.containsPlaceholderTokens("prefix %a" + separator + "b% suffix"));
            assertFalse(PlaceholderIntegration.containsPlaceholderTokens(separator + "{player}"));
            assertFalse(PlaceholderIntegration.containsPlaceholderTokens("%player%" + separator));
            assertFalse(PlaceholderIntegration.containsPlaceholderTokens("{a" + separator + "b}" + separator));
        }
    }

    @Test
    @Timeout(5)
    void handlesLargeMalformedAndEmptyTokensWithoutBacktracking() {
        assertFalse(PlaceholderIntegration.containsPlaceholderTokens("{".repeat(250_000)));
        assertFalse(PlaceholderIntegration.containsPlaceholderTokens("{}".repeat(125_000)));
        assertFalse(PlaceholderIntegration.containsPlaceholderTokens("%".repeat(250_000)));
        assertTrue(PlaceholderIntegration.containsPlaceholderTokens("{}".repeat(125_000) + "%player%"));
    }
}
