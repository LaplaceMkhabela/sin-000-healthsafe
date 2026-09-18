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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpWardLookupTest {

    private HttpServer server;
    private HttpWardLookup lookup;

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        lookup = new HttpWardLookup(
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
    void parsesFoundWard() {
        server.createContext("/wards/W-05", ex -> {
            try {
                respond(ex, 200, "{\"wardId\":\"W-05\",\"wing\":\"East Wing\",\"department\":\"Paediatrics\",\"bedsAvailable\":5}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Optional<Ward> ward = lookup.findById("W-05");
        assertTrue(ward.isPresent());
        assertEquals("East Wing", ward.orElseThrow().getWing());
        assertEquals(Integer.valueOf(5), ward.orElseThrow().getBedsAvailable());
    }

    @Test
    void unknownWardYieldsEmpty() {
        server.createContext("/wards/W-99", ex -> {
            try {
                respond(ex, 404, "{\"error\":\"unknown ward id: W-99\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(lookup.findById("W-99").isEmpty());
    }

    @Test
    void downstreamErrorThrows() {
        server.createContext("/wards/W-05", ex -> {
            try {
                respond(ex, 503, "{\"error\":\"down\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThrows(DownstreamUnavailableException.class, () -> lookup.findById("W-05"));
    }

    @Test
    void unreachableServerThrows() {
        HttpWardLookup bad = new HttpWardLookup(
            "http://localhost:1",
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build(),
            new ObjectMapper()
        );
        assertThrows(DownstreamUnavailableException.class, () -> bad.findById("W-05"));
    }
}