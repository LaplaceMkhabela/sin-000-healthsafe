package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentFailureConsumerTest {

    private EquipmentFailureConsumer consumer(AlertStore store) {
        return new EquipmentFailureConsumer("tcp://localhost:61616", "equipment-failure-queue", store,
            new ObjectMapper());
    }

    private String payload(String id) {
        return "{\"id\":\"" + id + "\",\"wardId\":\"W-05\",\"wing\":\"East Wing\","
            + "\"description\":\"ventilator failure\",\"severity\":\"CRITICAL\","
            + "\"timestamp\":\"2026-01-01T00:00:00Z\"}";
    }

    @Test
    void validAlertIsRecordedAndAcknowledged() {
        AlertStore store = new AlertStore();
        assertTrue(consumer(store).handlePayload(payload("a-1")));
        assertEquals(1, store.size());
        assertEquals("ventilator failure", store.findById("a-1").orElseThrow().getDescription());
    }

    @Test
    void poisonMessagesAreAcknowledgedWithoutRecording() {
        AlertStore store = new AlertStore();
        EquipmentFailureConsumer consumer = consumer(store);
        // Unparseable JSON: acknowledge so one bad payload cannot wedge the queue.
        assertTrue(consumer.handlePayload("not json at all"));
        // Missing id/wardId/description: same treatment.
        assertTrue(consumer.handlePayload("{\"wardId\":\"W-05\"}"));
        assertTrue(consumer.handlePayload(null));
        assertTrue(consumer.handlePayload("  "));
        assertEquals(0, store.size());
    }

    @Test
    void storeKeepsEveryAlertById() {
        AlertStore store = new AlertStore();
        EquipmentFailureConsumer consumer = consumer(store);
        assertTrue(consumer.handlePayload(payload("a-1")));
        assertTrue(consumer.handlePayload(payload("a-2")));
        assertEquals(2, store.size());
        assertEquals(2, store.all().size());
        assertTrue(store.findById("nope").isEmpty());
    }
}
