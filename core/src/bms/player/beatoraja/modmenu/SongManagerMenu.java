package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.arena.bmsir.BMSIRArenaI18n;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.SongBar;
import bms.player.beatoraja.song.SongData;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.text.SimpleDateFormat;
import java.util.*;


public class SongManagerMenu {
    // I cannot think of a better solution than hold a ref of MusicSelector
    private static MusicSelector selector;
    /**
     * Current song's reverse lookup result
     */
    private static List<String> currentReverseLookupList = new ArrayList<>();

    private static ImBoolean LAST_PLAYED_SORT = new ImBoolean(false);
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static String t(String japanese, String english) {
        return BMSIRArenaI18n.text(japanese, english);
    }

    public static void show(ImBoolean showSongManager) {
        Optional<SongData> currentSongData = getCurrentSongData();
        Optional<ScoreData> currentScoreData = getCurrentScoreData();
        if (ImGui.begin(t("楽曲管理", "Song Manager") + "###song-manager",
                showSongManager, ImGuiWindowFlags.AlwaysAutoResize)) {
            String songName = currentSongData.map(SongData::getTitle).orElse("");
            String lastPlayRecordTime = currentScoreData.map(scoreData -> {
                Date date = new Date(scoreData.getDate() * 1000L);
                return simpleDateFormat.format(date);
            }).orElse(t("未プレイ", "Not played"));
            ImGui.text(t("選択中: ", "Selected: ") + songName);

            ImGui.text(t("最終プレイ: ", "Last played: ") + lastPlayRecordTime);
            if (ImGui.checkbox(t("最終プレイ順に並べる", "Sort by last played"), LAST_PLAYED_SORT)) {
                selector.getBarManager().updateBar();
            }

            if (songName.isEmpty()) {
                ImGui.text(t("選択可能な楽曲ではありません", "Not a selectable song"));
            } else {
                if (ImGui.button(t("所属フォルダを表示", "Show Reverse Lookup"))) {
                    updateReverseLookupData(currentSongData);
                    ImGui.openPopup("Reverse Lookup");
                }
                if (ImGui.beginPopup("Reverse Lookup", ImGuiWindowFlags.AlwaysAutoResize)) {
                    for (int i = 0;i < currentReverseLookupList.size();++i) {
                        ImGui.pushID(i);
                        ImGui.bulletText(currentReverseLookupList.get(i));
                        ImGui.popID();
                    }
                    ImGui.endPopup();
                }
            }
        }
        ImGui.end();
    }

    public static void injectMusicSelector(MusicSelector musicSelector) {
        selector = musicSelector;
    }

    /**
     * Update current reverse lookup result by current song data
     *
     * @param currentSongData clear reverse lookup result if empty
     */
    private static void updateReverseLookupData(Optional<SongData> currentSongData) {
        if (currentSongData.isEmpty()) {
            currentReverseLookupList.clear();
            return ;
        }

        // Current song data is not used in this call, consider deleting upstream of this function
        // getReverseLookupData uses the selectors resource object to get data for what song is currently selected
        currentReverseLookupList = getReverseLookupData();
    }

    private static Optional<SongData> getCurrentSongData() {
        if (selector.getSelectedBar() instanceof SongBar) {
            final SongData sd = ((SongBar) selector.getSelectedBar()).getSongData();
            if (sd != null && sd.getPath() != null) {
                return Optional.of(sd);
            }
        }
        return Optional.empty();
    }

    private static Optional<ScoreData> getCurrentScoreData() {
        if (selector.getSelectedBar() instanceof SongBar) {
            final ScoreData sd = ((SongBar) selector.getSelectedBar()).getScore();
            return Optional.ofNullable(sd);
        }
        return Optional.empty();
    }

    private static List<String> getReverseLookupData() {
        return selector.main.getPlayerResource().getReverseLookupData();
    }

    public static boolean isLastPlayedSortEnabled() {
        return LAST_PLAYED_SORT.get();
    }

    public static void forceDisableLastPlayedSort() {
        LAST_PLAYED_SORT.set(false);
    }
}
