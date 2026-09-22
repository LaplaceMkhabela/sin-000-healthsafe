package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveMQ {@link StaffingEventPublisher} that broadcasts to the shared
 * {@code staffing-events-topic} topic.
 *
 * <p>A topic (not a queue) is the right shape here: every subscriber gets every
 * staffing update, delivery is fire-and-forget, and the producer never needs a
 * reply. That decouples staffing-service from whoever consumes the updates
 * (ward-service, dashboards) — contrast with the stage 4 equipment-failure
 * queue, where exactly one consumer must reliably receive each alert.</p>
 *
 * <p>Opens a short-lived JMS connection per publish so there is no long-lived
 * connection lifecycle to manage in the request path; fine at this volume.</p>
 */
public final class ActiveMqStaffingEventPublisher implements StaffingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ActiveMqStaffingEventPublisher.class);

    private final String brokerUrl;
    private final String topicName;
    private final ObjectMapper mapper;

    public ActiveMqStaffingEventPublisher() {
        this(MqConfig.BROKER_URL, MqConfig.TOPIC, new ObjectMapper());
    }

    public ActiveMqStaffingEventPublisher(String brokerUrl, String topicName) {
        this(brokerUrl, topicName, new ObjectMapper());
    }

    ActiveMqStaffingEventPublisher(String brokerUrl, String topicName, ObjectMapper mapper) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
        this.mapper = mapper;
    }

    @Override
    public void publish(Schedule schedule) {
        try {
            String json = mapper.writeValueAsString(StaffingEvent.from(schedule));
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            try (Connection connection = factory.createConnection()) {
                connection.start();
                try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                    Topic topic = session.createTopic(topicName);
                    try (MessageProducer producer = session.createProducer(topic)) {
                        // Broadcast semantics: fire-and-forget, no persistence needed.
                        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                        TextMessage message = session.createTextMessage(json);
                        producer.send(message);
                    }
                }
            }
            log.info("Published staffing event for ward {} to topic {}", schedule.getWardId(), topicName);
        } catch (Exception e) {
            // Best-effort: the REST layer logs and still returns the computed schedule.
            log.warn("Failed to publish staffing event for ward {}: {}",
                schedule == null ? null : schedule.getWardId(), e.getMessage());
            throw new StaffingPublishException("failed to publish staffing event: " + e.getMessage(), e);
        }
    }

    /** Raised when the broadcast to the topic fails. Callers translate it to a log, not a 5xx. */
    public static final class StaffingPublishException extends RuntimeException {
        public StaffingPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
