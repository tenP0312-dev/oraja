package bms.player.beatoraja.play;

import bms.model.BMSModel;
import bms.player.beatoraja.ClearType;
import bms.player.beatoraja.ReplayData;
import bms.player.beatoraja.arena.bmsir.BMSIRManiacSettings;

/** Shares the existing course per-song rules without inventing a course identity. */
public final class CourseGaugePolicy {
    private CourseGaugePolicy() {}

    public static int initialValue(BMSIRManiacSettings settings, ReplayData replay,
            boolean replayMode, boolean blocked) {
        if (blocked) return 0;
        // Old and missing replays must never inherit the current player's switch.
        int value = replayMode
                ? (replay == null ? 0 : replay.bmsirCourseGaugeInitialValue)
                : (settings.isCourseGauge() ? settings.getCourseGaugeInitialValue() : 0);
        return value <= 0 ? 0 : Math.max(2, Math.min(100, value)) / 2 * 2;
    }

    public static GrooveGauge create(BMSModel model, int type, int initialValue) {
        GrooveGauge gauge = GrooveGauge.create(model, type, 1, null);
        for (int i = GrooveGauge.CLASS; i <= GrooveGauge.EXHARDCLASS; i++) {
            gauge.setValue(i, initialValue);
        }
        return gauge;
    }

    public static ClearType clearType(boolean course, boolean failed, boolean qualified,
            int assist, boolean fullCombo, int good, int great, ClearType gaugeClear) {
        if (failed || !qualified) return ClearType.Failed;
        if (assist > 0) return course ? ClearType.Failed
                : (assist == 1 ? ClearType.LightAssistEasy : ClearType.AssistEasy);
        if (fullCombo) {
            return good > 0 ? ClearType.FullCombo
                    : (great > 0 ? ClearType.Perfect : ClearType.Max);
        }
        return course ? ClearType.Failed : gaugeClear;
    }

    public static int perSongClear(boolean course, int clear) {
        return course && clear == ClearType.Failed.id ? ClearType.NoPlay.id : clear;
    }
}
