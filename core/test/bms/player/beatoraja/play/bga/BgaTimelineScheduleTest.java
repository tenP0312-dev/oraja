package bms.player.beatoraja.play.bga;

import bms.model.TimeLine;
import com.badlogic.gdx.graphics.Texture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BgaTimelineScheduleTest {
    private static final MovieProcessor MOVIE = new MovieProcessor() {
        public Texture getFrame(long time) { throw new AssertionError("schedule must not decode"); }
        public void play(long time, boolean loop) { throw new AssertionError(); }
        public void stop() {} public void dispose() {}
    };
    private static TimeLine tl(long ms, int main, int layer) {
        var tl = new TimeLine(0, ms * 1000, 0); tl.setBGA(main); tl.setLayer(layer); return tl;
    }
    @Test void firstAndNextDistinctAreSeparatedByTrackAndSkipStaticResources() {
        var schedule = new BgaTimelineSchedule(new TimeLine[]{
                tl(0,0,-1), tl(100,0,1), tl(200,2,-1), tl(300,1,0)},
                new MovieProcessor[]{MOVIE,MOVIE,null});
        assertEquals(0,schedule.first(false).timeMs());
        assertEquals(100,schedule.first(true).timeMs());
        assertEquals(300,schedule.nextDistinctMovieAfter(0,0,false).timeMs());
        assertEquals(100,schedule.nextDistinctMovieAfter(0,-1,true).timeMs());
        assertNull(schedule.nextDistinctMovieAfter(300,1,false));
    }
    @Test void absentAndInvalidIdsNeverResolveAResource() {
        var schedule = new BgaTimelineSchedule(new TimeLine[]{tl(0,-2,999)},new MovieProcessor[]{null});
        assertNull(schedule.first(false)); assertNull(schedule.first(true));
    }
}
