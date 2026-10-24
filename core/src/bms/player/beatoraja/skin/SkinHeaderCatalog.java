package bms.player.beatoraja.skin;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.skin.json.JSONSkinLoader;
import bms.player.beatoraja.skin.lr2.LR2SkinHeaderLoader;
import bms.player.beatoraja.skin.lua.LuaSkinLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Shared F5/F12 definitions. Never cache skins, Lua globals or player properties. */
public class SkinHeaderCatalog {
	private static final Logger logger = LoggerFactory.getLogger(SkinHeaderCatalog.class);
	private static final SkinHeaderCatalog shared = new SkinHeaderCatalog();
	private Path root;
	private String lr2SkinPath;
	private Map<Path, Stamp> stamps = Map.of();
	private List<SkinHeader> headers = List.of();

	public static SkinHeaderCatalog shared() {
		return shared;
	}

	/** Also refreshes headers whose scripts read dependencies outside the skin tree. */
	public synchronized void invalidate() {
		root = null;
	}

	public synchronized List<SkinHeader> load(Path directory, Config config) {
		Snapshot snapshot = scan(directory);
		Path identity = directory.toAbsolutePath().normalize();
		if (!snapshot.complete || !identity.equals(root)
				|| !Objects.equals(lr2SkinPath, config.getSkinpath()) || !stamps.equals(snapshot.stamps)) {
			List<SkinHeader> loaded = new ArrayList<>();
			for (Path path : snapshot.paths) {
				try {
					SkinHeader header = loadHeader(path, config);
					if (header == null || header.getSkinType() == null) continue;
					loaded.add(header.copyDefinition());
					if (header.getType() == SkinHeader.TYPE_LR2SKIN
							&& (header.getSkinType() == SkinType.PLAY_7KEYS
							|| header.getSkinType() == SkinType.PLAY_14KEYS)) {
						SkinHeader alias = header.copyDefinition();
						String keys = header.getSkinType() == SkinType.PLAY_7KEYS ? "7KEYS" : "14KEYS";
						if (!alias.getName().toUpperCase(Locale.ROOT).contains(keys.substring(0, keys.length() - 1))) {
							alias.setName(alias.getName() + " (" + keys + ") ");
						}
						alias.setSkinType(header.getSkinType() == SkinType.PLAY_7KEYS
								? SkinType.PLAY_5KEYS : SkinType.PLAY_10KEYS);
						loaded.add(alias);
					}
				} catch (Exception e) {
					logger.warn("スキン一覧のヘッダを読み込めませんでした: {}", path, e);
				}
			}
			root = snapshot.complete ? identity : null;
			lr2SkinPath = config.getSkinpath();
			stamps = snapshot.stamps;
			headers = List.copyOf(loaded);
		}
		return headers.stream().map(SkinHeader::copyDefinition).toList();
	}

	SkinHeader loadHeader(Path path, Config config) throws IOException {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".json")) return new JSONSkinLoader().loadHeader(path);
		if (name.endsWith(".luaskin")) return new LuaSkinLoader().loadHeader(path);
		return new LR2SkinHeaderLoader(config).loadSkin(path, null);
	}

	private record Stamp(FileTime modified, long size, Object fileKey) {}

	private static final class Snapshot {
		final Map<Path, Stamp> stamps = new HashMap<>();
		final List<Path> paths = new ArrayList<>();
		boolean complete = true;
	}

	private static Snapshot scan(Path directory) {
		Snapshot snapshot = new Snapshot();
		try {
			// Include modules and assets: Lua headers may depend on require/dofile or file existence.
			Files.walkFileTree(directory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
					new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					record(dir, attrs);
					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					record(file, attrs);
					String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
					if (attrs.isRegularFile() && (name.endsWith(".json") || name.endsWith(".luaskin")
							|| name.endsWith(".lr2skin"))) snapshot.paths.add(file);
					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult visitFileFailed(Path file, IOException error) {
					if (!(error instanceof FileSystemLoopException)) snapshot.complete = false;
					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException error) {
					if (error != null) snapshot.complete = false;
					return FileVisitResult.CONTINUE;
				}
				private void record(Path path, BasicFileAttributes attrs) {
					snapshot.stamps.put(path, new Stamp(attrs.lastModifiedTime(), attrs.size(), attrs.fileKey()));
				}
			});
		} catch (IOException e) {
			snapshot.complete = false;
		}
		snapshot.paths.sort(Comparator.comparing(Path::toString));
		return snapshot;
	}
}
