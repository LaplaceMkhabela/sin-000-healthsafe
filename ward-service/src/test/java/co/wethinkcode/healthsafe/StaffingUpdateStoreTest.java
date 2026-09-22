package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingUpdateStoreTest {

    private StaffingEvent event(String wardId, int doctors) {
        StaffingEvent event = new StaffingEvent();
        event.setWardId(wardId);
        event.setDoctorsOnCall(doctors);
        event.setEmergencyStatus(3);
        return event;
    }

    @Test
    void keepsLatestEventPerWard() {
        StaffingUpdateStore store = new StaffingUpdateStore();
        store.update(event("W-05", 4));
        store.update(event("W-05", 7));
        assertEquals(1, store.size());
        assertEquals(7, store.findById("W-05").orElseThrow().getDoctorsOnCall());
    }

    @Test
    void lookupIsCaseInsensitive() {
        StaffingUpdateStore store = new StaffingUpdateStore();
        store.update(event("W-05", 4));
        assertTrue(store.findById("w-05").isPresent());
        assertTrue(store.findById(" w-05 ").isPresent());
    }

    @Test
    void ignoresEventsWithoutWardId() {
        StaffingUpdateStore store = new StaffingUpdateStore();
        store.update(null);
        store.update(event(null, 1));
        store.update(event("  ", 1));
        assertEquals(0, store.size());
        assertTrue(store.findById("W-05").isEmpty());
    }

    @Test
    void subscriberParsesTopicPayload() throws Exception {
        StaffingUpdateStore store = new StaffingUpdateStore();
        StaffingEventSubscriber subscriber = new StaffingEventSubscriber(
            "tcp://localhost:61616", "staffing-events-topic", store, new ObjectMapper());
        String json = """
            {"wardId":"W-05","wing":"East Wing","department":"Paediatrics",\
            "bedsAvailable":5,"emergencyStatus":3,"doctorsOnCall":4,\
            "timestamp":"2026-01-01T00:00:00Z"}""";
        subscriber.onTextMessage(json);
        StaffingEvent stored = store.findById("W-05").orElseThrow();
        assertEquals("East Wing", stored.getWing());
        assertEquals(4, stored.getDoctorsOnCall());
    }

    @Test
    void subscriberIgnoresPoisonMessages() {
        StaffingUpdateStore store = new StaffingUpdateStore();
        StaffingEventSubscriber subscriber = new StaffingEventSubscriber(
            "tcp://localhost:61616", "staffing-events-topic", store, new ObjectMapper());
        subscriber.onTextMessage("not json at all");
        subscriber.onTextMessage("{\"wing\":\"East Wing\"}");
        subscriber.onTextMessage(null);
        assertEquals(0, store.size());
    }
}
