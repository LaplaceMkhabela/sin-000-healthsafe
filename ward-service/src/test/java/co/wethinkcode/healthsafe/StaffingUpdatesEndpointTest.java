package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The topic-fed staffing view is served here so callers need no direct call to staffing-service. */
class StaffingUpdatesEndpointTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private StaffingUpdateStore storeWith(String wardId, int doctors) {
        StaffingUpdateStore store = new StaffingUpdateStore();
        StaffingEvent event = new StaffingEvent();
        event.setWardId(wardId);
        event.setWing("East Wing");
        event.setDepartment("Paediatrics");
        event.setDoctorsOnCall(doctors);
        event.setEmergencyStatus(3);
        store.update(event);
        return store;
    }

    @Test
    void listsLatestUpdates() {
        StaffingUpdateStore store = storeWith("W-05", 4);
        WardServiceApp.setCatalogForTests(new WardCatalog(List.of()));
        JavalinTest.test(WardServiceApp.createApp(store), (server, client) -> {
            Response res = client.get("/staffing-updates");
            assertEquals(200, res.code());
            List<Map<String, Object>> body =
                mapper.readValue(res.body().string(), new TypeReference<>() {
                });
            assertEquals(1, body.size());
            assertEquals("W-05", body.get(0).get("wardId"));
        });
    }

    @Test
    void singleUpdateById() {
        StaffingUpdateStore store = storeWith("W-05", 4);
        WardServiceApp.setCatalogForTests(new WardCatalog(List.of()));
        JavalinTest.test(WardServiceApp.createApp(store), (server, client) -> {
            Response hit = client.get("/staffing-updates/W-05");
            assertEquals(200, hit.code());
            Map<String, Object> body =
                mapper.readValue(hit.body().string(), new TypeReference<>() {
                });
            assertEquals(4, body.get("doctorsOnCall"));

            Response miss = client.get("/staffing-updates/W-99");
            assertEquals(404, miss.code());
            assertTrue(miss.body().string().contains("error"));
        });
    }
}
