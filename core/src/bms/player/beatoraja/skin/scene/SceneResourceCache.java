package bms.player.beatoraja.skin.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.IntBuffer;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reference-counted ownership for a compiled scene and its textures. */
public final class SceneResourceCache {
	private static final Logger logger = LoggerFactory.getLogger(SceneResourceCache.class);
	private static final long TEXTURE_MEMORY_WARNING_BYTES = 256L * 1024 * 1024;
	private static final Map<Key, Entry> ENTRIES = new HashMap<>();
	private static final SceneCompilationCache COMPILED = new SceneCompilationCache(32, 16L * 1024 * 1024);
	private static final SceneTexturePool<TextureKey, Texture> TEXTURES = new SceneTexturePool<>(Texture::dispose);

	private SceneResourceCache() {
	}

	public static synchronized Handle acquire(Path source) throws SceneValidationException {
		Path canonical = canonical(source);
		if (!Files.isRegularFile(canonical)) {
			throw new SceneValidationException("Scene file does not exist: " + canonical);
		}
		CompiledScene scene = COMPILED.get(canonical);
		Key key = new Key(canonical, resourceVersion(canonical, scene));
		Entry entry = ENTRIES.get(key);
		if (entry == null) {
			entry = new Entry(load(canonical, scene, key.version));
			ENTRIES.put(key, entry);
		}
		entry.references++;
		return new Handle(key, entry.bundle);
	}

	private static Bundle load(Path source, CompiledScene scene, ResourceVersion version) throws SceneValidationException {
		List<SceneTexturePool<TextureKey, Texture>.Lease> textures = new ArrayList<>();
		try {
			IntBuffer sizeBuffer = BufferUtils.newIntBuffer(1);
			Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, sizeBuffer);
			int maximumTextureSize = sizeBuffer.get(0);
			long estimatedTextureBytes = 0;
			Path sceneRoot = source.getParent().toRealPath();
			for (int i = 0; i < scene.textures.length; i++) {
				CompiledScene.TextureResource resource = scene.textures[i];
				Path texturePath = Path.of(resource.path).toRealPath();
				if (!texturePath.startsWith(sceneRoot) || !Files.isRegularFile(texturePath)) {
					throw new SceneValidationException("Scene texture does not exist: " + texturePath);
				}
				TextureKey key = new TextureKey(version.textures.get(i), resource.linear,
						resource.repeatX, resource.repeatY, Gdx.gl);
				var lease = TEXTURES.acquire(key, () -> {
					Texture texture = new Texture(Gdx.files.absolute(texturePath.toString()));
					try {
						if (texture.getWidth() > maximumTextureSize || texture.getHeight() > maximumTextureSize)
							throw new IllegalArgumentException("Scene texture exceeds the GPU size limit: " + texturePath);
						TextureFilter filter = resource.linear ? TextureFilter.Linear : TextureFilter.Nearest;
						texture.setFilter(filter, filter);
						texture.setWrap(resource.repeatX ? TextureWrap.Repeat : TextureWrap.ClampToEdge,
								resource.repeatY ? TextureWrap.Repeat : TextureWrap.ClampToEdge);
						return texture;
					} catch (Throwable error) {
						texture.dispose();
						throw error;
					}
				});
				textures.add(lease);
				Texture texture = lease.value();
				estimatedTextureBytes += (long) texture.getWidth() * texture.getHeight() * 4;
			}
			if (estimatedTextureBytes > TEXTURE_MEMORY_WARNING_BYTES) {
				logger.warn("Skin scene {} uses approximately {} MiB of decoded textures",
						source, estimatedTextureBytes / (1024 * 1024));
			}
			return new Bundle(scene, textures);
		} catch (Throwable error) {
			for (var texture : textures) texture.close();
			if (error instanceof SceneValidationException validation) {
				throw validation;
			}
			throw new SceneValidationException("Failed to load scene resources for " + source
					+ ": " + error.getMessage(), error);
		}
	}

	static ResourceVersion resourceVersion(Path source, CompiledScene scene)
			throws SceneValidationException {
		try {
			ResourceStamp sceneStamp = stamp(source);
			List<ResourceStamp> textureStamps = new ArrayList<>(scene.textures.length);
			Path sceneRoot = source.getParent().toRealPath();
			for (CompiledScene.TextureResource texture : scene.textures) {
				Path texturePath = Path.of(texture.path).toRealPath();
				if (!texturePath.startsWith(sceneRoot) || !Files.isRegularFile(texturePath)) {
					throw new SceneValidationException("Scene texture does not exist: " + texturePath);
				}
				textureStamps.add(stamp(texturePath));
			}
			return new ResourceVersion(sceneStamp, List.copyOf(textureStamps));
		} catch (IOException error) {
			throw new SceneValidationException("Cannot stat scene resources for " + source, error);
		}
	}

	private static ResourceStamp stamp(Path path) throws IOException {
		BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
		return new ResourceStamp(path, attributes.size(), attributes.lastModifiedTime());
	}

	private static Path canonical(Path source) throws SceneValidationException {
		try {
			return source.toRealPath();
		} catch (IOException error) {
			throw new SceneValidationException("Cannot resolve scene path: " + source, error);
		}
	}

	private static synchronized void release(Key key) {
		Entry entry = ENTRIES.get(key);
		if (entry == null || --entry.references > 0) {
			return;
		}
		ENTRIES.remove(key);
		entry.bundle.dispose();
	}

	public static final class Handle implements AutoCloseable {
		private final Key key;
		private final Bundle bundle;
		private boolean closed;

		private Handle(Key key, Bundle bundle) {
			this.key = key;
			this.bundle = bundle;
		}

		public CompiledScene scene() {
			return bundle.scene;
		}

		public Texture texture(int index) {
			return bundle.textures.get(index).value();
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				release(key);
			}
		}
	}

	private record Key(Path source, ResourceVersion version) {
	}

	static record ResourceVersion(ResourceStamp scene, List<ResourceStamp> textures) {
	}

	static record ResourceStamp(Path path, long size, FileTime modified) {
	}

	static record TextureKey(ResourceStamp stamp, boolean linear, boolean repeatX, boolean repeatY, GL20 context) {
	}

	private static final class Entry {
		private final Bundle bundle;
		private int references;

		private Entry(Bundle bundle) {
			this.bundle = bundle;
		}
	}

	private static final class Bundle {
		private final CompiledScene scene;
		private final List<SceneTexturePool<TextureKey, Texture>.Lease> textures;

		private Bundle(CompiledScene scene, List<SceneTexturePool<TextureKey, Texture>.Lease> textures) {
			this.scene = scene;
			this.textures = textures;
		}

		private void dispose() {
			for (var texture : textures) texture.close();
		}
	}
}
