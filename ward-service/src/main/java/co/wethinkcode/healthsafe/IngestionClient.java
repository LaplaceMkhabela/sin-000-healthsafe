package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Synchronous client for ingestion-service. Fetches the cleaned ward records the
 * rest of ward-service serves up.
 */
public final class IngestionClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public IngestionClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper());
    }

    IngestionClient(String baseUrl, HttpClient http, ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.mapper = mapper;
    }

    public List<Ward> fetchWards() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/wards"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IngestionUnavailableException("ingestion-service returned HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(), new TypeReference<List<Ward>>() {
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionUnavailableException("request to ingestion-service interrupted", e);
        } catch (IOException e) {
            throw new IngestionUnavailableException("ingestion-service unreachable: " + e.getMessage(), e);
        }
    }

    /** Raised when ingestion-service cannot be reached or answers badly. */
    public static final class IngestionUnavailableException extends RuntimeException {
        public IngestionUnavailableException(String message) {
            super(message);
        }

        public IngestionUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}