package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EquipmentAlertServiceApp {

    private static final Logger log = LoggerFactory.getLogger(EquipmentAlertServiceApp.class);

    private static final AlertStore alerts = new AlertStore();

    public static void main(String[] args) {
        // Best-effort queue subscription: queued alerts wait on the broker
        // until this consumer connects, and REST keeps serving meanwhile.
        try {
            new EquipmentFailureConsumer(alerts).startAsync();
        } catch (Exception e) {
            log.warn("Equipment failure queue subscription unavailable at startup (still serving alerts): {}",
                e.getMessage());
        }

        Javalin app = createApp(alerts).start(7034);

        log.info("Equipment alert service up on :7034");
    }

    /**
     * Builds the routes (without starting) so tests can drive the app on an
     * ephemeral port.
     */
    public static Javalin createApp(AlertStore store) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alerts", ctx -> ctx.json(store.all()));

        app.get("/alerts/{id}", ctx -> {
            String id = ctx.pathParam("id");
            EquipmentAlert alert = store.findById(id).orElse(null);
            if (alert == null) {
                ctx.status(404).json(Map.of("error", "unknown alert id: " + id));
                return;
            }
            ctx.json(alert);
        });

        return app;
    }
}
