package bms.player.beatoraja.play;

import bms.model.BMSModel;
import bms.model.Mode;

/**
 * プレイヤールール
 * 
 * @author exch
 */
public enum BMSPlayerRule {
	Beatoraja_5(GaugeProperty.FIVEKEYS, JudgeProperty.FIVEKEYS, NoteJudgementBehavior.BEATORAJA, Mode.BEAT_5K, Mode.BEAT_10K),
	Beatoraja_7(GaugeProperty.SEVENKEYS, JudgeProperty.SEVENKEYS, NoteJudgementBehavior.BEATORAJA, Mode.BEAT_7K, Mode.BEAT_14K),
	Beatoraja_9(GaugeProperty.PMS, JudgeProperty.PMS, NoteJudgementBehavior.BEATORAJA, Mode.POPN_5K, Mode.POPN_9K),
	Beatoraja_24(GaugeProperty.KEYBOARD, JudgeProperty.KEYBOARD, NoteJudgementBehavior.BEATORAJA, Mode.KEYBOARD_24K, Mode.KEYBOARD_24K_DOUBLE),
	Beatoraja_Other(GaugeProperty.SEVENKEYS, JudgeProperty.SEVENKEYS, NoteJudgementBehavior.BEATORAJA),

	LR2(GaugeProperty.LR2, JudgeProperty.LR2, NoteJudgementBehavior.LR2ORAJA),

	Default(GaugeProperty.SEVENKEYS, JudgeProperty.SEVENKEYS, NoteJudgementBehavior.BEATORAJA),
;

	public static final String PROFILE_LR2 = "lr2";
	public static final String PROFILE_ORAJA = "oraja";

	private static volatile String configuredProfile = PROFILE_LR2;
	private static volatile String arenaProfileOverride;

	/**
	 * ゲージ仕様
	 */
    public final GaugeProperty gauge;
	/**
	 * 判定仕様
	 */
    public final JudgeProperty judge;
	/**
	 * Note-selection behavior which is not represented by the judge windows.
	 */
	public final NoteJudgementBehavior noteJudgement;
	/**
	 * 対象モード。全モード対象の場合は空列
	 */
	public final Mode[] mode;

    private BMSPlayerRule(GaugeProperty gauge, JudgeProperty judge, NoteJudgementBehavior noteJudgement, Mode... mode) {
        this.gauge = gauge;
        this.judge = judge;
		this.noteJudgement = noteJudgement;
        this.mode = mode;
    }

	public enum NoteJudgementBehavior {
		BEATORAJA(false, false),
		LR2ORAJA(true, true),
		;

		private final boolean multipleBadNotesPerPress;
		private final boolean suppressLongNoteLateBad;

		NoteJudgementBehavior(boolean multipleBadNotesPerPress, boolean suppressLongNoteLateBad) {
			this.multipleBadNotesPerPress = multipleBadNotesPerPress;
			this.suppressLongNoteLateBad = suppressLongNoteLateBad;
		}

		public boolean allowsMultipleBadNotesPerPress() {
			return multipleBadNotesPerPress;
		}

		public boolean suppressesLongNoteLateBad() {
			return suppressLongNoteLateBad;
		}
	}

	public static String normalizeRuleProfile(String profile) {
		return PROFILE_ORAJA.equalsIgnoreCase(profile) ? PROFILE_ORAJA : PROFILE_LR2;
	}

	public static void setConfiguredRuleProfile(String profile) {
		configuredProfile = normalizeRuleProfile(profile);
	}

	public static String getConfiguredRuleProfileId() {
		return configuredProfile;
	}

	public static String getActiveRuleProfileId() {
		String override = arenaProfileOverride;
		return override == null ? configuredProfile : override;
	}

	public static void setArenaRuleProfileOverride(String profile) {
		arenaProfileOverride = normalizeRuleProfile(profile);
	}

	public static void clearArenaRuleProfileOverride() {
		arenaProfileOverride = null;
	}

    public static BMSPlayerRule getBMSPlayerRule(Mode mode) {
		BMSPlayerRuleSet activeSet = PROFILE_ORAJA.equals(getActiveRuleProfileId())
				? BMSPlayerRuleSet.Beatoraja
				: BMSPlayerRuleSet.LR2;
        for(BMSPlayerRule bmsrule : activeSet.ruleset) {
        	if(bmsrule.mode.length == 0) {
    			return bmsrule; 
        	}
        	for(Mode m : bmsrule.mode) {
        		if(mode == m) {
        			return bmsrule;
        		}
        	}
        }
        return activeSet == BMSPlayerRuleSet.LR2 ? LR2 : Beatoraja_Other;
    }
    
    public static void validate(BMSModel model) {
    	BMSPlayerRule rule = getBMSPlayerRule(model.getMode());
    	final int judgerank = model.getJudgerank();
    	switch(model.getJudgerankType()) {
    	case BMS_RANK:
			model.setJudgerank(judgerank >= 0 && model.getJudgerank() < 5 ? rule.judge.windowrule.judgerank[judgerank] : rule.judge.windowrule.judgerank[2]);
    		break;
    	case BMS_DEFEXRANK:
			model.setJudgerank(judgerank > 0 ? judgerank * rule.judge.windowrule.judgerank[2] / 100 : rule.judge.windowrule.judgerank[2]);
    		break;
    	case BMSON_JUDGERANK:
			model.setJudgerank(judgerank > 0 ? judgerank : 100);
    		break;
    	}
    	model.setJudgerankType(BMSModel.JudgeRankType.BMSON_JUDGERANK);
		
    	switch(model.getTotalType()) {
    	case BMS:
			// TOTAL未定義の場合
			if (model.getTotal() <= 0.0) {
				model.setTotal(calculateDefaultTotal(model.getMode(), model.getTotalNotes()));
			}			
    		break;
    	case BMSON:
    		final double total = calculateDefaultTotal(model.getMode(), model.getTotalNotes());
			model.setTotal(model.getTotal() > 0 ? model.getTotal() / 100.0 * total : total);
    		break;
    	}
    	model.setTotalType(BMSModel.TotalType.BMS);
    }
    
	static double calculateDefaultTotal(Mode mode, int totalnotes) {
		if (PROFILE_LR2.equals(getActiveRuleProfileId())) {
			return 160.0 + (totalnotes + Math.min(Math.max(totalnotes - 400, 0), 200)) * 0.16;
		}
		switch (mode) {
		case BEAT_7K:
		case BEAT_5K:
		case BEAT_14K:
		case BEAT_10K:
		case POPN_9K:
		case POPN_5K:
			return Math.max(260.0, 7.605 * totalnotes / (0.01 * totalnotes + 6.5));
		case KEYBOARD_24K:
		case KEYBOARD_24K_DOUBLE:
			return Math.max(300.0, 7.605 * (totalnotes + 100) / (0.01 * totalnotes + 6.5));
		default:
			return Math.max(260.0, 7.605 * totalnotes / (0.01 * totalnotes + 6.5));
		}
	}
}

enum BMSPlayerRuleSet {
	
	Beatoraja(BMSPlayerRule.Beatoraja_5, BMSPlayerRule.Beatoraja_7, BMSPlayerRule.Beatoraja_9, BMSPlayerRule.Beatoraja_24,  BMSPlayerRule.Beatoraja_Other),
	LR2(BMSPlayerRule.LR2);
	
	public final BMSPlayerRule[] ruleset;
	
    private BMSPlayerRuleSet(BMSPlayerRule... ruleset) {
    	this.ruleset = ruleset;
    }
}
