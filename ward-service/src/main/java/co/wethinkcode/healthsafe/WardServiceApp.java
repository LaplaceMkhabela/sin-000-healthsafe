package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WardServiceApp {

    private static final Logger log = LoggerFactory.getLogger(WardServiceApp.class);

    private static volatile WardCatalog catalog = new WardCatalog(List.of());

    public static void main(String[] args) {
        String ingestionUrl = System.getProperty("healthsafe.ingestion.url", "http://localhost:7030");
        int refreshSeconds = Integer.getInteger("healthsafe.ward.refresh.seconds", 10);

        IngestionClient ingestion = new IngestionClient(ingestionUrl);
        refresh(ingestion);

        ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ward-refresh");
            thread.setDaemon(true);
            return thread;
        });
        refresher.scheduleWithFixedDelay(() -> refresh(ingestion), refreshSeconds, refreshSeconds, TimeUnit.SECONDS);

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

        log.info("Ward service up on :7031 (ingestion at {}, refresh every {}s)", ingestionUrl, refreshSeconds);
    }

    /** Best-effort reload from ingestion-service; keeps serving the last good catalog on failure. */
    static void refresh(IngestionClient ingestion) {
        try {
            catalog = new WardCatalog(ingestion.fetchWards());
            log.info("Loaded {} wards from ingestion-service", catalog.all().size());
        } catch (IngestionClient.IngestionUnavailableException e) {
            log.warn("Ward refresh failed (still serving {} cached wards): {}", catalog.all().size(), e.getMessage());
        }
    }
}