package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Fetches a single ward from ward-service. */
public final class HttpWardLookup implements WardLookup {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public HttpWardLookup(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper());
    }

    HttpWardLookup(String baseUrl, HttpClient http, ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public Optional<Ward> findById(String wardId) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/wards/" + urlEncode(wardId)))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return switch (response.statusCode()) {
                case 200 -> Optional.of(mapper.readValue(response.body(), Ward.class));
                case 404 -> Optional.empty();
                default -> throw new DownstreamUnavailableException("ward-service returned HTTP " + response.statusCode());
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException("request to ward-service interrupted", e);
        } catch (IOException e) {
            throw new DownstreamUnavailableException("ward-service unreachable: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}