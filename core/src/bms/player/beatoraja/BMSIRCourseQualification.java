package bms.player.beatoraja;

import bms.player.beatoraja.ir.IRCourseData;
import bms.player.beatoraja.song.SongData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local result-screen evaluation for optional BMS-IR user-course rules. */
public final class BMSIRCourseQualification {
	public enum Status { NONE, PASS, GOLD, FAIL, UNKNOWN }

	public record Result(Status status, String reason) {
		public boolean hasRules() { return status != Status.NONE; }
		public boolean passed() { return status == Status.PASS || status == Status.GOLD; }
	}

	private enum TierState { PASS, FAIL, UNKNOWN }

	private BMSIRCourseQualification() {}

	public static Result evaluate(
			Map<String, Object> rules,
			CourseData course,
			ScoreData overall,
			IRCourseData.StageResult[] stages) {
		if (rules == null || rules.isEmpty()) return new Result(Status.NONE, "");
		if (number(rules.get("version"), -1) != 1 || course == null || overall == null) {
			return new Result(Status.UNKNOWN, "invalid_rules_or_result");
		}
		Object passRaw = rules.get("pass");
		if (!(passRaw instanceof Map<?, ?> pass)) return new Result(Status.UNKNOWN, "missing_pass_rules");
		if (stages == null || stages.length != course.getSong().length) {
			return new Result(Status.UNKNOWN, "stage_evidence_missing");
		}
		for (int index = 0; index < stages.length; index++) {
			SongData expected = course.getSong()[index];
			IRCourseData.StageResult actual = stages[index];
			if (actual == null || expected == null || !sameChart(expected, actual.chartHash)) {
				return new Result(Status.UNKNOWN, "stage_chart_order_mismatch");
			}
			if (actual.assist != 0) return new Result(Status.FAIL, "assisted_play");
		}
		if (!aggregateMatches(overall, stages)) return new Result(Status.UNKNOWN, "stage_aggregate_mismatch");
		if (overall.getClear() == ClearType.Failed.id) return new Result(Status.FAIL, "course_not_cleared");
		if (overall.getClear() == ClearType.AssistEasy.id || overall.getClear() == ClearType.LightAssistEasy.id) {
			return new Result(Status.FAIL, "assisted_course_clear");
		}
		Object goldRaw = rules.get("gold");
		if (goldRaw instanceof Map<?, ?> gold) {
			TierState goldState = evaluateTier(merge(pass, gold, stages.length), overall, stages);
			if (goldState == TierState.PASS) return new Result(Status.GOLD, "");
			if (goldState == TierState.UNKNOWN
					&& evaluateTier(pass, overall, stages) == TierState.UNKNOWN) {
				return new Result(Status.UNKNOWN, "missing_rule_facts");
			}
		}
		return switch (evaluateTier(pass, overall, stages)) {
			case PASS -> new Result(Status.PASS, "");
			case FAIL -> new Result(Status.FAIL, "requirements_not_met");
			case UNKNOWN -> new Result(Status.UNKNOWN, "missing_rule_facts");
		};
	}

	private static TierState evaluateTier(
			Map<?, ?> tier,
			ScoreData overall,
			IRCourseData.StageResult[] stages) {
		Object overallRaw = tier.get("overall");
		if (!(overallRaw instanceof Map<?, ?> overallRules)) return TierState.UNKNOWN;
		TierState overallResult = evaluateScore(overallRules, overall);
		if (overallResult != TierState.PASS) return overallResult;
		Object stagesRaw = tier.get("stages");
		if (stagesRaw == null) return TierState.PASS;
		if (!(stagesRaw instanceof List<?> rules)
				|| (!rules.isEmpty() && rules.size() != stages.length)) return TierState.UNKNOWN;
		if (rules.isEmpty()) return TierState.PASS;
		for (int index = 0; index < stages.length; index++) {
			if (!(rules.get(index) instanceof Map<?, ?> stageRules)) return TierState.UNKNOWN;
			TierState stageResult = evaluateScore(stageRules, stages[index]);
			if (stageResult != TierState.PASS) return stageResult;
		}
		return TierState.PASS;
	}

	private static TierState evaluateScore(Map<?, ?> rules, Object score) {
		for (Map.Entry<?, ?> entry : rules.entrySet()) {
			if (!(entry.getKey() instanceof String name)) return TierState.UNKNOWN;
			Number limit = numeric(entry.getValue());
			if (limit == null) return TierState.UNKNOWN;
			double value = switch (name) {
				case "min_ex_rate" -> {
					int ex = fact(score, "exscore");
					int notes = fact(score, "totalnotes");
					if (ex < 0 || notes <= 0 || ex > notes * 2) yield Double.NaN;
					yield ex * 100.0 / (notes * 2.0);
				}
				case "max_bp" -> fact(score, "minbp");
				case "min_clear" -> fact(score, "clear");
				case "min_pgreat" -> fact(score, "pg");
				case "max_great" -> fact(score, "gr");
				case "max_good" -> fact(score, "gd");
				case "max_bad" -> fact(score, "bd");
				case "max_poor" -> fact(score, "pr");
				case "min_combo" -> fact(score, "maxcombo");
				default -> Double.NaN;
			};
			if (!Double.isFinite(value)) return TierState.UNKNOWN;
			if (name.equals("min_ex_rate")) {
				int exscore = fact(score, "exscore");
				int notes = fact(score, "totalnotes");
				BigDecimal achieved = BigDecimal.valueOf((long) exscore * 100);
				BigDecimal required = BigDecimal.valueOf(limit.doubleValue())
						.multiply(BigDecimal.valueOf((long) notes * 2));
				if (achieved.compareTo(required) < 0) return TierState.FAIL;
				continue;
			}
			if (name.startsWith("min_") && value < limit.doubleValue()) return TierState.FAIL;
			if (name.startsWith("max_") && value > limit.doubleValue()) return TierState.FAIL;
		}
		return TierState.PASS;
	}

	private static int fact(Object score, String name) {
		if (score instanceof ScoreData data) {
			return switch (name) {
				case "exscore" -> data.getExscore();
				case "totalnotes" -> data.getNotes();
				case "minbp" -> data.getMinbp();
				case "clear" -> data.getClear();
				case "pg" -> data.getEpg() + data.getLpg();
				case "gr" -> data.getEgr() + data.getLgr();
				case "gd" -> data.getEgd() + data.getLgd();
				case "bd" -> data.getEbd() + data.getLbd();
				case "pr" -> data.getEpr() + data.getLpr() + data.getEms() + data.getLms();
				case "maxcombo" -> data.getCombo();
				default -> -1;
			};
		}
		if (score instanceof IRCourseData.StageResult stage) {
			return switch (name) {
				case "exscore" -> stage.exscore;
				case "totalnotes" -> stage.totalNotes;
				case "minbp" -> stage.minbp;
				case "clear" -> stage.clearType;
				case "pg" -> stage.pg;
				case "gr" -> stage.gr;
				case "gd" -> stage.gd;
				case "bd" -> stage.bd;
				case "pr" -> stage.pr;
				case "maxcombo" -> stage.maxcombo;
				default -> -1;
			};
		}
		return -1;
	}

	private static boolean aggregateMatches(ScoreData overall, IRCourseData.StageResult[] stages) {
		return overall.getNotes() == sum(stages, "totalnotes")
				&& overall.getExscore() == sum(stages, "exscore")
				&& overall.getMinbp() == sum(stages, "minbp")
				&& overall.getEpg() + overall.getLpg() == sum(stages, "pg")
				&& overall.getEgr() + overall.getLgr() == sum(stages, "gr")
				&& overall.getEgd() + overall.getLgd() == sum(stages, "gd")
				&& overall.getEbd() + overall.getLbd() == sum(stages, "bd")
				&& overall.getEpr() + overall.getLpr() + overall.getEms() + overall.getLms() == sum(stages, "pr");
	}

	private static int sum(IRCourseData.StageResult[] stages, String field) {
		int total = 0;
		for (IRCourseData.StageResult stage : stages) {
			total += switch (field) {
				case "totalnotes" -> stage.totalNotes;
				case "exscore" -> stage.exscore;
				case "minbp" -> stage.minbp;
				case "pg" -> stage.pg;
				case "gr" -> stage.gr;
				case "gd" -> stage.gd;
				case "bd" -> stage.bd;
				case "pr" -> stage.pr;
				default -> Integer.MIN_VALUE;
			};
		}
		return total;
	}

	private static Map<String, Object> merge(Map<?, ?> pass, Map<?, ?> gold, int stageCount) {
		Map<String, Object> result = new HashMap<>();
		copyRules(pass, result);
		Object passOverall = pass.get("overall");
		Object goldOverall = gold.get("overall");
		if (passOverall instanceof Map<?, ?> passMap && goldOverall instanceof Map<?, ?> goldMap) {
			result.put("overall", mergeMaps(passMap, goldMap));
		}
		Object passStages = pass.get("stages");
		Object goldStages = gold.get("stages");
		if (goldStages instanceof List<?> goldList && !goldList.isEmpty()) {
			List<?> passList = passStages instanceof List<?> list ? list : List.of();
			List<Map<String, Object>> merged = new ArrayList<>();
			for (int index = 0; index < stageCount; index++) {
				Map<?, ?> passStage = index < passList.size() && passList.get(index) instanceof Map<?, ?> map ? map : Map.of();
				Map<?, ?> goldStage = index < goldList.size() && goldList.get(index) instanceof Map<?, ?> map ? map : Map.of();
				merged.add(mergeMaps(passStage, goldStage));
			}
			result.put("stages", merged);
		}
		return result;
	}

	private static Map<String, Object> mergeMaps(Map<?, ?> first, Map<?, ?> second) {
		Map<String, Object> result = new HashMap<>();
		copyRules(first, result);
		copyRules(second, result);
		return result;
	}

	private static void copyRules(Map<?, ?> source, Map<String, Object> target) {
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (entry.getKey() instanceof String key) target.put(key, entry.getValue());
		}
	}

	private static boolean sameChart(SongData song, String hash) {
		return hash != null && (hash.equalsIgnoreCase(song.getMd5()) || hash.equalsIgnoreCase(song.getSha256()));
	}

	private static Number numeric(Object value) {
		return value instanceof Number number ? number : null;
	}

	private static int number(Object value, int fallback) {
		return value instanceof Number number ? number.intValue() : fallback;
	}
}
