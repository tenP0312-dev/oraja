package bms.player.beatoraja.launcher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SidebarSearchIndexTest {
	@Test
	void filtersLocalizedSettingTitlesAndDescriptions() {
		SidebarSearchIndex<String> index = new SidebarSearchIndex<>();
		index.add("video", "画面", "表示先と描画負荷を決めます");
		index.add("video", "画面モード", "ウィンドウ、ボーダーレス、フルスクリーンから選びます");
		index.add("audio", "音声", "出力方式と遅延を調整します");

		assertEquals(List.of("video"), index.filter(List.of("video", "audio"), "画面モード"));
		assertEquals(List.of("video"), index.filter(List.of("video", "audio"), "ボーダーレス"));
		assertEquals(List.of("audio"), index.filter(List.of("video", "audio"), "遅延"));
	}

	@Test
	void normalizesCaseAndFullWidthCharacters() {
		SidebarSearchIndex<String> index = new SidebarSearchIndex<>();
		index.add("video", "Maximum FPS", "Cap rendering when Vsync is off");

		assertEquals(List.of("video"), index.filter(List.of("video"), "maximum fps"));
		assertEquals(List.of("video"), index.filter(List.of("video"), "ＶＳＹＮＣ"));
	}

	@Test
	void keepsOrderForEmptyQueriesAndReturnsNoFalseMatch() {
		SidebarSearchIndex<String> index = new SidebarSearchIndex<>();
		index.add("video", "Video");
		index.add("audio", "Audio");

		assertEquals(List.of("video", "audio"), index.filter(List.of("video", "audio"), "  "));
		assertEquals(List.of(), index.filter(List.of("video", "audio"), "network"));
	}

	@Test
	void choosesTheCurrentMatchOrFallsBackToTheFirstResult() {
		assertEquals("audio", SidebarSearchIndex.preferredSelection(List.of("video", "audio"), "audio"));
		assertEquals("video", SidebarSearchIndex.preferredSelection(List.of("video", "audio"), "other"));
		assertNull(SidebarSearchIndex.preferredSelection(List.of(), "video"));
	}
}
