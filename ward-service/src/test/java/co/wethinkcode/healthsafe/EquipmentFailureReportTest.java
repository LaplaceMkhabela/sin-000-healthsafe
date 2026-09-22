package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reporting a failure must reach the queue exactly as the alert service will consume it. */
class EquipmentFailureReportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Response post(HttpClient client, String path, String json) {
        return client.post(path, json);
    }

    @BeforeEach
    void seedCatalog() {
        Ward ward = new Ward();
        ward.setWardId("W-05");
        ward.setWing("East Wing");
        ward.setDepartment("Paediatrics");
        WardServiceApp.setCatalogForTests(new WardCatalog(List.of(ward)));
    }

    @Test
    void reportDeliversAlertToQueue() {
        List<EquipmentAlert> published = new ArrayList<>();
        EquipmentAlertPublisher publisher = published::add;
        JavalinTest.test(WardServiceApp.createApp(new StaffingUpdateStore(), publisher), (server, client) -> {
            Response res = post(client, "/wards/W-05/equipment-failure",
                "{\"description\":\"ventilator failure\",\"severity\":\"critical\"}");
            assertEquals(202, res.code());
            Map<String, Object> body = mapper.readValue(res.body().string(), new TypeReference<>() {
            });
            assertEquals("W-05", body.get("wardId"));
            assertEquals("ventilator failure", body.get("description"));
            assertEquals("CRITICAL", body.get("severity"));
            assertTrue(body.get("id") != null && !body.get("id").toString().isBlank());
            assertEquals(1, published.size());
            assertEquals("W-05", published.get(0).getWardId());
        });
    }

    @Test
    void unknownWardReports404WithoutPublishing() {
        List<EquipmentAlert> published = new ArrayList<>();
        JavalinTest.test(WardServiceApp.createApp(new StaffingUpdateStore(), published::add), (server, client) -> {
            Response res = post(client, "/wards/W-99/equipment-failure", "{\"description\":\"x\"}");
            assertEquals(404, res.code());
            assertEquals(0, published.size());
        });
    }

    @Test
    void missingDescriptionReturns400() {
        List<EquipmentAlert> published = new ArrayList<>();
        JavalinTest.test(WardServiceApp.createApp(new StaffingUpdateStore(), published::add), (server, client) -> {
            assertEquals(400, post(client, "/wards/W-05/equipment-failure", "{}").code());
            assertEquals(400, post(client, "/wards/W-05/equipment-failure", "{\"description\":\"  \"}").code());
            assertEquals(400, post(client, "/wards/W-05/equipment-failure", "not json").code());
            assertEquals(0, published.size());
        });
    }

    @Test
    void queueDownReturns503SoCallerRetries() {
        EquipmentAlertPublisher down = alert -> {
            throw new ActiveMqEquipmentAlertPublisher.EquipmentPublishException("broker down", null);
        };
        JavalinTest.test(WardServiceApp.createApp(new StaffingUpdateStore(), down), (server, client) -> {
            Response res = post(client, "/wards/W-05/equipment-failure", "{\"description\":\"ventilator failure\"}");
            assertEquals(503, res.code());
            assertTrue(res.body().string().contains("error"));
        });
    }
}
