package co.wethinkcode.healthsafe;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Received equipment failure alerts, keyed by alert id. */
public final class AlertStore {

    private final ConcurrentMap<String, EquipmentAlert> alerts = new ConcurrentHashMap<>();

    /** Records an alert. Returns false only when there is nothing to record. */
    public boolean record(EquipmentAlert alert) {
        if (alert == null || alert.getId() == null || alert.getId().isBlank()) {
            return false;
        }
        alerts.put(alert.getId().trim(), alert);
        return true;
    }

    public Optional<EquipmentAlert> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(alerts.get(id.trim()));
    }

    /** All recorded alerts, oldest first. */
    public List<EquipmentAlert> all() {
        return alerts.values().stream()
            .sorted(Comparator.comparing(EquipmentAlert::getTimestamp, Comparator.nullsLast(String::compareTo))
                .thenComparing(EquipmentAlert::getId, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    public int size() {
        return alerts.size();
    }
}
