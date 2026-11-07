package bms.player.beatoraja.skin.lua;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Lua skins must not run programs or reach files outside their own skin directory. */
class SkinLuaSandboxTest {
    @TempDir Path temp;

    private SkinLuaAccessor accessor(Path skinDirectory) {
        SkinLuaAccessor lua = new SkinLuaAccessor(false);
        lua.setDirectory(skinDirectory);
        return lua;
    }

    private Path skinDirectory() throws Exception {
        return Files.createDirectories(temp.resolve("skin").resolve("sample"));
    }

    @Test void osCommandAndEnvironmentFunctionsAreRemoved() throws Exception {
        SkinLuaAccessor lua = accessor(skinDirectory());
        for (String name : new String[] { "execute", "exit", "getenv", "remove", "rename", "tmpname" }) {
            assertTrue(lua.exec("return os." + name).isnil(), "os." + name + " must be removed");
        }
        // Clocks and dates used by ordinary skins keep working.
        assertTrue(lua.exec("return os.time()").isnumber());
        assertTrue(lua.exec("return os.clock()").isnumber());
        assertTrue(lua.exec("return os.date('%Y')").isstring());
        assertTrue(lua.exec("return os.difftime(2, 1)").isnumber());
    }

    @Test void popenAndNativeLoadersAreUnavailable() throws Exception {
        SkinLuaAccessor lua = accessor(skinDirectory());
        Path marker = temp.resolve("popen-ran");
        LuaValue popen;
        try {
            popen = lua.exec("return io.popen([[touch " + marker + "]])");
        } catch (LuaError e) {
            popen = LuaValue.NIL;
        }
        assertTrue(popen.isnil(), "io.popen must not return a process handle");
        Thread.sleep(300);
        assertFalse(Files.exists(marker), "io.popen must not run a program");
        assertTrue(lua.exec("return package.loadlib").isnil());
        assertEquals("", lua.exec("return package.cpath").tojstring());
        assertTrue(lua.exec("return package.searchers[3]").isnil());
        assertTrue(lua.exec("return package.searchers[4]").isnil());
    }

    @Test void javaReflectionIsLimitedToTheLegacyFacades() throws Exception {
        SkinLuaAccessor lua = accessor(skinDirectory());
        for (String className : new String[] { "java.lang.Runtime", "java.lang.ProcessBuilder",
                "java.lang.System", "java.lang.Class", "java.nio.file.Files" }) {
            assertThrows(LuaError.class, () -> lua.exec("return luajava.bindClass('" + className + "')"),
                    className);
            assertThrows(LuaError.class, () -> lua.exec("return luajava.newInstance('" + className + "')"),
                    className);
        }
        // Old skins that read the screen size through luajava keep loading.
        assertTrue(lua.exec("return luajava.bindClass('com.badlogic.gdx.Gdx').graphics").istable());
        // debug only keeps getmetatable.
        assertTrue(lua.exec("return debug.sethook").isnil());
        assertTrue(lua.exec("return debug.getmetatable").isfunction());
    }

    @Test void fileAccessStaysInsideTheSkinDirectory() throws Exception {
        Path skin = skinDirectory();
        Files.writeString(skin.resolve("data.txt"), "inside");
        Path outside = Files.writeString(temp.resolve("secret.txt"), "outside");
        SkinLuaAccessor lua = accessor(skin);

        assertEquals("inside", lua.exec("local f = io.open('data.txt'); local s = f:read('*a'); f:close(); return s").tojstring());
        assertEquals("inside", lua.exec("local f = io.open([[" + skin.resolve("data.txt") + "]]); local s = f:read('*a'); f:close(); return s").tojstring());

        LuaValue denied = lua.exec("return io.open([[" + outside + "]])");
        assertTrue(denied.isnil(), "absolute path outside the skin must be refused");
        assertTrue(lua.exec("return io.open('../../secret.txt')").isnil(), "parent traversal must be refused");

        lua.exec("local f = io.open('out/written.txt', 'w'); f:write('ok'); f:close()");
        assertEquals("ok", Files.readString(skin.resolve("out").resolve("written.txt")));
        assertTrue(lua.exec("return io.open([[" + temp.resolve("escape.txt") + "]], 'w')").isnil());
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }

    @Test void symbolicLinksInsideTheSkinAreNotFollowed() throws Exception {
        Path skin = skinDirectory();
        Path outside = Files.writeString(temp.resolve("secret.txt"), "outside");
        Path link = skin.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException e) {
            assumeTrue(false, "symbolic links are not available: " + e);
        }
        SkinLuaAccessor lua = accessor(skin);
        assertTrue(lua.exec("return io.open('link.txt')").isnil());
        assertThrows(LuaError.class,
                () -> lua.exec("return luajava.newInstance('java.io.File', 'link.txt'):isFile()"));
    }

    @Test void requireStillLoadsModulesFromTheSkinDirectory() throws Exception {
        Path skin = skinDirectory();
        Files.writeString(skin.resolve("helper.lua"), "return { value = 42 }");
        SkinLuaAccessor lua = accessor(skin);
        assertEquals(42, lua.exec("return require('helper').value").toint());
    }

    @Test void ioWithoutASkinDirectoryIsRefused() {
        SkinLuaAccessor lua = new SkinLuaAccessor(true);
        assertTrue(lua.exec("return io.open('anything.txt')").isnil());
        assertTrue(lua.exec("return os.execute").isnil());
    }
}
