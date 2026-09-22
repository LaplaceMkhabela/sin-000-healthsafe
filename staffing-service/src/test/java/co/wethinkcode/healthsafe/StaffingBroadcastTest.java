package co.wethinkcode.healthsafe;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaffingBroadcastTest {

    private Ward ward() {
        Ward ward = new Ward();
        ward.setWardId("W-05");
        ward.setWing("East Wing");
        ward.setDepartment("Paediatrics");
        ward.setBedsAvailable(5);
        return ward;
    }

    @Test
    void schedulePublishesStaffingEvent() {
        List<Schedule> published = new ArrayList<>();
        StaffingEventPublisher publisher = published::add;
        JavalinTest.test(
            StaffingServiceApp.createApp(id -> Optional.of(ward()), () -> 3, publisher),
            (server, client) -> {
                Response res = client.get("/schedule?wardId=W-05");
                assertEquals(200, res.code());
                res.close();
                assertEquals(1, published.size());
                assertEquals("W-05", published.get(0).getWardId());
                assertEquals(3, published.get(0).getEmergencyStatus());
                assertEquals(4, published.get(0).getDoctorsOnCall());
            }
        );
    }

    @Test
    void unknownWardPublishesNothing() {
        List<Schedule> published = new ArrayList<>();
        StaffingEventPublisher publisher = published::add;
        JavalinTest.test(
            StaffingServiceApp.createApp(id -> Optional.empty(), () -> 3, publisher),
            (server, client) -> {
                Response res = client.get("/schedule?wardId=W-99");
                assertEquals(404, res.code());
                res.close();
                assertEquals(0, published.size());
            }
        );
    }

    @Test
    void brokerFailureStillReturnsSchedule() {
        StaffingEventPublisher failing = schedule -> {
            throw new ActiveMqStaffingEventPublisher.StaffingPublishException("broker down", null);
        };
        AtomicReference<Integer> status = new AtomicReference<>();
        JavalinTest.test(
            StaffingServiceApp.createApp(id -> Optional.of(ward()), () -> 2, failing),
            (server, client) -> {
                Response res = client.get("/schedule?wardId=W-05");
                status.set(res.code());
                res.close();
            }
        );
        assertEquals(200, status.get());
    }

    @Test
    void eventCarriesTimestamp() {
        Ward ward = ward();
        Schedule schedule = ScheduleCalculator.compute(ward, 1);
        StaffingEvent event = StaffingEvent.from(schedule);
        assertEquals("W-05", event.getWardId());
        assertEquals("East Wing", event.getWing());
        assertEquals(schedule.getDoctorsOnCall(), event.getDoctorsOnCall());
        // Timestamp is stamped at broadcast time, not part of the REST schedule.
        assertEquals(true, event.getTimestamp() != null && !event.getTimestamp().isBlank());
    }
}
