package bms.player.beatoraja.skin.lua;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.Prototype;

/** Shares compiled code only. Globals, module results and closure upvalues are never cached. */
final class SkinLuaPrototypeCache {
    private static final SkinLuaPrototypeCache SHARED =
            new SkinLuaPrototypeCache(64, 8 * 1024 * 1024, 16 * 1024 * 1024, 2 * 1024 * 1024);

    private final int maximumEntries;
    private final long maximumSourceBytes;
    private final long maximumPrototypeBytes;
    private final int maximumChunkBytes;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long sourceBytes, prototypeBytes;

    SkinLuaPrototypeCache(int maximumEntries, long maximumSourceBytes,
            long maximumPrototypeBytes, int maximumChunkBytes) {
        this.maximumEntries = maximumEntries;
        this.maximumSourceBytes = maximumSourceBytes;
        this.maximumPrototypeBytes = maximumPrototypeBytes;
        this.maximumChunkBytes = maximumChunkBytes;
    }

    static void install(Globals globals) {
        SHARED.wrapCompiler(globals);
    }

    void wrapCompiler(Globals globals) {
        Globals.Compiler compiler = globals.compiler;
        globals.compiler = (input, name) -> compile(compiler, input, name);
    }

    private Prototype compile(Globals.Compiler compiler, InputStream input, String name) throws IOException {
        // Do not retain very long inline chunk names or buffer arbitrarily large streams.
        if (maximumEntries <= 0 || name == null || name.length() > 4096) {
            return compiler.compile(input, name);
        }
        byte[] source = input.readNBytes(maximumChunkBytes + 1);
        if (source.length > maximumChunkBytes || source.length > maximumSourceBytes) {
            // SequenceInputStream closes its components at EOF; LuaC leaves the caller's stream open.
            InputStream remainder = new FilterInputStream(input) {
                @Override public void close() { }
            };
            return compiler.compile(new SequenceInputStream(new ByteArrayInputStream(source), remainder), name);
        }
        Key key = new Key(name, digest(source));
        synchronized (this) {
            Entry previous = entries.get(key);
            if (previous != null && previous.compiler == compiler) return previous.prototype;
        }
        // Compilation and source reads never hold the shared LRU lock.
        Prototype prototype = compiler.compile(new ByteArrayInputStream(source), name);
        long weight = prototypeWeight(prototype) + 2L * name.length() + 256;
        if (weight <= maximumPrototypeBytes) {
            synchronized (this) {
                Entry previous = entries.put(key, new Entry(compiler, prototype, source.length, weight));
                if (previous != null) {
                    sourceBytes -= previous.sourceBytes;
                    prototypeBytes -= previous.prototypeBytes;
                }
                sourceBytes += source.length;
                prototypeBytes += weight;
                while (entries.size() > maximumEntries || sourceBytes > maximumSourceBytes
                        || prototypeBytes > maximumPrototypeBytes) {
                    var iterator = entries.values().iterator();
                    Entry oldest = iterator.next();
                    sourceBytes -= oldest.sourceBytes;
                    prototypeBytes -= oldest.prototypeBytes;
                    iterator.remove();
                }
            }
        }
        return prototype;
    }

    private static String digest(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Conservative accounting for code, debug arrays, constants and their strings; not a JVM RSS cap. */
    private static long prototypeWeight(Prototype prototype) {
        long bytes = 512;
        if (prototype.code != null) bytes += 4L * prototype.code.length;
        if (prototype.lineinfo != null) bytes += 4L * prototype.lineinfo.length;
        if (prototype.source != null) bytes += prototype.source.m_bytes.length;
        if (prototype.k != null) {
            bytes += 96L * prototype.k.length;
            for (var constant : prototype.k) {
                // A short LuaString may keep an entire larger backing array alive.
                if (constant instanceof LuaString text) bytes += text.m_bytes.length;
            }
        }
        if (prototype.locvars != null) {
            bytes += 96L * prototype.locvars.length;
            for (var local : prototype.locvars) {
                if (local != null && local.varname != null) bytes += local.varname.m_bytes.length;
            }
        }
        if (prototype.upvalues != null) {
            bytes += 96L * prototype.upvalues.length;
            for (var upvalue : prototype.upvalues) {
                if (upvalue != null && upvalue.name != null) bytes += upvalue.name.m_bytes.length;
            }
        }
        if (prototype.p != null) {
            bytes += 8L * prototype.p.length;
            for (Prototype child : prototype.p) bytes += prototypeWeight(child);
        }
        return bytes;
    }

    private record Key(String name, String digest) { }
    private record Entry(Globals.Compiler compiler, Prototype prototype, long sourceBytes, long prototypeBytes) { }
}
