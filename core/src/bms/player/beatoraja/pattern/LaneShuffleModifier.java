package bms.player.beatoraja.pattern;

import bms.model.*;

import java.util.*;

import bms.player.beatoraja.modmenu.RandomTrainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.IntStream;

import com.badlogic.gdx.utils.IntArray;

/**
 * レーン単位でノーツを入れ替えるオプション
 *
 * @author exch
 */
public abstract class LaneShuffleModifier extends PatternModifier {
	private static final Logger logger = LoggerFactory.getLogger(LaneShuffleModifier.class);

	/**
	 * 各レーンの移動先
	 */
	private int[] random;
	/**
	 * 変更レーンにスクラッチレーンを含むか
	 */
	public final boolean isScratchLaneModify;

	public final boolean showShufflePattern;

	public LaneShuffleModifier(int player, boolean isScratchLaneModify, boolean showShufflePattern) {
		super(player);
		this.isScratchLaneModify = isScratchLaneModify;
		this.showShufflePattern = showShufflePattern;
	}

	protected abstract int[] makeRandom(int[] keys, BMSModel model);

	@Override
	public void modify(BMSModel model) {
		Mode mode = model.getMode();
		final int[] keys = getKeys(mode, player, isScratchLaneModify);
		if(keys.length == 0) {
			return;
		}
		random = makeRandom(keys, model);

        // Random Trainer History
        if (random.length == 8) {
            StringBuilder randomSb = new StringBuilder();
            for (int i = 0; i < random.length - 1; i++) {
                randomSb.append(Integer.toString(random[i] + 1));
            }
            RandomTrainer rt = new RandomTrainer();
            RandomTrainer.addRandomHistory(
                    rt.new RandomHistoryEntry(model.getTitle(), randomSb.toString())
            );
        }

		final int lanes = model.getMode().key;
		final Note[] notes = new Note[lanes];
		final Note[] hnotes = new Note[lanes];
		final boolean[] clone = new boolean[lanes];
		TimeLine[] timelines = model.getAllTimeLines();
		for (int index = 0; index < timelines.length; index++) {
			final TimeLine tl = timelines[index];
			if (tl.existNote() || tl.existHiddenNote()) {
				for (int i = 0; i < lanes; i++) {
					notes[i] = tl.getNote(i);
					hnotes[i] = tl.getHiddenNote(i);
					clone[i] = false;
				}
				for (int i = 0; i < lanes; i++) {
					final int mod = i < random.length ? random[i] : i;
					if (clone[mod]) {
						if (notes[mod] != null) {
							if (notes[mod] instanceof LongNote && ((LongNote) notes[mod]).isEnd()) {
								for (int j = index - 1; j >= 0; j--) {
									if (((LongNote) notes[mod]).getPair().getSection() == timelines[j].getSection()) {
										LongNote ln = (LongNote) timelines[j].getNote(i);
										tl.setNote(i, ln.getPair());
//										System.out.println(ln.toString() + " : " + ln.getPair().toString() + " == "
//												+ ((LongNote) notes[mod]).getPair().toString() + " : "
//												+ notes[mod].toString());
										break;
									}
								}
							} else {
								tl.setNote(i, (Note) notes[mod].clone());
							}
						} else {
							tl.setNote(i, null);
						}
						if (hnotes[mod] != null) {
							tl.setHiddenNote(i, (Note) hnotes[mod].clone());
						} else {
							tl.setHiddenNote(i, null);
						}
					} else {
						tl.setNote(i, notes[mod]);
						tl.setHiddenNote(i, hnotes[mod]);
						clone[mod] = true;
					}
				}
			}
		}
	}

	public boolean isToDisplay() {
		return showShufflePattern;
	}

	public int[] getRandomPattern(Mode mode) {
		int keys = mode.key / mode.player;
		int[] repr = new int[keys];
		if(showShufflePattern) {
			int start = keys * player;
			for (int local = 0; local < keys; local++) {
				int lane = start + local;
				repr[local] = !isScratchLaneModify && mode.isScratchKey(lane)
						? lane
						: random[lane];
			}
		}
		return repr;
	}

	public static class LaneMirrorShuffleModifier extends LaneShuffleModifier {

		public LaneMirrorShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, false);
			setAssistLevel(isScratchLaneModify ? AssistLevel.LIGHT_ASSIST : AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int lane = 0; lane < keys.length; lane++) {
				result[keys[lane]] = keys[keys.length - 1 - lane];
			}
			return result;
		}
	}

	public static class LaneRotateShuffleModifier extends LaneShuffleModifier {

		public LaneRotateShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(isScratchLaneModify ? AssistLevel.LIGHT_ASSIST : AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			java.util.Random rand = new java.util.Random(getSeed());
			final boolean inc = (rand.nextInt(2) == 1);
			final int start = rand.nextInt(keys.length - 1) + (inc ? 1 : 0);
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int lane = 0, rlane = start; lane < keys.length; lane++) {
				result[keys[lane]] = keys[rlane];
				rlane = inc ? (rlane + 1) % keys.length : (rlane + keys.length - 1) % keys.length;
			}
			return result;
		}
	}

	public static class LaneRandomShuffleModifier extends LaneShuffleModifier {

		public LaneRandomShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(isScratchLaneModify ? AssistLevel.LIGHT_ASSIST : AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			java.util.Random rand = new java.util.Random(getSeed());
			IntArray l = new IntArray(keys);
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int lane = 0; lane < keys.length; lane++) {
				int r = rand.nextInt(l.size);
				result[keys[lane]] = l.get(r);
				l.removeIndex(r);
			}
			return result;
		}
	}

	/**
	 * 旧リプレイの交換前seedを再現しつつ、新形式の条件付き通常RANDOM seedも扱う。
	 * 新形式では元1鍵が既に指定先へ来るため、この交換はno-opになる。
	 */
	public static final class OneBassLaneRandomShuffleModifier
			extends LaneRandomShuffleModifier {
		private final int targetLane;

		public OneBassLaneRandomShuffleModifier(int player, int targetLane) {
			super(player, false);
			this.targetLane = targetLane;
		}

		@Override
		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = super.makeRandom(keys, model);
			if (
					keys.length == 0
							|| IntStream.of(keys).noneMatch(lane -> lane == targetLane)
			) {
				return result;
			}
			int sourceLane = keys[0];
			int sourceDestination = -1;
			for (int destination : keys) {
				if (result[destination] == sourceLane) {
					sourceDestination = destination;
					break;
				}
			}
			if (sourceDestination >= 0 && sourceDestination != targetLane) {
				int swap = result[targetLane];
				result[targetLane] = result[sourceDestination];
				result[sourceDestination] = swap;
			}
			return result;
		}
	}

	public static class PlayerFlipModifier extends LaneShuffleModifier {

		public PlayerFlipModifier() {
			super(0, true, false);
			setAssistLevel(AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			if (model.getMode().player == 2) {
				for (int i = 0; i < result.length; i++) {
					result[i] = (i + result.length / 2) % result.length;
				}
			}
			return result;
		}
	}

	public static class PlayerBattleModifier extends LaneShuffleModifier {

		public PlayerBattleModifier() {
			super(0, true, false);
			setAssistLevel(AssistLevel.ASSIST);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			if (model.getMode().player == 1) {
				return new int[0];
			} else {
				int[] result = new int[keys.length * 2];
				System.arraycopy(keys, 0, result, 0, keys.length);
				System.arraycopy(keys, 0, result, keys.length, keys.length);
				setAssistLevel(AssistLevel.ASSIST);
				return result;
			}
		}
	}

	/** Copies the already modified 1P keys to 2P for DBM/DBR link modes. */
	public static final class DoubleBattleLinkModifier extends LaneShuffleModifier {
		private final boolean symmetry;

		public DoubleBattleLinkModifier(boolean symmetry) {
			super(1, false, false);
			this.symmetry = symmetry;
			setAssistLevel(AssistLevel.NONE);
		}

		@Override
		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			int sideWidth = model.getMode().key / 2;
			int[] sourceKeys = getKeys(model.getMode(), 0, false);
			for (int index = 0; index < keys.length && index < sourceKeys.length; index++) {
				int sourceIndex = symmetry ? sourceKeys.length - 1 - index : index;
				result[keys[index]] = sourceKeys[sourceIndex];
			}
			for (int scratch : model.getMode().scratchKey) {
				if (scratch >= sideWidth) {
					int source = scratch - sideWidth;
					if (source >= 0 && source < result.length) result[scratch] = source;
				}
			}
			return result;
		}
	}

	public static class LaneCrossShuffleModifier extends LaneShuffleModifier {

		public LaneCrossShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(AssistLevel.LIGHT_ASSIST);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int i = 0; i < keys.length / 2 - 1; i += 2) {
				result[keys[i]] = keys[i + 1];
				result[keys[i + 1]] = keys[i];
				result[keys[keys.length - i - 1]] = keys[keys.length - i - 2];
				result[keys[keys.length - i - 2]] = keys[keys.length - i - 1];
			}
			return result;
		}
	}

	public static class LanePlayableRandomShuffleModifier extends LaneShuffleModifier {

		public LanePlayableRandomShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(AssistLevel.LIGHT_ASSIST);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			// 無理押しが来ないようにLaneShuffleをかける(ただし正規鏡を除く)。無理押しが来ない譜面が存在しない場合は正規か鏡でランダム
			Mode mode = model.getMode();
			int lanes = mode.key;
			int[] ln = new int[lanes];
			int[] endLnNoteTime = new int[lanes];
			int max = 0;
			for (int key : keys) {
				max = Math.max(max, key);
			}
			boolean isImpossible = false; //7個押し以上が存在するかどうか
			Set<Integer> originalPatternList = new HashSet<>(); //3個押し以上の同時押しパターンのセット
			Arrays.fill(ln, -1);
			Arrays.fill(endLnNoteTime, -1);

			//3個押し以上の同時押しパターンのリストを作る
			for (TimeLine tl : model.getAllTimeLines()) {
				if (tl.existNote()) {
					//LN
					for (int i = 0; i < lanes; i++) {
						Note n = tl.getNote(i);
						if (n instanceof LongNote ln2) {
							if (ln2.isEnd() && tl.getTime() == endLnNoteTime[i]) {
								ln[i] = -1;
								endLnNoteTime[i] = -1;
							} else {
								ln[i] = i;
								if (!ln2.isEnd()) {
									endLnNoteTime[i] = ln2.getPair().getTime();
								}
							}
						}
					}
					//通常ノート
					List<Integer> noteLane = new ArrayList<>(keys.length);
					for (int i = 0; i < lanes; i++) {
						Note n = tl.getNote(i);
						if (n != null && n instanceof NormalNote || ln[i] != -1) {
							noteLane.add(i);
						}
					}
					//7個押し以上が一つでも存在すれば無理押しが来ない譜面は存在しない
					if (noteLane.size() >= 7) {
						isImpossible = true;
						break;
					} else if (noteLane.size() >= 3) {
						int pattern = 0;
						for (Integer i : noteLane) {
							pattern += (int) Math.pow(2, i);
						}
						originalPatternList.add(pattern);
					}
				}
			}

			List<List<Integer>> kouhoPatternList = new ArrayList<>(); //無理押しが来ない譜面のリスト
			if (!isImpossible) {
				kouhoPatternList = searchForNoMurioshiLaneCombinations(originalPatternList, keys);
			}

			logger.info("無理押し無し譜面数 : {}", kouhoPatternList.size());

			int[] result = new int[9];
			if (kouhoPatternList.size() > 0) {
				int r = (int) (Math.random() * kouhoPatternList.size());
				for (int i = 0; i < 9; i++) {
					result[kouhoPatternList.get(r).get(i)] = i;
				}
			//無理押しが来ない譜面が存在しない場合は正規か鏡でランダム
			} else {
				int mirror = (int) (Math.random() * 2);
				for (int i = 0; i < 9; i++) {
					result[i] = mirror == 0 ? i : 8 - i;
				}
			}
			return result;
		}

		private List<List<Integer>> searchForNoMurioshiLaneCombinations(Set<Integer> originalPatternList, int[] keys) {
			List<List<Integer>> noMurioshiLaneCombinations = new ArrayList<>(); // 無理押しが来ない譜面のリスト
			List<Integer> tempPattern = new ArrayList<>(keys.length);
			int[] indexes = new int[9];
			int[] laneNumbers = new int[9];
			for (int i = 0; i < 9; i++) {
				laneNumbers[i] = i;
				indexes[i] = 0;
			}

			List<List<Integer>> murioshiChords = Arrays.asList(
					Arrays.asList(1, 4, 7),
					Arrays.asList(1, 4, 8),
					Arrays.asList(1, 4, 9),
					Arrays.asList(1, 5, 8),
					Arrays.asList(1, 5, 9),
					Arrays.asList(1, 6, 9),
					Arrays.asList(2, 5, 8),
					Arrays.asList(2, 5, 9),
					Arrays.asList(2, 6, 9),
					Arrays.asList(3, 6, 9)
			);

			int i = 0;
			while (i < 9) {
				if (indexes[i] < i) {
					swap(laneNumbers, i % 2 == 0 ? 0 : indexes[i], i);

					boolean murioshiFlag = false;
					for (Integer pattern : originalPatternList) {
						tempPattern.clear();
						for (int j = 0; j < 9; j++) {
							if (((int) (pattern / Math.pow(2, j)) % 2) == 1) {
								tempPattern.add(laneNumbers[j] + 1);
							}
						}

						murioshiFlag = murioshiChords.stream().anyMatch(tempPattern::containsAll);
						if (murioshiFlag) {
							break;
						}
					}
					if (!murioshiFlag) {
						List<Integer> randomCombination = new ArrayList<>();
						for (int j = 0; j < 9; j++) {
							randomCombination.add(laneNumbers[j]);
						}
						noMurioshiLaneCombinations.add(randomCombination);
					}

					indexes[i]++;
					i = 0;
				} else {
					indexes[i] = 0;
					i++;
				}
			}

			noMurioshiLaneCombinations.remove(Arrays.asList(8, 7, 6, 5, 4, 3, 2, 1, 0));
			return noMurioshiLaneCombinations;
		}

		private void swap(int[] input, int a, int b) {
			int tmp = input[a];
			input[a] = input[b];
			input[b] = tmp;
		}
	}
}
