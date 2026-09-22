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

class AlertsEndpointTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AlertStore storeWith(String id, String wardId) {
        AlertStore store = new AlertStore();
        EquipmentAlert alert = new EquipmentAlert();
        alert.setId(id);
        alert.setWardId(wardId);
        alert.setDescription("ventilator failure");
        alert.setSeverity("CRITICAL");
        alert.setTimestamp("2026-01-01T00:00:00Z");
        store.record(alert);
        return store;
    }

    @Test
    void listsRecordedAlerts() {
        JavalinTest.test(EquipmentAlertServiceApp.createApp(storeWith("a-1", "W-05")), (server, client) -> {
            Response res = client.get("/alerts");
            assertEquals(200, res.code());
            List<Map<String, Object>> body = mapper.readValue(res.body().string(), new TypeReference<>() {
            });
            assertEquals(1, body.size());
            assertEquals("a-1", body.get(0).get("id"));
            assertEquals("W-05", body.get(0).get("wardId"));
        });
    }

    @Test
    void singleAlertById() {
        JavalinTest.test(EquipmentAlertServiceApp.createApp(storeWith("a-1", "W-05")), (server, client) -> {
            Response hit = client.get("/alerts/a-1");
            assertEquals(200, hit.code());
            assertEquals("ventilator failure",
                mapper.readValue(hit.body().string(), new TypeReference<Map<String, Object>>() {
                }).get("description"));

            Response miss = client.get("/alerts/nope");
            assertEquals(404, miss.code());
            assertTrue(miss.body().string().contains("error"));
        });
    }
}
