package bms.player.beatoraja.launcher;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class SidebarSearchIndex<T> {
	private final Map<T, List<String>> entries = new LinkedHashMap<>();

	void add(T destination, String... searchText) {
		Objects.requireNonNull(destination, "destination");
		List<String> destinationEntries = entries.computeIfAbsent(destination, ignored -> new ArrayList<>());
		for (String value : searchText) {
			if (value != null && !value.isBlank()) {
				destinationEntries.add(normalize(value));
			}
		}
	}

	List<T> filter(Collection<T> destinations, String query) {
		String normalizedQuery = normalize(query);
		if (normalizedQuery.isEmpty()) {
			return List.copyOf(destinations);
		}
		return destinations.stream()
				.filter(destination -> entries.getOrDefault(destination, List.of()).stream()
						.anyMatch(candidate -> candidate.contains(normalizedQuery)))
				.toList();
	}

	static <T> T preferredSelection(List<T> matches, T current) {
		if (matches.isEmpty()) {
			return null;
		}
		return matches.contains(current) ? current : matches.get(0);
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return Normalizer.normalize(value, Normalizer.Form.NFKC)
				.trim()
				.toLowerCase(Locale.ROOT);
	}
}
