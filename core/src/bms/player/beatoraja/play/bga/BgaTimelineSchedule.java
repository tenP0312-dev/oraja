package bms.player.beatoraja.play.bga;

import java.util.ArrayList;
import bms.model.TimeLine;

/** Movie events only; finding the next event never resolves files or opens a decoder. */
final class BgaTimelineSchedule {
    record Event(long timeMs, int id, boolean layer) {}
    private final Event[] events;
    BgaTimelineSchedule(TimeLine[] timelines, MovieProcessor[] movies) {
        var entries = new ArrayList<Event>();
        for (TimeLine tl : timelines) {
            if (movie(tl.getBGA(), movies)) entries.add(new Event(tl.getTime(), tl.getBGA(), false));
            if (movie(tl.getLayer(), movies)) entries.add(new Event(tl.getTime(), tl.getLayer(), true));
        }
        events = entries.toArray(Event[]::new);
    }
    private static boolean movie(int id, MovieProcessor[] movies) {
        return id >= 0 && id < movies.length && movies[id] != null;
    }
    Event first(boolean layer) {
        for (Event event : events) if (event.layer == layer) return event;
        return null;
    }
    Event nextDistinctMovieAfter(long timeMs, int currentId, boolean layer) {
        int left = 0, right = events.length;
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (events[mid].timeMs <= timeMs) left = mid + 1;
            else right = mid;
        }
        for (int i = left; i < events.length; i++) {
            Event event = events[i];
            if (event.layer == layer && event.id != currentId) return event;
        }
        return null;
    }
}
