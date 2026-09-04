package bms.player.beatoraja.skin.scene.render;

public enum BlendMode {
	NORMAL,
	ADD,
	SUBTRACT,
	MULTIPLY,
	SCREEN,
	ERASE,
	INVERT;

	public static BlendMode parse(String value) {
		if (value == null) {
			return NORMAL;
		}
		return switch (value.trim().toLowerCase()) {
			case "normal" -> NORMAL;
			case "add" -> ADD;
			case "subtract" -> SUBTRACT;
			case "multiply" -> MULTIPLY;
			case "screen" -> SCREEN;
			case "erase" -> ERASE;
			case "invert" -> INVERT;
			default -> throw new IllegalArgumentException("unknown blend mode: " + value);
		};
	}
}
