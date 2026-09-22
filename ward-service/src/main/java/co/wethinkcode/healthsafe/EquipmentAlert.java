package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * A critical medical equipment failure reported on a ward, published to the
 * {@code equipment-failure-queue} queue.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentAlert {

    private String id;
    private String wardId;
    private String wing;
    private String department;
    private String description;
    private String severity;
    private String timestamp;

    /** Builds an alert for the given ward, stamping id and time. */
    public static EquipmentAlert report(Ward ward, String description, String severity) {
        EquipmentAlert alert = new EquipmentAlert();
        alert.setId(UUID.randomUUID().toString());
        alert.setWardId(ward.getWardId());
        alert.setWing(ward.getWing());
        alert.setDepartment(ward.getDepartment());
        alert.setDescription(description);
        alert.setSeverity(severity == null || severity.isBlank() ? "CRITICAL" : severity.trim().toUpperCase());
        alert.setTimestamp(Instant.now().toString());
        return alert;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
