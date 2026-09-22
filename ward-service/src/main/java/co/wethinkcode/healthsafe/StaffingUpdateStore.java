package co.wethinkcode.healthsafe;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Local view of staffing updates received over the topic. Keeps the latest
 * event per ward so the frontend can read staffing state from ward-service
 * without calling staffing-service directly.
 */
public final class StaffingUpdateStore {

    private final ConcurrentMap<String, StaffingEvent> latest = new ConcurrentHashMap<>();

    /** Records an event, replacing any older event for the same ward. */
    public void update(StaffingEvent event) {
        if (event == null || event.getWardId() == null || event.getWardId().isBlank()) {
            return;
        }
        latest.put(normalize(event.getWardId()), event);
    }

    public Optional<StaffingEvent> findById(String wardId) {
        if (wardId == null || wardId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latest.get(normalize(wardId)));
    }

    /** All latest events, sorted by ward id. */
    public List<StaffingEvent> all() {
        return latest.values().stream()
            .sorted(Comparator.comparing(StaffingEvent::getWardId, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    public int size() {
        return latest.size();
    }

    private static String normalize(String id) {
        return id.trim().toUpperCase();
    }
}
