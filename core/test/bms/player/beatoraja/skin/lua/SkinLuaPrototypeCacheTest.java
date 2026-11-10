package bms.player.beatoraja.skin.lua;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.DumpState;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.*;

class SkinLuaPrototypeCacheTest {
    static { LuaValue.valueOf(0); }
    @TempDir Path directory;

    @Test void recompilationIsSkippedButExecutionTablesAndUpvaluesAreFresh() {
        var cache = cache(4, 8192, 8192, 4096);
        var calls = new AtomicInteger();
        Globals globals = countingGlobals(cache, calls);
        globals.set("marker", 10);
        String source = "runs=(runs or 0)+1; local n=marker; return {next=function() n=n+1; return n end}";
        LuaValue first = load(globals, source).call();
        assertEquals(11, first.get("next").call().toint());
        globals.set("marker", 20);
        LuaValue second = load(globals, source).call();
        assertNotSame(first, second);
        assertEquals(21, second.get("next").call().toint());
        assertEquals(12, first.get("next").call().toint());
        assertEquals(2, globals.get("runs").toint());
        assertEquals(1, calls.get());
    }

    @Test void everyNewSkinExecutesRequiredModulesAndChangedContentIsObserved() throws Exception {
        Path module = directory.resolve("fixture.lua");
        Files.writeString(module, "return {value=1}");
        var first = new SkinLuaAccessor(false);
        first.setDirectory(directory);
        assertEquals(1, first.exec("return require('fixture').value").toint());
        first.exec("require('fixture').value=99");
        var second = new SkinLuaAccessor(false);
        second.setDirectory(directory);
        assertEquals(1, second.exec("return require('fixture').value").toint());
        var stamp = Files.getLastModifiedTime(module);
        Files.writeString(module, "return {value=2}");
        Files.setLastModifiedTime(module, stamp); // Same name, size and mtime still must not return stale code.
        var third = new SkinLuaAccessor(false);
        third.setDirectory(directory);
        assertEquals(2, third.exec("return require('fixture').value").toint());
        assertEquals(99, first.exec("return require('fixture').value").toint());
    }

    @Test void entryAndSourceAndPrototypeBudgetsEvictOrBypass() {
        var calls = new AtomicInteger();
        Globals globals = countingGlobals(cache(1, 8192, 8192, 4096), calls);
        load(globals, "return 1"); load(globals, "return 2"); load(globals, "return 1");
        assertEquals(3, calls.get());
        calls.set(0);
        globals = countingGlobals(cache(8, 15, 8192, 4096), calls);
        load(globals, "return 1"); load(globals, "return 2"); load(globals, "return 1");
        assertEquals(3, calls.get());
        calls.set(0);
        globals = countingGlobals(cache(8, 8192, 1, 4096), calls);
        load(globals, "return 1"); load(globals, "return 1");
        assertEquals(2, calls.get());
    }

    @Test void oversizedStreamsAreCompiledInFullWithoutCachingOrClosingTheCallerStream() throws Exception {
        var calls = new AtomicInteger();
        Globals globals = countingGlobals(cache(4, 8192, 8192, 8), calls);
        for (int i = 0; i < 2; i++) {
            var input = new ByteArrayInputStream("local n=41; return n+1".getBytes(StandardCharsets.UTF_8)) {
                @Override public void close() { fail("compiler closed caller-owned stream"); }
            };
            assertEquals(42, new LuaClosure(globals.compiler.compile(input, "@large"), globals).call().toint());
        }
        assertEquals(2, calls.get());
    }

    @Test void prototypeBudgetIncludesStringBackingStorageInsteadOfOnlyTheVisibleSlice() throws Exception {
        var calls = new AtomicInteger();
        Globals globals = JsePlatform.standardGlobals();
        globals.compiler = (input, name) -> {
            calls.incrementAndGet();
            var prototype = new org.luaj.vm2.Prototype();
            prototype.k = new LuaValue[]{org.luaj.vm2.LuaString.valueUsing(new byte[8192], 0, 1)};
            return prototype;
        };
        cache(8, 8192, 1024, 4096).wrapCompiler(globals);
        for (int i = 0; i < 2; i++) {
            globals.compiler.compile(new ByteArrayInputStream(new byte[]{1}), "@slice");
        }
        assertEquals(2, calls.get(), "oversized backing storage must bypass the cache");
    }

    @Test void syntaxAndReadErrorsAreNotCachedAndChunkNamesRemainDistinct() throws Exception {
        var calls = new AtomicInteger();
        Globals globals = countingGlobals(cache(8, 8192, 8192, 4096), calls);
        assertThrows(LuaError.class, () -> load(globals, "local ="));
        assertThrows(LuaError.class, () -> load(globals, "local ="));
        assertEquals(2, calls.get());
        assertThrows(IOException.class, () -> globals.compiler.compile(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("fixture read error"); }
        }, "@broken"));
        assertEquals(42, load(globals, "return 42").call().toint());
        globals.load("return 42", "@different-name");
        assertEquals(4, calls.get());
    }

    @Test void binaryChunksStillUseTheOriginalUndumper() throws Exception {
        var calls = new AtomicInteger();
        Globals globals = countingGlobals(cache(8, 8192, 8192, 4096), calls);
        var closure = (LuaClosure) load(globals, "return 42");
        var bytes = new ByteArrayOutputStream();
        DumpState.dump(closure.p, bytes, false);
        assertEquals(42, globals.load(new ByteArrayInputStream(bytes.toByteArray()), "@binary", "b", globals).call().toint());
        assertEquals(1, calls.get());
    }

    private static SkinLuaPrototypeCache cache(int entries, long source, long compiled, int chunk) {
        return new SkinLuaPrototypeCache(entries, source, compiled, chunk);
    }

    private static Globals countingGlobals(SkinLuaPrototypeCache cache, AtomicInteger calls) {
        Globals globals = JsePlatform.standardGlobals();
        Globals.Compiler original = globals.compiler;
        globals.compiler = (input, name) -> { calls.incrementAndGet(); return original.compile(input, name); };
        cache.wrapCompiler(globals);
        return globals;
    }

    private static LuaValue load(Globals globals, String source) {
        return globals.load(source, "@fixture");
    }
}
