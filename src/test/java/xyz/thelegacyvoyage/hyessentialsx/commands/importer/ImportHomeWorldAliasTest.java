package xyz.thelegacyvoyage.hyessentialsx.commands.importer;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImportHomeWorldAliasTest {

    private static final Gson GSON = new Gson();
    private static final String HOME_TYPE =
            "xyz.thelegacyvoyage.hyessentialsx.commands.importer.ImportHomesCommand$ImportHome";

    @Test
    void acceptsBothWorldUuidSpellings() throws Exception {
        Class<?> homeType = Class.forName(HOME_TYPE);
        Object preferred = GSON.fromJson("{\"worldUuid\":\"preferred\"}", homeType);
        Object legacy = GSON.fromJson("{\"worldUUID\":\"legacy\"}", homeType);

        assertEquals("preferred", readField(preferred, "worldUuid"));
        assertNull(readField(preferred, "legacyWorldUuid"));
        assertEquals("legacy", readField(legacy, "legacyWorldUuid"));
        assertNull(readField(legacy, "worldUuid"));
    }

    @Test
    void keepsBothValuesWhenBothWorldUuidSpellingsArePresent() throws Exception {
        Class<?> homeType = Class.forName(HOME_TYPE);
        Object home = GSON.fromJson("{\"worldUUID\":\"legacy\",\"worldUuid\":\"preferred\"}", homeType);

        assertEquals("preferred", readField(home, "worldUuid"));
        assertEquals("legacy", readField(home, "legacyWorldUuid"));
    }

    private static Object readField(Object home, String name) throws Exception {
        Field field = home.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(home);
    }
}
