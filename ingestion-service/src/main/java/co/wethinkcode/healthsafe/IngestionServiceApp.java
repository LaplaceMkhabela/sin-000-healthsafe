package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngestionServiceApp {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceApp.class);

    public static void main(String[] args) {
        List<WardRecord> wards = WardCsvLoader.loadFromResource("wards-outdated.csv");

        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(wards));

        log.info("Ingestion service up on :7030 serving {} cleaned wards", wards.size());
    }
}