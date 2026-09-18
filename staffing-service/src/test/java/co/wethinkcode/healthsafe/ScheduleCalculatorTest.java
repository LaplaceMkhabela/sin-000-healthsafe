package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScheduleCalculatorTest {

    private Ward ward(int beds) {
        Ward ward = new Ward();
        ward.setWardId("W-05");
        ward.setWing("East Wing");
        ward.setDepartment("Paediatrics");
        ward.setBedsAvailable(beds);
        return ward;
    }

    @Test
    void carriesWardContextIntoSchedule() {
        Ward ward = ward(5);
        ward.setDepartment("Oncology");
        Schedule schedule = ScheduleCalculator.compute(ward, 2);
        assertEquals("W-05", schedule.getWardId());
        assertEquals("East Wing", schedule.getWing());
        assertEquals("Oncology", schedule.getDepartment());
        assertEquals(Integer.valueOf(5), schedule.getBedsAvailable());
        assertEquals(2, schedule.getEmergencyStatus());
    }

    @Test
    void higherEmergencyStatusMeansMoreDoctors() {
        int low = ScheduleCalculator.compute(ward(5), 1).getDoctorsOnCall();
        int high = ScheduleCalculator.compute(ward(5), 6).getDoctorsOnCall();
        assertEquals(2, low);
        assertEquals(7, high);
    }

    @Test
    void bedsAddOneDoctorPerTenBeds() {
        assertEquals(4, ScheduleCalculator.compute(ward(25), 1).getDoctorsOnCall());
        assertEquals(3, ScheduleCalculator.compute(ward(20), 1).getDoctorsOnCall());
        assertEquals(2, ScheduleCalculator.compute(ward(10), 1).getDoctorsOnCall());
    }

    @Test
    void unknownBedsContributeNothing() {
        assertEquals(1, ScheduleCalculator.compute(ward(0), 0).getDoctorsOnCall());
        Ward noBeds = ward(5);
        noBeds.setBedsAvailable(null);
        assertEquals(1, ScheduleCalculator.compute(noBeds, 0).getDoctorsOnCall());
        assertEquals(8, ScheduleCalculator.compute(noBeds, 8).getDoctorsOnCall());
    }
}