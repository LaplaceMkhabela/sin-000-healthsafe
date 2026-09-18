package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Reads the current Emergency Status from alert-level-service. */
public final class HttpAlertLevelSource implements AlertLevelSource {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public HttpAlertLevelSource(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper());
    }

    HttpAlertLevelSource(String baseUrl, HttpClient http, ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public int current() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/alert-level"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DownstreamUnavailableException("alert-level-service returned HTTP " + response.statusCode());
            }
            Map<String, Object> parsed = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
            });
            Object level = parsed.get("level");
            if (level instanceof Number number) {
                return number.intValue();
            }
            throw new DownstreamUnavailableException("alert-level-service returned an unexpected payload");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException("request to alert-level-service interrupted", e);
        } catch (IOException e) {
            throw new DownstreamUnavailableException("alert-level-service unreachable: " + e.getMessage(), e);
        }
    }
}