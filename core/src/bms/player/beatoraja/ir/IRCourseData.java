package bms.player.beatoraja.ir;

import bms.player.beatoraja.CourseData;
import bms.player.beatoraja.CourseData.CourseDataConstraint;

/**
 * IR用コースデータ
 * 
 * @author exch
 */
public class IRCourseData {

    /**
     * コース名
     */
    public final String name;
    /**
     * 楽曲のハッシュ
     */
    public final IRChartData[] charts;
    /**
     * コースの制限
     */
    public final CourseDataConstraint[] constraint;
    /**
     * トロフィーデータ
     */
    public final IRTrophyData[] trophy;

    /** Ordered current-play stage facts used only by the BMS-IR evidence extension. */
    public final StageResult[] bmsirStages;
	/**
	 * LN TYPE(-1 : 未指定, 0: LN, 1: CN, 2: HCN)
	 */
	public final int lntype;

    public IRCourseData(CourseData course) {
    	this(course, -1);
    }

    public IRCourseData(CourseData course, int lntype) {
        this(course, lntype, false);
    }

    public IRCourseData(CourseData course, int lntype, boolean forceLn) {
		this(course, lntype, forceLn, null);
	}

	public IRCourseData(CourseData course, int lntype, boolean forceLn, StageResult[] bmsirStages) {
    	this.name = course.getName();
    	this.charts = new IRChartData[course.getSong().length];
    	for(int i = 0;i < this.charts.length;i++) {
            charts[i] = forceLn ? IRChartData.forRanking(course.getSong()[i], 0, true)
                    : new IRChartData(course.getSong()[i]);
    	}
    	this.constraint = new CourseDataConstraint[course.getConstraint().length];
    	for(int i = 0;i < this.constraint.length;i++) {
    		constraint[i] =course.getConstraint()[i];
    	}
        this.lntype = forceLn ? 0 : lntype;
		this.bmsirStages = bmsirStages == null ? null : bmsirStages.clone();

    	this.trophy = new IRTrophyData[course.getTrophy().length];
    	for(int i = 0; i < trophy.length;i++) {
    		trophy[i] = new IRTrophyData(course.getTrophy()[i]);
		}
    }

	/**
	 * IR用トロフィーデータ
	 *
	 * @author exch
	 */
	public static class IRTrophyData {
		/**
		 * トロフィー名称
		 */
    	public final String name;
		/**
		 * トロフィーのスコアレート条件
		 */
		public final float scorerate;
		/**
		 * トロフィーのミスレート条件
		 */
		public final float smissrate;

    	public IRTrophyData (CourseData.TrophyData trophy) {
    		name = trophy.getName();
    		scorerate = trophy.getScorerate();
			smissrate = trophy.getMissrate();
		}
	}

	public static final class StageResult {
		public final String chartHash;
		public final int totalNotes;
		public final int exscore;
		public final int minbp;
		public final int clear;
		public final int clearType;
		public final int assist;
		public final int pg;
		public final int gr;
		public final int gd;
		public final int bd;
		public final int pr;
		public final int maxcombo;
		public final int option;
		public final int lntype;

		public StageResult(String chartHash, int totalNotes, int exscore, int minbp,
				int clear, int clearType, int assist, int pg, int gr, int gd, int bd, int pr,
				int maxcombo, int option, int lntype) {
			this.chartHash = chartHash;
			this.totalNotes = totalNotes;
			this.exscore = exscore;
			this.minbp = minbp;
			this.clear = clear;
			this.clearType = clearType;
			this.assist = assist;
			this.pg = pg;
			this.gr = gr;
			this.gd = gd;
			this.bd = bd;
			this.pr = pr;
			this.maxcombo = maxcombo;
			this.option = option;
			this.lntype = lntype;
		}
	}
}
