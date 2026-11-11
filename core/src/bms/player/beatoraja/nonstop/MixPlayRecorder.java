package bms.player.beatoraja.nonstop;

import bms.player.beatoraja.BMSPlayerMode;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Turns the end of a played set into a {@link MixResult} and keeps it. */
public final class MixPlayRecorder {
    private static final Logger logger = LoggerFactory.getLogger(MixPlayRecorder.class);

    private MixPlayRecorder() {
    }

    public static void record(MainController main, PlayerResource resource) {
        MixRuntime.Session session = MixRuntime.session();
        ScoreData score = resource.getScoreData();
        if (session == null || score == null) {
            return;
        }
        CompiledMix mix = session.mix();
        MixResult result = new MixResult();
        result.identity = mix.identity();
        result.name = session.title();
        result.irId = mix.plan().irId;
        result.irRevision = mix.plan().irRevision;
        result.exScore = score.getExscore();
        result.notes = score.getNotes();
        result.maxExScore = score.getNotes() * 2;
        result.clear = score.getClear();
        result.gauge = resource.getGrooveGauge() != null ? resource.getGrooveGauge().getType() : score.getGauge();
        result.badPoor = score.getMinbp();
        result.maxCombo = score.getCombo();
        result.tracks.addAll(MixResult.perTrack(mix, resource.getBMSModel()));
        int unreached = 0;
        for (MixResult.TrackResult track : result.tracks) {
            unreached += track.unreached;
        }
        result.finished = unreached == 0;
        MixRuntime.setLastResult(result);

        boolean recordable = session.kind() == MixRuntime.Kind.FULL
                && resource.getPlayMode().mode == BMSPlayerMode.Mode.PLAY;
        if (!recordable) {
            return;
        }
        try {
            MixStore.forPlayer(main.getConfig().getPlayerpath(), main.getConfig().getPlayername()).addRecord(result);
        } catch (Exception e) {
            logger.warn("NONSTOP MIX record could not be saved: {}", e.getMessage());
            ImGuiNotify.warning(text("NONSTOP MIXの記録を保存できませんでした", "Could not save the NONSTOP MIX record"));
        }
        if (!result.irId.isEmpty() && result.finished) {
            MixIrClient.submitScoreAsync(main, mix, result);
        }
    }

    private static String text(String japanese, String english) {
        return bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n.text(japanese, english);
    }
}
