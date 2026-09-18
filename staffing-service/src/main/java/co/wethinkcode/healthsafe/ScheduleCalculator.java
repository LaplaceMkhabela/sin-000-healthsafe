package co.wethinkcode.healthsafe;

/**
 * Sizes the on-call roster for a ward.
 *
 * <p>Formula: one doctor per ten beds that must be covered, plus one doctor per
 * Emergency Status point (at least one). A higher status or a bigger ward always
 * means a bigger roster.</p>
 */
public final class ScheduleCalculator {

    private static final int BEDS_PER_DOCTOR = 10;

    private ScheduleCalculator() {
    }

    public static Schedule compute(Ward ward, int emergencyStatus) {
        Schedule schedule = new Schedule();
        schedule.setWardId(ward.getWardId());
        schedule.setWing(ward.getWing());
        schedule.setDepartment(ward.getDepartment());
        schedule.setBedsAvailable(ward.getBedsAvailable());
        schedule.setEmergencyStatus(emergencyStatus);

        int bedsFactor = ward.getBedsAvailable() == null
            ? 0
            : (int) Math.ceil((double) ward.getBedsAvailable() / BEDS_PER_DOCTOR);
        schedule.setDoctorsOnCall(Math.max(1, emergencyStatus) + bedsFactor);
        return schedule;
    }
}