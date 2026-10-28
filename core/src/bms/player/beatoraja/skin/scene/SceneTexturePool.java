package bms.player.beatoraja.skin.scene;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shares resources while at least one scene owns them. Caller serializes access. */
final class SceneTexturePool<K, T> {
	private final Map<K, Entry<T>> entries = new HashMap<>();
	private final Consumer<T> dispose;

	SceneTexturePool(Consumer<T> dispose) {
		this.dispose = dispose;
	}

	Lease acquire(K key, Supplier<T> factory) {
		Entry<T> entry = entries.get(key);
		if (entry == null) {
			entry = new Entry<>(factory.get());
			entries.put(key, entry);
		}
		entry.references++;
		return new Lease(key, entry);
	}

	final class Lease implements AutoCloseable {
		private final K key;
		private final Entry<T> entry;
		private boolean closed;

		private Lease(K key, Entry<T> entry) { this.key = key; this.entry = entry; }
		T value() { return entry.value; }

		@Override public void close() {
			if (closed) return;
			closed = true;
			if (--entry.references == 0) {
				entries.remove(key);
				dispose.accept(entry.value);
			}
		}
	}

	private static final class Entry<T> {
		private final T value;
		private int references;
		private Entry(T value) { this.value = value; }
	}
}
