package bms.player.beatoraja.ir;

import bms.player.beatoraja.MainController;

/** Shared compatibility helpers for optional Internet Ranking implementations. */
public final class IRUtil {
    private IRUtil() {
    }

    public static String playerName(MainController main, String rankingPlayer) {
        if (rankingPlayer != null && !rankingPlayer.isBlank()) {
            return rankingPlayer;
        }
        if (main != null) {
            MainController.IRStatus[] statuses = main.getIRStatus();
            if (statuses != null && statuses.length > 0
                    && statuses[0] != null
                    && statuses[0].player != null
                    && statuses[0].player.name != null
                    && !statuses[0].player.name.isBlank()) {
                return statuses[0].player.name;
            }
        }
        return "YOU";
    }
}
