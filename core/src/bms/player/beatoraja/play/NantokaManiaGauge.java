package bms.player.beatoraja.play;

/** Integer gauge arithmetic for the user-specified NANTOKA MANIA rules. */
public final class NantokaManiaGauge {
    private final int type;
    private final int recovery;
    private final int goodRecovery;
    private int value;

    public NantokaManiaGauge(int type, int notes) {
        this.type = type;
        long effective = notes < 350 ? Math.max(0, notes) : 350L + (notes - 350L) / 3;
        recovery = effective == 0 ? 0 : (int) (80_000 / (6 * effective));
        goodRecovery = effective == 0 ? 0 : (int) (40_000 / (6 * effective));
        value = type <= GrooveGauge.NORMAL ? 1100 : 5000;
    }

    public int units() { return value; }
    public float percent() { return value / 50f; }
    public int displayPercent() { return value / 100 * 2; }

    public void setPercent(float percent) { setUnits(Math.round(percent * 50)); }

    void setUnits(int units) {
        if (value == 0) return;
        value = Math.max(type <= GrooveGauge.NORMAL ? 100 : 0, Math.min(5000, units));
        if ((type == GrooveGauge.HARD || type == GrooveGauge.EXHARD) && value < 100) value = 0;
    }

    public void update(int judge, float rate) {
        if (value == 0) return;
        if (type == GrooveGauge.HAZARD && judge >= 3) {
            value = 0;
            return;
        }
        int delta;
        if (judge <= 2) {
            delta = type <= GrooveGauge.NORMAL
                    ? (judge == 2 ? goodRecovery : recovery)
                    : judge < 2 || type == GrooveGauge.HAZARD ? 8
                    : type >= GrooveGauge.CLASS ? 2 : 0;
        } else {
            boolean poor = judge == 4;
            delta = switch (type) {
                case GrooveGauge.ASSISTEASY, GrooveGauge.EASY -> poor ? -240 : -80;
                case GrooveGauge.NORMAL -> poor ? -300 : -100;
                case GrooveGauge.HARD -> poor ? -450 : -250;
                case GrooveGauge.EXHARD -> poor ? -900 : -500;
                default -> poor ? -125 : -85;
            };
            if ((type == GrooveGauge.HARD || type >= GrooveGauge.CLASS) && value < 1666) delta /= 2;
        }
        setUnits(value + (int) (delta * rate));
    }
}
