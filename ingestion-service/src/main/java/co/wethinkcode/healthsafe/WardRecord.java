package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * A cleaned ward record, ready for the rest of the HealthSafe pipeline to consume.
 *
 * <p>{@code bedsAvailable} is {@code null} when the legacy data could not be trusted
 * (placeholder, non-numeric, negative, or unrealistic). Every adjustment or
 * ambiguity from the cleaning step is surfaced through {@link #getNotes()} so
 * downstream callers can see that a human should follow up.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WardRecord {

    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;
    private final List<String> flags = new ArrayList<>();

    public String getWardId() {
        return wardId;
    }

    public void setWardId(String wardId) {
        this.wardId = wardId;
    }

    public String getWing() {
        return wing;
    }

    public void setWing(String wing) {
        this.wing = wing;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getBedsAvailable() {
        return bedsAvailable;
    }

    public void setBedsAvailable(Integer bedsAvailable) {
        this.bedsAvailable = bedsAvailable;
    }

    public void addFlag(String note) {
        flags.add(note);
    }

    public String getNotes() {
        return flags.isEmpty() ? null : String.join("; ", flags);
    }

    /**
     * Merges a later duplicate of the same real-world ward into this record,
     * adopting only fields this record does not already have, and flags the merge
     * so the follow-up is visible. Keeps the first complete value rather than
     * letting a second, messier row overwrite good data.
     */
    public WardRecord mergeWith(WardRecord other) {
        if (bedsAvailable == null && other.bedsAvailable != null) {
            bedsAvailable = other.bedsAvailable;
            flags.add("adopted bedsAvailable from merged duplicate");
        }
        if (wing == null && other.wing != null) {
            wing = other.wing;
            flags.add("adopted wing from merged duplicate");
        }
        if (department == null && other.department != null) {
            department = other.department;
            flags.add("adopted department from merged duplicate");
        }
        flags.add("merged duplicate record for ward " + wardId);
        return this;
    }
}