package bms.player.beatoraja;

import org.junit.jupiter.api.Test;

import static bms.player.beatoraja.CourseData.CourseDataConstraint.GAUGE_7KEYS;
import static bms.player.beatoraja.CourseData.CourseDataConstraint.NO_GREAT;
import static bms.player.beatoraja.CourseData.CourseDataConstraint.NO_SPEED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayDataAccessorCourseModeTest {
    @Test
    void courseDeletionUsesTheSameEncodedModesAsCourseScores() {
        CourseData.CourseDataConstraint[] constraints = {NO_SPEED, NO_GREAT, GAUGE_7KEYS};

        assertEquals(32121, PlayDataAccessor.courseScoreMode(true, 1, 2, constraints));
        assertEquals(32120, PlayDataAccessor.courseScoreMode(false, 2, 2, constraints));
    }
}
