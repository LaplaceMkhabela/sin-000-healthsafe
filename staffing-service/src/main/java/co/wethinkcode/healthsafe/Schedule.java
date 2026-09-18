package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The on-call schedule for one ward, as returned by this service. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Schedule {

    private String wardId;
    private String wing;
    private String department;
    private Integer bedsAvailable;
    private int emergencyStatus;
    private int doctorsOnCall;

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
}