package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlertLevelServiceApp {

    private static final Logger log = LoggerFactory.getLogger(AlertLevelServiceApp.class);

    public static void main(String[] args) {
        createApp(new AlertLevelStore()).start(7032);
        log.info("Alert level service up on :7032");
    }

    /** Builds the routes (without starting) so tests can drive the app on an ephemeral port. */
    public static Javalin createApp(AlertLevelStore store) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alert-level", ctx -> ctx.json(Map.of("level", store.current())));

        app.put("/alert-level", ctx -> {
            AlertLevelRequest request;
            try {
                request = ctx.bodyAsClass(AlertLevelRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "invalid body: expected {\"level\": 0-8}"));
                return;
            }
            if (request == null) {
                ctx.status(400).json(Map.of("error", "invalid body: expected {\"level\": 0-8}"));
                return;
            }
            try {
                int updated = store.update(request.level);
                ctx.json(Map.of("level", updated));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        return app;
    }

    public static class AlertLevelRequest {
        private int level;

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }
    }
}