package bms.player.beatoraja.ir;

import bms.player.beatoraja.PlayerConfig;

/** Immutable interpretation shared by cache lookup and the corresponding request. */
public record IRRankingContext(int lnmode, boolean forceLn) {
    public IRRankingContext {
        if (forceLn) lnmode = 0;
    }

    public static IRRankingContext from(PlayerConfig config) {
        boolean forceLn = config.isBmsirForceLn();
        return new IRRankingContext(config.getSelectedLnmode(), forceLn);
    }
}
