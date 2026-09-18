package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaffingServiceApp {

    private static final Logger log = LoggerFactory.getLogger(StaffingServiceApp.class);

    public static void main(String[] args) {
        String wardUrl = System.getProperty("healthsafe.ward.url", "http://localhost:7031");
        String alertUrl = System.getProperty("healthsafe.alert.url", "http://localhost:7032");
        createApp(new HttpWardLookup(wardUrl), new HttpAlertLevelSource(alertUrl)).start(7033);
        log.info("Staffing service up on :7033 (ward-service at {}, alert-level-service at {})", wardUrl, alertUrl);
    }

    /**
     * Builds the routes (without starting) so tests can drive the app on an
     * ephemeral port with faked sources.
     */
    public static Javalin createApp(WardLookup wards, AlertLevelSource alertLevel) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/schedule", ctx -> {
            String wardId = ctx.queryParam("wardId");
            if (wardId == null || wardId.isBlank()) {
                ctx.status(400).json(Map.of("error", "missing required query parameter: wardId"));
                return;
            }
            try {
                Ward ward = wards.findById(wardId).orElse(null);
                if (ward == null) {
                    ctx.status(404).json(Map.of("error", "unknown ward id: " + wardId));
                    return;
                }
                ctx.json(ScheduleCalculator.compute(ward, alertLevel.current()));
            } catch (DownstreamUnavailableException e) {
                ctx.status(503).json(Map.of("error", e.getMessage()));
            }
        });

        return app;
    }
}