package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bms.model.Mode;
import bms.player.beatoraja.ir.IRCourseData;
import bms.player.beatoraja.song.SongData;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class BMSIRCourseQualificationTest {
	private static SongData song(String md5) {
		SongData song = new SongData();
		song.setMd5(md5);
		song.setSha256("sha" + md5);
		return song;
	}

	private static ScoreData score(int exscore, int minbp, int clear) {
		ScoreData score = new ScoreData(Mode.BEAT_7K);
		score.setNotes(200);
		score.setEpg(exscore / 2);
		score.setEgr(exscore % 2);
		score.setMinbp(minbp);
		score.setClear(clear);
		score.setCombo(180);
		return score;
	}

	private static IRCourseData.StageResult stage(String hash, int exscore, int minbp, int assist) {
		return new IRCourseData.StageResult(
				hash, 100, exscore, minbp, 5, 5, assist,
				exscore / 2, exscore % 2, 0, 0, 0, 90, 0, 0);
	}

	private static CourseData course() {
		CourseData course = new CourseData();
		course.setName("Qualification fixture");
		course.setSong(new SongData[] {song("a".repeat(32)), song("b".repeat(32))});
		return course;
	}

	private static Map<String, Object> rules() {
		return Map.of(
				"version", 1,
				"pass", Map.of(
						"overall", Map.of("min_ex_rate", 80, "max_bp", 20, "min_clear", 4),
						"stages", List.of(
								Map.of("min_ex_rate", 75, "max_bp", 12),
								Map.of("min_ex_rate", 85, "max_bp", 10))),
				"gold", Map.of("overall", Map.of("min_ex_rate", 90, "max_bp", 10)));
	}

	@Test
	void evaluatesPassAndGoldWithoutChangingSubmittedScore() {
		ScoreData score = score(360, 10, 5);
		BMSIRCourseQualification.Result result = BMSIRCourseQualification.evaluate(
				rules(), course(), score,
				new IRCourseData.StageResult[] {
						stage("a".repeat(32), 180, 5, 0),
						stage("b".repeat(32), 180, 5, 0),
				});
		assertEquals(BMSIRCourseQualification.Status.GOLD, result.status());
		assertEquals(5, score.getClear(), "the qualification result must not overwrite the real lamp");
	}

	@Test
	void rejectsFailedAndAssistedCoursesAndUnknownStageEvidence() {
		assertEquals(BMSIRCourseQualification.Status.FAIL,
				BMSIRCourseQualification.evaluate(
						rules(), course(), score(100, 15, 5),
						new IRCourseData.StageResult[] {
								stage("a".repeat(32), 50, 8, 0), stage("b".repeat(32), 50, 7, 0)
						}).status());
		assertEquals(BMSIRCourseQualification.Status.FAIL,
				BMSIRCourseQualification.evaluate(
							rules(), course(), score(360, 15, 5),
							new IRCourseData.StageResult[] {
									stage("a".repeat(32), 180, 8, 0), stage("b".repeat(32), 180, 7, 1)
						}).status());
		assertEquals(BMSIRCourseQualification.Status.UNKNOWN,
				BMSIRCourseQualification.evaluate(rules(), course(), score(180, 15, 5), null).status());
		assertEquals(BMSIRCourseQualification.Status.UNKNOWN,
				BMSIRCourseQualification.evaluate(
						rules(), course(), score(360, 15, 5),
						new IRCourseData.StageResult[] {
								stage("b".repeat(32), 180, 8, 0), stage("a".repeat(32), 180, 7, 0)
						}).status());
	}

	@Test
	void keepsUnconfiguredCoursesOnTheLegacyClearPath() {
		assertEquals(BMSIRCourseQualification.Status.NONE,
				BMSIRCourseQualification.evaluate(Map.of(), course(), score(100, 99, 1), null).status());
	}

	@Test
	void qualificationPlayConstraintsJoinTheStandardCourseConstraints() {
		CourseData course = course();
		course.setConstraint(new CourseData.CourseDataConstraint[] {
				CourseData.CourseDataConstraint.MIRROR,
				CourseData.CourseDataConstraint.GAUGE_LR2,
		});
		course.setBmsirQualification(Map.of(
				"play_constraints", List.of("no_speed", "no_good")));
		assertTrue(course.validate());
		assertTrue(Arrays.asList(course.getConstraint()).contains(CourseData.CourseDataConstraint.NO_SPEED));
		assertTrue(Arrays.asList(course.getConstraint()).contains(CourseData.CourseDataConstraint.NO_GOOD));
	}
}
