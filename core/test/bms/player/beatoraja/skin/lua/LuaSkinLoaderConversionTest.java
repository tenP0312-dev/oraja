package bms.player.beatoraja.skin.lua;

import bms.player.beatoraja.skin.json.JsonSkin;
import bms.player.beatoraja.skin.property.IntegerProperty;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.ZeroArgFunction;

import static org.junit.jupiter.api.Assertions.*;

class LuaSkinLoaderConversionTest {
    static { LuaValue.valueOf(0); }
    public static class Parent { public int inherited = 7; public int hidden = 8; }
    public static class Definition extends Parent { public int hidden = 9; public int value = 10; public IntegerProperty callback; }

    @Test void metadataReusePreservesDefaultsInheritanceUnknownKeysAndLiveCallbacks() throws Exception {
        var loader = new LuaSkinLoader();
        var table = new LuaTable();
        table.set("value", 42); table.set("inherited", 43); table.set("hidden", 44); table.set("unknown", "ignored");
        int[] counter = {0};
        table.set("callback", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(++counter[0]); }
        });
        Definition first = loader.fromLuaValue(Definition.class, table);
        assertEquals(42, first.value);
        assertEquals(43, first.inherited);
        var hidden = java.util.Arrays.stream(ClassReflection.getFields(Definition.class))
                .filter(field -> field.getName().equals("hidden")).findFirst().orElseThrow();
        assertEquals(44, hidden.get(first));
        assertEquals(1, first.callback.get(null));
        assertEquals(2, first.callback.get(null));
        table.set("value", 99);
        Definition second = loader.fromLuaValue(Definition.class, table);
        assertNotSame(first, second);
        assertEquals(42, first.value);
        assertEquals(99, second.value);
        assertEquals(3, second.callback.get(null));
        Definition empty = loader.fromLuaValue(Definition.class, new LuaTable());
        assertEquals(10, empty.value);
        assertEquals(7, empty.inherited);
    }

    @Test void nonStringKeysKeepTheirOriginalStringConversionAndArrayEnumerationOrder() {
        var loader = new LuaSkinLoader();
        var table = new LuaTable();
        table.set(new LuaUserdata(new Object()) {
            @Override public String tojstring() { return "value"; }
        }, LuaValue.valueOf(77));
        assertEquals(77, loader.fromLuaValue(Definition.class, table).value);
        var array = new LuaTable();
        array.set(3, "three"); array.set(1, "one"); array.set("extra", "extra");
        LuaValue[] keys = array.keys();
        String[] converted = loader.fromLuaValue(String[].class, array);
        assertEquals(keys.length, converted.length);
        for (int i = 0; i < keys.length; i++) assertEquals(array.get(keys[i]).tojstring(), converted[i]);
    }

    @Test void repeatedLargeDefinitionConversionDoesNotRecreateReflectionMetadata() {
        var platform = ManagementFactory.getThreadMXBean();
        Assumptions.assumeTrue(platform instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) platform;
        Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var loader = new LuaSkinLoader();
        LuaTable input = fixture();
        for (int i = 0; i < 30; i++) loader.fromLuaValue(JsonSkin.Destination[].class, input);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        JsonSkin.Destination[] result = loader.fromLuaValue(JsonSkin.Destination[].class, input);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertEquals(100, result.length);
        assertEquals(64, result[99].dst[63].x);
        // Old conversion allocates over 16 MB for this fixture; allow headroom for JVM/JIT differences.
        assertTrue(allocated < 8 * 1024 * 1024, "conversion allocated " + allocated + " bytes");
    }

    private static LuaTable fixture() {
        LuaTable all = new LuaTable();
        for (int i = 1; i <= 100; i++) {
            LuaTable destination = new LuaTable(), animations = new LuaTable();
            destination.set("id", "fixture-" + i);
            destination.set("blend", i % 4); destination.set("loop", 0); destination.set("dst", animations);
            for (int j = 1; j <= 64; j++) {
                LuaTable animation = new LuaTable();
                for (String key : new String[]{"time", "x", "y", "w", "h", "acc", "a", "r", "g", "b", "angle"}) {
                    animation.set(key, j);
                }
                animations.set(j, animation);
            }
            all.set(i, destination);
        }
        return all;
    }
}
