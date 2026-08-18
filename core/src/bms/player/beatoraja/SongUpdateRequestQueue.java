package bms.player.beatoraja;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Coalesces equivalent song-database scans that are waiting behind an active update. */
final class SongUpdateRequestQueue {

	private final Deque<Request> pending = new ArrayDeque<>();

	synchronized Request enqueue(String path, boolean updateParentWhenMissing, Runnable completion) {
		for (Request request : pending) {
			if (request.matches(path, updateParentWhenMissing)) {
				request.addCompletion(completion);
				return request;
			}
		}
		Request request = new Request(path, updateParentWhenMissing, completion);
		pending.addLast(request);
		return request;
	}

	synchronized Request poll() {
		return pending.pollFirst();
	}

	synchronized int size() {
		return pending.size();
	}

	static Request request(String path, boolean updateParentWhenMissing, Runnable completion) {
		return new Request(path, updateParentWhenMissing, completion);
	}

	static final class Request {
		private final String path;
		private final boolean updateParentWhenMissing;
		private final List<Runnable> completions = new ArrayList<>();

		private Request(String path, boolean updateParentWhenMissing, Runnable completion) {
			this.path = path;
			this.updateParentWhenMissing = updateParentWhenMissing;
			addCompletion(completion);
		}

		String path() {
			return path;
		}

		boolean updateParentWhenMissing() {
			return updateParentWhenMissing;
		}

		List<Runnable> completions() {
			return List.copyOf(completions);
		}

		private boolean matches(String otherPath, boolean otherUpdateParentWhenMissing) {
			return Objects.equals(path, otherPath)
					&& updateParentWhenMissing == otherUpdateParentWhenMissing;
		}

		private void addCompletion(Runnable completion) {
			if (completion != null) {
				completions.add(completion);
			}
		}
	}
}
