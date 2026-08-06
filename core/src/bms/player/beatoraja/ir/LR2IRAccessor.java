package bms.player.beatoraja.ir;

import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.ScoreDatabaseAccessor;
import bms.player.beatoraja.modmenu.ImGuiNotify;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import javafx.util.Pair;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.zip.GZIPInputStream;

/**
 * Original repo from https://github.com/SayakaIsBaka/lr2ir-read-only
 *
 * @author Sayaka, Catizard
 * @implNote This class is not a real IR connection, but the original repo is. It keeps the
 * original form to make things easier
 */
public class LR2IRAccessor {
	private static final String IRUrl = "https://www.bms-ir.org/LR2IR/2";
	private static final int CONNECT_TIMEOUT_MS = 5000;
	private static final int READ_TIMEOUT_MS = 30000;
	private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;
	private static final long RANKING_CACHE_MS = 30000L;
	private static final int RANKING_CACHE_SIZE = 32;
	private static ScoreDatabaseAccessor scoreDatabaseAccessor;

	private static final Map<String, RankingCacheEntry> bmsirRankingCache =
			new LinkedHashMap<>(RANKING_CACHE_SIZE + 1, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<String, RankingCacheEntry> eldest
				) {
					return size() > RANKING_CACHE_SIZE;
				}
			};

	public static void setScoreDatabaseAccessor(ScoreDatabaseAccessor scoreDatabaseAccessor) {
		LR2IRAccessor.scoreDatabaseAccessor = scoreDatabaseAccessor;
	}

	private static Object convertXMLToObject(String xml, Class c) {
		try {
			XmlMapper xmlMapper = new XmlMapper();
			xmlMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
			Object res = xmlMapper.readValue(xml, c);
			return res;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private static String makePOSTRequest(String uri, String data) {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(IRUrl + uri);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("Connection", "close");
			conn.setRequestProperty("Accept-Encoding", "gzip");
			conn.setDoOutput(true);
			byte[] requestBody = data.getBytes(StandardCharsets.US_ASCII);
			conn.setFixedLengthStreamingMode(requestBody.length);
			try (OutputStream os = conn.getOutputStream()) {
				os.write(requestBody);
			}

			int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new RuntimeException("HTTP error code: " + responseCode);
			}

			try (InputStream response = responseStream(conn)) {
				return readResponse(response, Charset.forName("windows-31j"));
			}
		} catch (Exception e) {
			ImGuiNotify.error("Failed to send request to BMS-IR: " + e.getMessage());
			return null;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private static String makeGETRequest(String uri) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(IRUrl + uri)
				.openConnection();
		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setRequestProperty("Accept-Encoding", "gzip");
			int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new RuntimeException("HTTP error code: " + responseCode);
			}
			try (InputStream response = responseStream(conn)) {
				return readResponse(response, Charset.forName("windows-31j"));
			}
		} finally {
			conn.disconnect();
		}
	}

	private static InputStream responseStream(HttpURLConnection conn)
			throws Exception {
		InputStream stream = conn.getInputStream();
		return "gzip".equalsIgnoreCase(conn.getContentEncoding())
				? new GZIPInputStream(stream)
				: stream;
	}

	private static String readResponse(InputStream stream, Charset charset)
			throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int total = 0;
		for (int read; (read = stream.read(buffer)) >= 0;) {
			total += read;
			if (total > MAX_RESPONSE_BYTES) {
				throw new IllegalStateException("BMS-IR response is too large");
			}
			output.write(buffer, 0, read);
		}
		return output.toString(charset);
	}

	private static LeaderboardEntry[] cachedRanking(String key) {
		synchronized (bmsirRankingCache) {
			RankingCacheEntry cached = bmsirRankingCache.get(key);
			if (cached == null
					|| System.currentTimeMillis() - cached.loadedAt >= RANKING_CACHE_MS) {
				return null;
			}
			return cached.entries;
		}
	}

	private static void cacheRanking(String key, LeaderboardEntry[] entries) {
		synchronized (bmsirRankingCache) {
			bmsirRankingCache.put(
					key,
					new RankingCacheEntry(System.currentTimeMillis(), entries)
			);
		}
	}

	private static String rankingXml(String response) {
		if (response == null) {
			throw new IllegalArgumentException("empty BMS-IR response");
		}
		int xmlStart = response.indexOf("<?xml");
		if (xmlStart < 0) {
			xmlStart = response.indexOf("<ranking");
		}
		if (xmlStart < 0) {
			throw new IllegalArgumentException("ranking XML is missing");
		}
		return response.substring(xmlStart)
				.replace("<lastupdate></lastupdate>", "");
	}

	/**
	 * Get LR2IR scores and personal score
	 *
	 * @param chart requested chart
	 * @implNote Technically speaking, this class shouldn't have the access of local scores. But this makes the code
	 * easier to assemble.
	 * @return A pair, first is local score and second is scores from LR2IR. The first can be null.
	 */
	public static Pair<IRScoreData, LeaderboardEntry[]> getScoreData(IRChartData chart) {
		if (chart.md5 == null || chart.md5.isEmpty()) {
			return new Pair<>(null, new LeaderboardEntry[0]);
		}
		LR2IRSongData lr2IRSongData = new LR2IRSongData(chart.md5, "0");
		try {
            String requestURL = lr2IRSongData.toUrlEncodedForm();
            LeaderboardEntry[] scoreData = cachedRanking(requestURL);
            if (scoreData == null) {
                String res = makePOSTRequest("/getrankingxml.cgi", requestURL);
				Ranking ranking = (Ranking) convertXMLToObject(
						rankingXml(res),
						Ranking.class
				);
				if (ranking == null) {
					throw new IllegalArgumentException("ranking XML could not be parsed");
				}
                scoreData = ranking.toBeatorajaScoreData(chart);
				cacheRanking(requestURL, scoreData);
            }
			ScoreData localScore = scoreDatabaseAccessor == null
					? null
					: scoreDatabaseAccessor.getScoreData(
							chart.sha256,
							chart.hasUndefinedLN ? chart.lntype : 0
					);
			if (localScore != null) {
				// This is intentional behaivor, see IRScoreData's player definition
				// and how we use this feature in LeaderBoardBar
				localScore.setPlayer("");
			}
			return new Pair<>(localScore == null ? null : new IRScoreData(localScore), scoreData);
		} catch (Exception e) {
			e.printStackTrace();
			ImGuiNotify.error("Failed to get score data from BMS-IR: " + e.getMessage());
			return new Pair<>(null, new LeaderboardEntry[0]);
		}
	}

    public static LR2GhostData getGhostData(String MD5, long scoreId) {
        String api = "/getghost.cgi?songmd5=" + MD5 + "&mode=top&targetid=" + scoreId;
        try {
			String body = makeGETRequest(api);
            return LR2GhostData.parse(body);
        }
        catch (Exception e) {
            e.printStackTrace();
			ImGuiNotify.error("Failed to load BMS-IR ghost data.");
            return null;
        }
    }

	private record RankingCacheEntry(long loadedAt, LeaderboardEntry[] entries) {
	}

	public static class LR2IRSongData {
		public String md5;
		public String id;
		public String lastUpdate;

		LR2IRSongData(String md5, String id) {
			this.md5 = md5;
			this.id = id;
			this.lastUpdate = "";
		}

		public String toUrlEncodedForm() {
			return "songmd5=" + md5 + "&id=" + id + "&lastupdate=" + lastUpdate;
		}
	}

	public static class Ranking {
		@JacksonXmlElementWrapper(useWrapping = false)
		private List<Score> score = new ArrayList<>();

		public List<Score> getScore() {
			return score;
		}

		public void setScore(List<Score> score) {
			this.score = score;
		}

		public LeaderboardEntry[] toBeatorajaScoreData(IRChartData model) {
			List<Score> scores = getScore();
			List<LeaderboardEntry> res = new ArrayList<>();
			for (Score s : scores) {
				ScoreData tmp = new ScoreData(model.mode);
				tmp.setSha256(model.sha256);
				tmp.setPlayer(s.getName());
				tmp.setClear(s.getBeatorajaClear());
				tmp.setNotes(s.getNotes());
				tmp.setCombo(s.getCombo());
				tmp.setEpg(s.getPg());
				tmp.setEgr(s.getGr());
				tmp.setMinbp(s.getMinbp());
                res.add(LeaderboardEntry.newEntryLR2IR(new IRScoreData(tmp), s.getId()));
			}
        /*if (lastScoreData != null && lastChart != null && lastChart.sha256.equals(model.sha256)) {
            System.out.println(lastScoreData.player);
            ScoreData tmp2 = new ScoreData(model.mode);
            tmp2.setSha256(model.sha256);
            tmp2.setPlayer(null);
            tmp2.setClear(lastScoreData.clear.id);
            tmp2.setNotes(lastScoreData.notes);
            tmp2.setCombo(lastScoreData.maxcombo);
            tmp2.setEpg(lastScoreData.epg);
            tmp2.setLpg(lastScoreData.lpg);
            tmp2.setEgr(lastScoreData.egr);
            tmp2.setLgr(lastScoreData.lgr);
            tmp2.setMinbp(lastScoreData.minbp);
            res.add(new IRScoreData(tmp2));
            lastScoreData = null;
            lastChart = null;
        } else*/
            ToIntFunction<LeaderboardEntry> leaderboardScore =
                (entry -> entry.getIrScore().getExscore());
            return res.stream()
                .sorted(Comparator.comparingInt(leaderboardScore).reversed())
                .toArray(LeaderboardEntry[]::new);
        }
	}

	public static class Score {
		private String name;
		private int id;
		private int clear;
		private int notes;
		private int combo;
		private int pg;
		private int gr;
		private int minbp;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public int getClear() {
			return clear;
		}

		public int getBeatorajaClear() {
			switch (clear) {
				case 1: // Failed
					return 1;
				case 2: // Easy
					return 4;
				case 3: // Groove
					return 5;
				case 4: // Hard
					return 6;
				case 5: // FC
					if (pg + gr == notes) // Perfect
						return 9;
					else
						return 8;
				default:
					return 0;
			}
		}

		public void setClear(int clear) {
			this.clear = clear;
		}

		public int getNotes() {
			return notes;
		}

		public void setNotes(int notes) {
			this.notes = notes;
		}

		public int getCombo() {
			return combo;
		}

		public void setCombo(int combo) {
			this.combo = combo;
		}

		public int getPg() {
			return pg;
		}

		public void setPg(int pg) {
			this.pg = pg;
		}

		public int getGr() {
			return gr;
		}

		public void setGr(int gr) {
			this.gr = gr;
		}

		public int getMinbp() {
			return minbp;
		}

		public void setMinbp(int minbp) {
			this.minbp = minbp;
		}
	}
}
