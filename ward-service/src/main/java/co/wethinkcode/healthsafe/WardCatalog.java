package co.wethinkcode.healthsafe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Lookup over the loaded ward list: by id, and the distinct departments. */
public final class WardCatalog {

    private final List<Ward> wards;

    public WardCatalog(List<Ward> wards) {
        this.wards = List.copyOf(wards);
    }

    public List<Ward> all() {
        return wards;
    }

    /** Looks up a ward by id, tolerating the case/padding variants the legacy data had. */
    public Optional<Ward> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.trim().toUpperCase();
        return wards.stream()
            .filter(ward -> ward.getWardId() != null && ward.getWardId().equals(normalized))
            .findFirst();
    }

    public List<String> departments() {
        return wards.stream()
            .map(Ward::getDepartment)
            .filter(Objects::nonNull)
            .filter(department -> !department.isBlank())
            .distinct()
            .sorted()
            .toList();
    }
}