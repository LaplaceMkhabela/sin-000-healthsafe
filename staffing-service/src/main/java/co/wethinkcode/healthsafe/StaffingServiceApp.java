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
        StaffingEventPublisher publisher;
        try {
            publisher = new ActiveMqStaffingEventPublisher();
        } catch (Exception e) {
            log.warn("MQ publisher unavailable at startup, continuing without broadcasts: {}", e.getMessage());
            publisher = schedule -> log.warn("Skipping staffing broadcast for ward {} (no publisher)",
                schedule == null ? null : schedule.getWardId());
        }
        createApp(new HttpWardLookup(wardUrl), new HttpAlertLevelSource(alertUrl), publisher).start(7033);
        log.info("Staffing service up on :7033 (ward-service at {}, alert-level-service at {})", wardUrl, alertUrl);
    }

    /**
     * Builds the routes (without starting) so tests can drive the app on an
     * ephemeral port with faked sources.
     */
    public static Javalin createApp(WardLookup wards, AlertLevelSource alertLevel) {
        return createApp(wards, alertLevel, schedule -> {
        });
    }

    /** Same as {@link #createApp(WardLookup, AlertLevelSource)} with an explicit broadcast publisher. */
    public static Javalin createApp(WardLookup wards, AlertLevelSource alertLevel, StaffingEventPublisher publisher) {
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
                Schedule schedule = ScheduleCalculator.compute(ward, alertLevel.current());
                try {
                    publisher.publish(schedule);
                } catch (Exception e) {
                    // Broadcast is fire-and-forget: still return the schedule.
                    log.warn("Staffing broadcast failed for ward {} (returning schedule anyway): {}",
                        ward.getWardId(), e.getMessage());
                }
                ctx.json(schedule);
            } catch (DownstreamUnavailableException e) {
                ctx.status(503).json(Map.of("error", e.getMessage()));
            }
        });

        return app;
    }
}