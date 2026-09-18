package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionClientTest {

    private HttpServer server;
    private IngestionClient client;

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        client = new IngestionClient(
            "http://localhost:" + port,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            new ObjectMapper()
        );
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void parsesWardsSuccessfully() throws IOException {
        server.createContext("/wards", ex -> respond(ex, 200,
            "[{\"wardId\":\"W-01\",\"wing\":\"East Wing\",\"department\":\"Cardiology\",\"bedsAvailable\":3},"
                + "{\"wardId\":\"W-02\",\"department\":\"Oncology\"}]"
        ));

        List<Ward> wards = client.fetchWards();
        assertEquals(2, wards.size());
        assertEquals("W-01", wards.get(0).getWardId());
        assertEquals("East Wing", wards.get(0).getWing());
        assertEquals(Integer.valueOf(3), wards.get(0).getBedsAvailable());
        assertNull(wards.get(1).getWing());
        assertEquals("Oncology", wards.get(1).getDepartment());
    }

    @Test
    void throwsOnNon200Status() {
        server.createContext("/wards", ex -> {
            try {
                respond(ex, 503, "{\"error\":\"down\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrows(IngestionClient.IngestionUnavailableException.class, client::fetchWards);
    }

    @Test
    void throwsWhenServerUnreachable() {
        IngestionClient bad = new IngestionClient(
            "http://localhost:1",
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build(),
            new ObjectMapper()
        );
        assertThrows(IngestionClient.IngestionUnavailableException.class, bad::fetchWards);
    }
}