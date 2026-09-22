package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A staffing update received on the {@code staffing-events-topic} topic.
 * Mirrors the producer's event shape; unknown fields are ignored so the two
 * services can evolve independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StaffingEvent {

    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;
    private int emergencyStatus;
    private int doctorsOnCall;
    private String timestamp;

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

    public int getEmergencyStatus() {
        return emergencyStatus;
    }

    public void setEmergencyStatus(int emergencyStatus) {
        this.emergencyStatus = emergencyStatus;
    }

    public int getDoctorsOnCall() {
        return doctorsOnCall;
    }

    public void setDoctorsOnCall(int doctorsOnCall) {
        this.doctorsOnCall = doctorsOnCall;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
