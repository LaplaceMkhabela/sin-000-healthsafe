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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpAlertLevelSourceTest {

    private HttpServer server;
    private HttpAlertLevelSource source;

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        source = new HttpAlertLevelSource(
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
    void readsCurrentLevel() {
        server.createContext("/alert-level", ex -> {
            try {
                respond(ex, 200, "{\"level\":5}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(5, source.current());
    }

    @Test
    void downstreamErrorThrows() {
        server.createContext("/alert-level", ex -> {
            try {
                respond(ex, 503, "{\"error\":\"down\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrows(DownstreamUnavailableException.class, source::current);
    }

    @Test
    void unreachableServerThrows() {
        HttpAlertLevelSource bad = new HttpAlertLevelSource(
            "http://localhost:1",
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build(),
            new ObjectMapper()
        );
        assertThrows(DownstreamUnavailableException.class, bad::current);
    }
}