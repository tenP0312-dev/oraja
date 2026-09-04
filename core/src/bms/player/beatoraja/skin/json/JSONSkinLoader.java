package bms.player.beatoraja.skin.json;

import static bms.player.beatoraja.Resolution.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.*;

import bms.player.beatoraja.*;
import bms.player.beatoraja.config.SkinConfigurationSkin;
import bms.player.beatoraja.play.bga.BGAProcessor;
import bms.player.beatoraja.skin.*;
import bms.player.beatoraja.skin.SkinHeader.CustomItem;
import bms.player.beatoraja.skin.lua.SkinLuaAccessor;
import bms.player.beatoraja.skin.property.TimerProperty;
import bms.player.beatoraja.skin.scene.SceneResourceCache;
import bms.player.beatoraja.skin.scene.SkinSceneObject;

/**
 * JSONスキンローダー
 * 
 * @author exch
 */
public class JSONSkinLoader extends SkinLoader {
	private static final Logger logger = LoggerFactory.getLogger(JSONSkinLoader.class);

	protected Resolution dstr;
	protected boolean usecim;
	protected int bgaExpand = -1;

	protected JsonSkin.Skin sk;

	Map<String, SourceData> sourceMap;
	Map<String, SkinTextBitmap.SkinTextBitmapSource> bitmapSourceMap;
	private Map<String, JsonSkin.SceneResource> sceneMap = java.util.Collections.emptyMap();
	private Set<String> claimedSceneIds = java.util.Collections.emptySet();

	protected final SkinLuaAccessor lua;

	protected ObjectMap<String, String> filemap = new ObjectMap<String, String>();

	protected JsonSkinSerializer serializer;
	
	protected static class SourceData {
		public final String path;
		public boolean loaded = false;
		public Object data;
		
		public SourceData(String path) {
			this.path = path;
		}
	}

	/**
	 * ヘッダの読み込みに使われるコンストラクタ
	 */
	public JSONSkinLoader() {
		this(null);
	}

	public JSONSkinLoader(SkinLuaAccessor lua) {
		this.lua = lua;
		dstr = HD;
		usecim = false;
	}

	/**
	 * スキン本体の読み込みに使われるコンストラクタ
	 * @param state
	 * @param c
	 */
	public JSONSkinLoader(MainState state, Config c) {
		this(state, c, new SkinLuaAccessor(true));
	}

	public JSONSkinLoader(MainState state, Config c, SkinLuaAccessor lua) {
		this.lua = lua;
		dstr = c.getResolution();
		usecim = false;
		bgaExpand = c.getBgaExpand();
		lua.exportMainStateAccessor(state);
		lua.exportUtilities(state);
	}

	public Skin loadSkin(Path p, SkinType type, SkinConfig.Property property) {
		return load(p, type, property);
	}

	public SkinHeader loadHeader(Path p) {
		serializer = new JsonSkinSerializer(lua, path -> getPath(path, filemap));
		SkinHeader header = null;
		try {
			Json json = new Json();
			json.setIgnoreUnknownFields(true);
			serializer.setSerializers(json, null, p);
			try{
                sk = json.fromJson(JsonSkin.Skin.class, new FileReader(p.toFile()));
            }
			catch (Exception e) {
				logger.warn("Error parsing json, retrying with Shift JIS: {}", p);
                var stream = new InputStreamReader(new FileInputStream(p.toFile()),
                                                   Charset.forName("Shift_JIS"));
                sk = json.fromJson(JsonSkin.Skin.class, stream);
            }
            header = loadJsonSkinHeader(sk, p);
		} catch (FileNotFoundException e) {
			logger.error("JSONスキンファイルが見つかりません : {}", p.toString());
		}
		return header;
	}

	protected SkinHeader loadJsonSkinHeader(JsonSkin.Skin sk, Path p) {
		SkinHeader header = null;
		try {
			if (sk.type != -1) {
				header = new SkinHeader();
				header.setSkinType(SkinType.getSkinTypeById(sk.type));
				header.setName(sk.name != null ? sk.name : "");
				header.setAuthor(sk.author != null ? sk.author : "");
				header.setPath(p);
				header.setType(SkinHeader.TYPE_BEATORJASKIN);

				ObjectMap<JsonSkin.Category, SkinHeader.CustomItem[]> categories = new ObjectMap<JsonSkin.Category, SkinHeader.CustomItem[]>();
				for(JsonSkin.Category category : sk.category) {
					categories.put(category, new SkinHeader.CustomItem[category.item.length]);
				}
				
				SkinHeader.CustomOption[] options = new SkinHeader.CustomOption[sk.property.length];
				for (int i = 0; i < sk.property.length; i++) {
					JsonSkin.Property pr = sk.property[i];

					int[] op = new int[pr.item.length];
					String[] name = new String[pr.item.length];
					for (int j = 0; j < pr.item.length; j++) {
						op[j] = pr.item[j].op;
						name[j] = pr.item[j].name;
					}
					options[i] = new SkinHeader.CustomOption(pr.name, op, name, pr.def);
					
					for(JsonSkin.Category category : sk.category) {
						for(int index = 0;index < category.item.length;index++) {
							if(category.item[index].equals(pr.category)) {
								categories.get(category)[index] = options[i];
							}
						}
					}
				}
				header.setCustomOptions(options);

				SkinHeader.CustomFile[] files = new SkinHeader.CustomFile[sk.filepath.length];
				for (int i = 0; i < sk.filepath.length; i++) {
					JsonSkin.Filepath pr = sk.filepath[i];
					files[i] = new SkinHeader.CustomFile(pr.name, p.getParent().toString() + "/" + pr.path, pr.def);
					for(JsonSkin.Category category : sk.category) {
						for(int index = 0;index < category.item.length;index++) {
							if(category.item[index].equals(pr.category)) {
								categories.get(category)[index] = files[i];
							}
						}
					}
				}
				header.setCustomFiles(files);

				int offsetLengthAddition = 0;
				switch (header.getSkinType()) {
					case PLAY_5KEYS:
					case PLAY_7KEYS:
					case PLAY_9KEYS:
					case PLAY_10KEYS:
					case PLAY_14KEYS:
					case PLAY_24KEYS:
					case PLAY_24KEYS_DOUBLE:
						offsetLengthAddition = 4;
					default:
				}
				SkinHeader.CustomOffset[] offsets = new SkinHeader.CustomOffset[sk.offset.length + offsetLengthAddition];
				for (int i = 0; i < sk.offset.length; i++) {
					JsonSkin.Offset pr = sk.offset[i];
					offsets[i] = new SkinHeader.CustomOffset(pr.name, pr.id, pr.x, pr.y, pr.w, pr.h, pr.r, pr.a);
					for(JsonSkin.Category category : sk.category) {
						for(int index = 0;index < category.item.length;index++) {
							if(category.item[index].equals(pr.category)) {
								categories.get(category)[index] = offsets[i];
							}
						}
					}
				}
				switch (header.getSkinType()) {
					case PLAY_5KEYS:
					case PLAY_7KEYS:
					case PLAY_9KEYS:
					case PLAY_10KEYS:
					case PLAY_14KEYS:
					case PLAY_24KEYS:
					case PLAY_24KEYS_DOUBLE:
						offsets[sk.offset.length + 0] = new SkinHeader.CustomOffset("All offset(%)", SkinProperty.OFFSET_ALL, true, true, true, true, false, false);
						offsets[sk.offset.length + 1] = new SkinHeader.CustomOffset("Notes offset", SkinProperty.OFFSET_NOTES_1P, false, false, false, true, false, false);
						offsets[sk.offset.length + 2] = new SkinHeader.CustomOffset("Judge offset", SkinProperty.OFFSET_JUDGE_1P, true, true, true, true, false, true);
						offsets[sk.offset.length + 3] = new SkinHeader.CustomOffset("Judge Detail offset", SkinProperty.OFFSET_JUDGEDETAIL_1P, true, true, true, true, false, true);
					default:
				}
				header.setCustomOffsets(offsets);
				
				SkinHeader.CustomCategory[] category = new SkinHeader.CustomCategory[sk.category.length];
				for(int i = 0;i < sk.category.length;i++) {
					JsonSkin.Category pr = sk.category[i];
					Array<SkinHeader.CustomItem> array = new Array<CustomItem>();
					for(SkinHeader.CustomItem item : categories.get(pr)) {
						if(item != null) {
							array.add(item);
						}
					}
					category[i] = new SkinHeader.CustomCategory(pr.name, array.toArray(SkinHeader.CustomItem.class)); 
				}
				header.setCustomCategories(category);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return header;
	}

	public Skin load(Path p, SkinType type, SkinConfig.Property property) {
		serializer = new JsonSkinSerializer(lua, path -> getPath(path, filemap));
		Skin skin = null;
		SkinHeader header = loadHeader(p);
		if(header == null) {
			return null;
		}
		header.setSkinConfigProperty(property);

		try {
			Json json = new Json();
			json.setIgnoreUnknownFields(true);

			serializer.setSerializers(json, getEnabledOptions(header), p);
			
			filemap = new ObjectMap<>();
			for(SkinHeader.CustomFile customFile : header.getCustomFiles()) {
				if(customFile.getSelectedFilename() != null) {
					filemap.put(customFile.path, customFile.getSelectedFilename());
				}
			}

			lua.exportSkinProperty(header, property, (String path) -> {
				return getPath(p.getParent().toString() + "/" + path, filemap).getPath();
			});

            try {
                sk = json.fromJson(JsonSkin.Skin.class, new FileReader(p.toFile()));
            }
            catch (Exception e) {
				logger.warn("Error parsing json, retrying with Shift JIS: {}", p);
                var stream = new InputStreamReader(new FileInputStream(p.toFile()),
                                                   Charset.forName("Shift_JIS"));
                sk = json.fromJson(JsonSkin.Skin.class, stream);
            }

            skin = loadJsonSkin(header, sk, type, property, p);
		} catch (FileNotFoundException e) {
			logger.error("JSONスキンファイルが見つかりません : {}", p.toString());
		} catch (Throwable e) {
			logger.error("何らかの原因でJSONスキンファイルの読み込みに失敗しました");
			e.printStackTrace();
		}
		return skin;
	}

	protected HashSet<Integer> getEnabledOptions(SkinHeader header) {
		HashSet<Integer> enabledOptions = new HashSet<>();
		for (SkinHeader.CustomOption customOption : header.getCustomOptions()) {
			enabledOptions.add(customOption.getSelectedOption());	
		}		
		return enabledOptions;
	}

	protected Skin loadJsonSkin(SkinHeader header, JsonSkin.Skin sk, SkinType type, SkinConfig.Property property, Path p){
		Skin skin = null;
		try (var perf = PerformanceMetrics.get().Event("Load JSON skin: " + p)) {
			Resolution src = HD;
			for(Resolution r : Resolution.values()) {
				if(sk.w == r.width && sk.h == r.height) {
					src = r;
					break;
				}
			}

			sourceMap = new HashMap<>();
			bitmapSourceMap = new HashMap<>();
			prepareSceneResources(sk);

			JsonSkinObjectLoader objectLoader = null;
			switch(type) {
			case MUSIC_SELECT:
				objectLoader = new JsonSelectSkinObjectLoader(this);
				break;
			case PLAY_5KEYS:
			case PLAY_7KEYS:
			case PLAY_9KEYS:
			case PLAY_10KEYS:
			case PLAY_14KEYS:
			case PLAY_24KEYS:
			case PLAY_24KEYS_DOUBLE:
				objectLoader = new JsonPlaySkinObjectLoader(this);
				break;
			case DECIDE:
				objectLoader = new JsonDecideSkinObjectLoader(this);
				break;
			case RESULT:
				objectLoader = new JsonResultSkinObjectLoader(this);
				break;
			case COURSE_RESULT:
				objectLoader = new JsonCourseResultSkinObjectLoader(this);
				break;
			case SKIN_SELECT:
				objectLoader = new JsonSkinConfigurationSkinObjectLoader(this);
				break;
			case KEY_CONFIG:
			default:
				objectLoader = new JsonKeyConfigurationSkinObjectLoader(this);
				break;				
			}
			
			header.setSourceResolution(src);
			header.setDestinationResolution(dstr);
			skin = objectLoader.getSkin(header);
			
			IntIntMap op = new IntIntMap();
			for (SkinHeader.CustomOption option : header.getCustomOptions()) {
				for (int i = 0; i < option.option.length; i++) {
					op.put(option.option[i], option.option[i] == option.getSelectedOption() ? 1 : 0);
				}
			}
			skin.setOption(op);

			IntMap<SkinConfig.Offset> offset = new IntMap<>();
			for (SkinHeader.CustomOffset of : header.getCustomOffsets()) {
				offset.put(of.id, of.getOffset());
			}
			skin.setOffset(offset);

			skin.setFadeout(sk.fadeout);
			skin.setInput(sk.input);
			skin.setScene(sk.scene);
			
			for(JsonSkin.Source source : sk.source) {
				sourceMap.put(source.id, new SourceData(source.path));
			}

            BitmapFontBatchLoader bmpFontLoader = new BitmapFontBatchLoader(sk, p, usecim, true);
            try (var fontPerf = PerformanceMetrics.get().Event("Bitmap font preload")) {
                bmpFontLoader.load();
            }

			for (JsonSkin.Destination dst : sk.destination) {
				SkinObject obj = null;
				if (!claimsSceneId(dst.id)) {
					try {
						int id = Integer.parseInt(dst.id);
						if (id < 0) {
							obj = new SkinImage(-id);
						}
					} catch (Exception e) {
					}
				}
				if (obj == null) {
					if(objectLoader != null) {
						SkinObject sobj = objectLoader.loadSkinObject(skin, sk, dst, p);
						if(sobj != null) {
							obj = sobj;
						}
					}
				}

				if (obj != null) {
					obj.setName(dst.id);
					setDestination(skin, obj, dst);
					skin.add(obj);
				}
			}

			if (sk.skinSelect != null && skin instanceof  SkinConfigurationSkin) {
				SkinConfigurationSkin skinSelect = (SkinConfigurationSkin) skin;
				skinSelect.setCustomOffsetStyle(sk.skinSelect.customOffsetStyle);
				skinSelect.setDefaultSkinType(sk.skinSelect.defaultCategory);
				skinSelect.setSampleBMS(sk.skinSelect.customBMS);
				if (sk.skinSelect.customPropertyCount > 0) {
					skinSelect.setCustomPropertyCount(sk.skinSelect.customPropertyCount);
				} else {
					int count = 0;
					for (JsonSkin.Image image : sk.image) {
						if (SkinPropertyMapper.isSkinCustomizeButton(image.act.getEventId())) {
							int index = SkinPropertyMapper.getSkinCustomizeIndex(image.act.getEventId());
							if (count <= index)
								count = index + 1;
						}
					}
					for (JsonSkin.ImageSet imageSet : sk.imageset) {
						if (SkinPropertyMapper.isSkinCustomizeButton(imageSet.act.getEventId())) {
							int index = SkinPropertyMapper.getSkinCustomizeIndex(imageSet.act.getEventId());
							if (count <= index)
								count = index + 1;
						}
					}
					skinSelect.setCustomPropertyCount(count);
				}
			}

			if (sk.customEvents != null) {
				for (JsonSkin.CustomEvent event : sk.customEvents) {
					skin.addCustomEvent(new CustomEvent(event.id, event.action, event.condition, event.minInterval));
				}
			}

			if (sk.customTimers != null) {
				for (JsonSkin.CustomTimer timer : sk.customTimers) {
					skin.addCustomTimer(new CustomTimer(timer.id, timer.timer));
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
		return skin;
	}

	void prepareSceneResources(JsonSkin.Skin skin) {
		Map<String, JsonSkin.SceneResource> scenes = new HashMap<>();
		Set<String> claimedIds = new HashSet<>();
		Set<String> blockedIds = new HashSet<>();
		Set<String> ordinaryIds = new HashSet<>();
		collectIds(ordinaryIds, skin.source, value -> value.id);
		collectIds(ordinaryIds, skin.font, value -> value.id);
		collectIds(ordinaryIds, skin.image, value -> value.id);
		collectIds(ordinaryIds, skin.imageset, value -> value.id);
		collectIds(ordinaryIds, skin.value, value -> value.id);
		collectIds(ordinaryIds, skin.floatvalue, value -> value.id);
		collectIds(ordinaryIds, skin.text, value -> value.id);
		collectIds(ordinaryIds, skin.slider, value -> value.id);
		collectIds(ordinaryIds, skin.graph, value -> value.id);
		collectIds(ordinaryIds, skin.gaugegraph, value -> value.id);
		collectIds(ordinaryIds, skin.judgegraph, value -> value.id);
		collectIds(ordinaryIds, skin.bpmgraph, value -> value.id);
		collectIds(ordinaryIds, skin.radargraph, value -> value.id);
		collectIds(ordinaryIds, skin.hiterrorvisualizer, value -> value.id);
		collectIds(ordinaryIds, skin.timingvisualizer, value -> value.id);
		collectIds(ordinaryIds, skin.timingdistributiongraph, value -> value.id);
		collectIds(ordinaryIds, skin.hiddenCover, value -> value.id);
		collectIds(ordinaryIds, skin.liftCover, value -> value.id);
		collectIds(ordinaryIds, skin.judge, value -> value.id);
		collectIds(ordinaryIds, skin.pmchara, value -> value.id);
		collectId(ordinaryIds, skin.note == null ? null : skin.note.id);
		collectId(ordinaryIds, skin.gauge == null ? null : skin.gauge.id);
		collectId(ordinaryIds, skin.bga == null ? null : skin.bga.id);
		collectId(ordinaryIds, skin.skinpreview == null ? null : skin.skinpreview.id);
		collectId(ordinaryIds, skin.songlist == null ? null : skin.songlist.id);
		JsonSkin.SceneResource[] definitions = skin.scenes == null
				? new JsonSkin.SceneResource[0] : skin.scenes;
		for (JsonSkin.SceneResource scene : definitions) {
			if (scene == null || scene.id == null || scene.id.isBlank()) {
				logger.warn("Ignoring a scene resource with a missing id or path");
				continue;
			}
			claimedIds.add(scene.id);
			if (scene.path == null || scene.path.isBlank()) {
				logger.warn("Ignoring skin scene {} with a missing path", scene.id);
				scenes.remove(scene.id);
				blockedIds.add(scene.id);
				continue;
			}
			if (ordinaryIds.contains(scene.id) || blockedIds.contains(scene.id)
					|| scenes.putIfAbsent(scene.id, scene) != null) {
				logger.warn("Ignoring duplicate skin resource id: {}", scene.id);
				scenes.remove(scene.id);
				blockedIds.add(scene.id);
			}
		}
		sceneMap = scenes;
		claimedSceneIds = claimedIds;
	}

	private interface IdReader<T> {
		String read(T value);
	}

	private static <T> void collectIds(Set<String> ids, T[] values, IdReader<T> reader) {
		if (values == null) return;
		for (T value : values) {
			if (value != null) {
				String id = reader.read(value);
				if (id != null) ids.add(id);
			}
		}
	}

	private static void collectId(Set<String> ids, String id) {
		if (id != null) ids.add(id);
	}

	SkinObject loadSceneObject(String id, Path skinPath) {
		JsonSkin.SceneResource definition = sceneMap.get(id);
		if (definition == null) {
			return null;
		}
		SceneResourceCache.Handle resources = null;
		try {
			File file = getPath(skinPath.getParent() + "/" + definition.path, filemap);
			resources = SceneResourceCache.acquire(file.toPath());
			SkinSceneObject object = new SkinSceneObject(resources,
					resolveSceneTimer(definition.timer), definition.cycle,
					definition.playbackRate, definition.loop);
			resources = null;
			return object;
		} catch (Throwable error) {
			logger.warn("Failed to load skin scene {} from {}: {}", id, definition.path,
					error.getMessage());
			return null;
		} finally {
			if (resources != null) {
				resources.close();
			}
		}
	}

	/** Scene timer 0 follows the traditional untimed-resource convention. */
	static TimerProperty resolveSceneTimer(TimerProperty timer) {
		return timer != null && timer.getTimerId() == 0 ? null : timer;
	}

	boolean claimsSceneId(String id) {
		return claimedSceneIds.contains(id);
	}

	boolean hasEnabledSceneId(String id) {
		return sceneMap.containsKey(id);
	}

	private void setDestination(Skin skin, SkinObject obj, JsonSkin.Destination dst) {
		JsonSkin.Animation prev = null;
		for (JsonSkin.Animation a : dst.dst) {
			if (prev == null) {
				a.time = (a.time == Integer.MIN_VALUE ? 0 : a.time);
				a.x = (a.x == Integer.MIN_VALUE ? 0 : a.x);
				a.y = (a.y == Integer.MIN_VALUE ? 0 : a.y);
				a.w = (a.w == Integer.MIN_VALUE ? 0 : a.w);
				a.h = (a.h == Integer.MIN_VALUE ? 0 : a.h);
				a.acc = (a.acc == Integer.MIN_VALUE ? 0 : a.acc);
				a.angle = (a.angle == Integer.MIN_VALUE ? 0 : a.angle);
				a.a = (a.a == Integer.MIN_VALUE ? 255 : a.a);
				a.r = (a.r == Integer.MIN_VALUE ? 255 : a.r);
				a.g = (a.g == Integer.MIN_VALUE ? 255 : a.g);
				a.b = (a.b == Integer.MIN_VALUE ? 255 : a.b);
			} else {
				a.time = (a.time == Integer.MIN_VALUE ? prev.time : a.time);
				a.x = (a.x == Integer.MIN_VALUE ? prev.x : a.x);
				a.y = (a.y == Integer.MIN_VALUE ? prev.y : a.y);
				a.w = (a.w == Integer.MIN_VALUE ? prev.w : a.w);
				a.h = (a.h == Integer.MIN_VALUE ? prev.h : a.h);
				a.acc = (a.acc == Integer.MIN_VALUE ? prev.acc : a.acc);
				a.angle = (a.angle == Integer.MIN_VALUE ? prev.angle : a.angle);
				a.a = (a.a == Integer.MIN_VALUE ? prev.a : a.a);
				a.r = (a.r == Integer.MIN_VALUE ? prev.r : a.r);
				a.g = (a.g == Integer.MIN_VALUE ? prev.g : a.g);
				a.b = (a.b == Integer.MIN_VALUE ? prev.b : a.b);
			}
			if(dst.draw != null) {
				skin.setDestination(obj, a.time, a.x, a.y, a.w, a.h, a.acc, a.a, a.r, a.g, a.b, dst.blend, dst.filter,
						a.angle, dst.center, dst.loop, dst.timer, dst.draw);
			} else {
				skin.setDestination(obj, a.time, a.x, a.y, a.w, a.h, a.acc, a.a, a.r, a.g, a.b, dst.blend, dst.filter,
						a.angle, dst.center, dst.loop, dst.timer, dst.op);
			}
			if (dst.mouseRect != null) {
				skin.setMouseRect(obj, dst.mouseRect.x, dst.mouseRect.y, dst.mouseRect.w, dst.mouseRect.h);
			}
			prev = a;
		}

		int[] offsets = new int[dst.offsets.length + 1];
		for(int i = 0; i < dst.offsets.length; i++) {
			offsets[i] = dst.offsets[i];
		}
		offsets[dst.offsets.length] = dst.offset;
		obj.setOffsetID(offsets);
		if (dst.stretch >= 0) {
			obj.setStretch(dst.stretch);
		}
	}
	
	protected Object getSource(String srcid, Path p) {
		if(srcid == null) {
			return null;
		}
		
		final SourceData data = sourceMap.get(srcid);
		if(data == null) {
			return null;
		}
		
		if(data.loaded) {
			return data.data;
		}
		final File imagefile = getPath(p.getParent().toString() + "/" + data.path, filemap);
		if (imagefile.exists()) {
			boolean isMovie = false;
			 for (String mov : BGAProcessor.mov_extension) {
				 if (imagefile.getName().toLowerCase().endsWith(mov)) {
					 try {
					 	SkinSourceMovie mm = new SkinSourceMovie(imagefile.getAbsolutePath());
					 	data.data = mm;
					 	isMovie = true;
					 	break;
					 } catch (Throwable e) {
						 logger.warn("BGAファイル読み込み失敗。{}", e.getMessage());
						 e.printStackTrace();
					 }
				 }
			 }

			if (!isMovie) {
				data.data = getTexture(imagefile.getPath());
			}
		}
		data.loaded = true;
		
		return data.data;
	}

	private Texture getTexture(String path) {
		return getTexture(path, usecim);
	}
}
