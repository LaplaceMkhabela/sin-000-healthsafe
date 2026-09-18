package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertLevelServiceAppTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private String body(Response res) {
        try {
            return res.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> bodyMap(Response res) {
        try {
            return mapper.readValue(body(res), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void healthReturnsOk() {
        JavalinTest.test(AlertLevelServiceApp.createApp(new AlertLevelStore()), (server, client) -> {
            Response res = client.get("/health");
            assertEquals(200, res.code());
            assertEquals("OK", body(res));
        });
    }

    @Test
    void defaultLevelIsZero() {
        JavalinTest.test(AlertLevelServiceApp.createApp(new AlertLevelStore()), (server, client) -> {
            Response res = client.get("/alert-level");
            assertEquals(200, res.code());
            assertEquals(0, bodyMap(res).get("level"));
        });
    }

    @Test
    void putUpdatesThePersistedLevel() {
        AlertLevelStore store = new AlertLevelStore();
        JavalinTest.test(AlertLevelServiceApp.createApp(store), (server, client) -> {
            Response put = client.put("/alert-level", "{\"level\":5}");
            assertEquals(200, put.code());
            assertEquals(5, bodyMap(put).get("level"));

            Response get = client.get("/alert-level");
            assertEquals(5, bodyMap(get).get("level"));
        });
    }

    @Test
    void putRejectsOutOfRangeLevels() {
        AlertLevelStore store = new AlertLevelStore();
        JavalinTest.test(AlertLevelServiceApp.createApp(store), (server, client) -> {
            Response tooHigh = client.put("/alert-level", "{\"level\":9}");
            assertEquals(400, tooHigh.code());
            assertEquals(true, bodyMap(tooHigh).containsKey("error"));

            Response tooLow = client.put("/alert-level", "{\"level\":-1}");
            assertEquals(400, tooLow.code());

            assertEquals(0, store.current());
        });
    }

    @Test
    void putRejectsMalformedBody() {
        JavalinTest.test(AlertLevelServiceApp.createApp(new AlertLevelStore()), (server, client) -> {
            Response res = client.put("/alert-level", "not json");
            assertEquals(400, res.code());
        });
    }
}