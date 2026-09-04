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

	private SceneResourceCache() {
	}

	public static synchronized Handle acquire(Path source) throws SceneValidationException {
		Path canonical = canonical(source);
		if (!Files.isRegularFile(canonical)) {
			throw new SceneValidationException("Scene file does not exist: " + canonical);
		}
		CompiledScene scene = compile(canonical);
		Key key = new Key(canonical, resourceVersion(canonical, scene));
		Entry entry = ENTRIES.get(key);
		if (entry == null) {
			entry = new Entry(load(canonical, scene));
			ENTRIES.put(key, entry);
		}
		entry.references++;
		return new Handle(key, entry.bundle);
	}

	private static CompiledScene compile(Path source) throws SceneValidationException {
		SceneDocument document = new SceneDocumentLoader().load(source);
		return new SceneCompiler().compile(document, source);
	}

	private static Bundle load(Path source, CompiledScene scene) throws SceneValidationException {
		Texture[] textures = new Texture[scene.textures.length];
		try {
			IntBuffer sizeBuffer = BufferUtils.newIntBuffer(1);
			Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, sizeBuffer);
			int maximumTextureSize = sizeBuffer.get(0);
			long estimatedTextureBytes = 0;
			Path sceneRoot = source.getParent().toRealPath();
			for (int i = 0; i < textures.length; i++) {
				CompiledScene.TextureResource resource = scene.textures[i];
				Path texturePath = Path.of(resource.path).toRealPath();
				if (!texturePath.startsWith(sceneRoot) || !Files.isRegularFile(texturePath)) {
					throw new SceneValidationException("Scene texture does not exist: " + texturePath);
				}
				textures[i] = new Texture(Gdx.files.absolute(texturePath.toString()));
				if (textures[i].getWidth() > maximumTextureSize
						|| textures[i].getHeight() > maximumTextureSize) {
					throw new SceneValidationException("Scene texture exceeds the GPU size limit: "
							+ texturePath);
				}
				estimatedTextureBytes += (long) textures[i].getWidth()
						* textures[i].getHeight() * 4;
				TextureFilter filter = resource.linear ? TextureFilter.Linear : TextureFilter.Nearest;
				textures[i].setFilter(filter, filter);
				textures[i].setWrap(resource.repeatX ? TextureWrap.Repeat : TextureWrap.ClampToEdge,
						resource.repeatY ? TextureWrap.Repeat : TextureWrap.ClampToEdge);
			}
			if (estimatedTextureBytes > TEXTURE_MEMORY_WARNING_BYTES) {
				logger.warn("Skin scene {} uses approximately {} MiB of decoded textures",
						source, estimatedTextureBytes / (1024 * 1024));
			}
			return new Bundle(scene, textures);
		} catch (Throwable error) {
			for (Texture texture : textures) {
				if (texture != null) texture.dispose();
			}
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
			return bundle.textures[index];
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

	private static final class Entry {
		private final Bundle bundle;
		private int references;

		private Entry(Bundle bundle) {
			this.bundle = bundle;
		}
	}

	private static final class Bundle {
		private final CompiledScene scene;
		private final Texture[] textures;

		private Bundle(CompiledScene scene, Texture[] textures) {
			this.scene = scene;
			this.textures = textures;
		}

		private void dispose() {
			for (Texture texture : textures) {
				texture.dispose();
			}
		}
	}
}
