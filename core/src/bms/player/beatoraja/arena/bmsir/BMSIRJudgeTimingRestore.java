package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.PlayerConfig;

/** Keeps automatic judge-timing changes local to one play when requested. */
public final class BMSIRJudgeTimingRestore {
    private boolean active;
    private int judgeTimingBeforePlay;

    public void begin(PlayerConfig player) {
        restore(player);
        if (player == null
                || !player.isBmsirJudgeTimingRestoreEnabled()
                || !player.isNotesDisplayTimingAutoAdjust()) {
            return;
        }
        judgeTimingBeforePlay = player.getJudgetiming();
        active = true;
    }

    public boolean restore(PlayerConfig player) {
        if (!active) {
            return false;
        }
        if (player != null) {
            player.setJudgetiming(judgeTimingBeforePlay);
        }
        active = false;
        return true;
    }

    boolean isActive() {
        return active;
    }
}
