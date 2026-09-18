package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingServiceAppTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Ward ward() {
        Ward ward = new Ward();
        ward.setWardId("W-05");
        ward.setWing("East Wing");
        ward.setDepartment("Paediatrics");
        ward.setBedsAvailable(5);
        return ward;
    }

    private Map<String, Object> bodyMap(Response res) {
        try {
            return mapper.readValue(res.body().string(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void returnsComputedRosterForValidWard() {
        WardLookup wards = id -> Optional.of(ward());
        AlertLevelSource alert = () -> 3;
        JavalinTest.test(StaffingServiceApp.createApp(wards, alert), (server, client) -> {
            Response res = client.get("/schedule?wardId=W-05");
            assertEquals(200, res.code());
            Map<String, Object> body = bodyMap(res);
            assertEquals("W-05", body.get("wardId"));
            assertEquals("East Wing", body.get("wing"));
            assertEquals("Paediatrics", body.get("department"));
            assertEquals(3, body.get("emergencyStatus"));
            assertEquals(5, body.get("bedsAvailable"));
            assertEquals(4, body.get("doctorsOnCall"));
        });
    }

    @Test
    void unknownWardReturns404() {
        WardLookup wards = id -> Optional.empty();
        AlertLevelSource alert = () -> 0;
        JavalinTest.test(StaffingServiceApp.createApp(wards, alert), (server, client) -> {
            Response res = client.get("/schedule?wardId=W-99");
            assertEquals(404, res.code());
            assertEquals(true, bodyMap(res).containsKey("error"));
        });
    }

    @Test
    void missingWardIdReturns400() {
        WardLookup wards = id -> Optional.of(ward());
        AlertLevelSource alert = () -> 0;
        JavalinTest.test(StaffingServiceApp.createApp(wards, alert), (server, client) -> {
            Response res = client.get("/schedule");
            assertEquals(400, res.code());
            assertTrue(bodyMap(res).get("error").toString().toLowerCase().contains("wardid"));
        });
    }

    @Test
    void wardServiceDownReturns503() {
        WardLookup down = id -> {
            throw new DownstreamUnavailableException("ward-service unreachable: connection refused");
        };
        AlertLevelSource alert = () -> 0;
        JavalinTest.test(StaffingServiceApp.createApp(down, alert), (server, client) -> {
            Response res = client.get("/schedule?wardId=W-05");
            assertEquals(503, res.code());
            assertEquals(true, bodyMap(res).containsKey("error"));
        });
    }

    @Test
    void alertLevelServiceDownReturns503() {
        WardLookup wards = id -> Optional.of(ward());
        AlertLevelSource down = () -> {
            throw new DownstreamUnavailableException("alert-level-service unreachable: timeout");
        };
        JavalinTest.test(StaffingServiceApp.createApp(wards, down), (server, client) -> {
            Response res = client.get("/schedule?wardId=W-05");
            assertEquals(503, res.code());
            assertEquals(true, bodyMap(res).containsKey("error"));
        });
    }
}