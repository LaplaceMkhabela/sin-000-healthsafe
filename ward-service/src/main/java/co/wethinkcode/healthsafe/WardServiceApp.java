package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WardServiceApp {

    private static final Logger log = LoggerFactory.getLogger(WardServiceApp.class);

    public static void main(String[] args) {
        String ingestionUrl = System.getProperty("healthsafe.ingestion.url", "http://localhost:7030");
        List<Ward> loaded = loadWards(ingestionUrl);
        WardCatalog catalog = new WardCatalog(loaded);

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> ctx.json(catalog.all()));

        app.get("/wards/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Ward ward = catalog.byId(id).orElse(null);
            if (ward == null) {
                ctx.status(404).json(Map.of("error", "unknown ward id: " + id));
                return;
            }
            ctx.json(ward);
        });

        app.get("/departments", ctx -> ctx.json(catalog.departments()));

        log.info("Ward service up on :7031 serving {} wards (ingestion at {})", catalog.all().size(), ingestionUrl);
    }

    private static List<Ward> loadWards(String ingestionUrl) {
        try {
            return new IngestionClient(ingestionUrl).fetchWards();
        } catch (IngestionClient.IngestionUnavailableException e) {
            log.warn("Could not load wards from ingestion-service at startup: {}", e.getMessage());
            return List.of();
        }
    }
}