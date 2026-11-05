package com.horizonkk.itemAction.util;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class NbtUtilsTest {
    private static Method nestedReader;

    @BeforeAll
    static void initializeReader() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            Class.forName(NbtUtils.class.getName());
        }
        nestedReader = NbtUtils.class.getDeclaredMethod("getNestedString", Object.class, String.class);
        nestedReader.setAccessible(true);
    }

    @ParameterizedTest(name = "optionalGetters={0}")
    @ValueSource(booleans = {false, true})
    void readsRootString(boolean optionalGetters) throws Exception {
        Object tag = compound(optionalGetters, Map.of("itemaction", "功法"));
        assertEquals("功法", read(tag, "itemaction"));
    }

    @ParameterizedTest(name = "optionalGetters={0}")
    @ValueSource(booleans = {false, true})
    void readsPublicBukkitValues(boolean optionalGetters) throws Exception {
        Object pdc = compound(optionalGetters, Map.of("mythicmobs:type", "功法"));
        Object tag = compound(optionalGetters, Map.of("PublicBukkitValues", pdc));
        assertEquals("功法", read(tag, "PublicBukkitValues.mythicmobs:type"));
    }

    @ParameterizedTest(name = "optionalGetters={0}")
    @ValueSource(booleans = {false, true})
    void readsMultipleNestedCompounds(boolean optionalGetters) throws Exception {
        Object inner = compound(optionalGetters, Map.of("itemaction", "功法"));
        Object outer = compound(optionalGetters, Map.of("inner", inner));
        Object tag = compound(optionalGetters, Map.of("outer", outer));
        assertEquals("功法", read(tag, "outer.inner.itemaction"));
    }

    @ParameterizedTest(name = "optionalGetters={0}")
    @ValueSource(booleans = {false, true})
    void missingTagsReturnNull(boolean optionalGetters) throws Exception {
        Object child = compound(optionalGetters, Map.of());
        Object tag = compound(optionalGetters, Map.of("child", child));
        assertNull(read(tag, "missing"));
        assertNull(read(tag, "child.missing"));
        assertNull(read(tag, "missing.itemaction"));
        assertNull(read(tag, "missing.inner.itemaction"));
    }

    @ParameterizedTest(name = "optionalGetters={0}")
    @ValueSource(booleans = {false, true})
    void emptyStringsReturnNull(boolean optionalGetters) throws Exception {
        Object child = compound(optionalGetters, Map.of("itemaction", ""));
        Object tag = compound(optionalGetters, Map.of("itemaction", "", "child", child));
        assertNull(read(tag, "itemaction"));
        assertNull(read(tag, "child.itemaction"));
    }

    @ParameterizedTest(name = "optionalGetters={0}")
    @ValueSource(booleans = {false, true})
    void wrongTagTypesReturnNull(boolean optionalGetters) throws Exception {
        Object child = compound(optionalGetters, Map.of("itemaction", 42));
        Object tag = compound(optionalGetters, Map.of("itemaction", 42, "child", child));
        assertNull(read(tag, "itemaction"));
        assertNull(read(tag, "child.itemaction"));
        assertNull(read(tag, "itemaction.nested"));
    }

    private static String read(Object tag, String path) throws Exception {
        return (String) nestedReader.invoke(null, tag, path);
    }

    private static Object compound(boolean optionalGetters, Map<String, Object> values) {
        return optionalGetters ? new OptionalCompound(values) : new LegacyCompound(values);
    }

    // Match the reflective getter signatures before Minecraft 1.21.5.
    public static final class LegacyCompound {
        private final Map<String, Object> values;

        LegacyCompound(Map<String, Object> values) {
            this.values = values;
        }

        public String getString(String key) {
            return values.get(key) instanceof String value ? value : "";
        }

        public LegacyCompound getCompound(String key) {
            return values.get(key) instanceof LegacyCompound value ? value : new LegacyCompound(Map.of());
        }
    }

    // Match Minecraft 1.21.5+ (including 1.21.11) Optional-returning getters.
    public static final class OptionalCompound {
        private final Map<String, Object> values;

        OptionalCompound(Map<String, Object> values) {
            this.values = values;
        }

        public Optional<String> getString(String key) {
            return values.get(key) instanceof String value ? Optional.of(value) : Optional.empty();
        }

        public Optional<OptionalCompound> getCompound(String key) {
            return values.get(key) instanceof OptionalCompound value ? Optional.of(value) : Optional.empty();
        }
    }
}
