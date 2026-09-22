package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveMQ {@link EquipmentAlertPublisher} that delivers to the shared
 * {@code equipment-failure-queue} queue.
 *
 * <p>A queue (not a topic) is the right shape here: each failure alert must
 * reach exactly one consumer and survive a consumer outage, so messages are
 * sent {@link DeliveryMode#PERSISTENT} and the consumer acknowledges only
 * after handling them — contrast with the stage 3 staffing topic, which is
 * non-persistent fire-and-forget broadcast.</p>
 */
public final class ActiveMqEquipmentAlertPublisher implements EquipmentAlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(ActiveMqEquipmentAlertPublisher.class);

    private final String brokerUrl;
    private final String queueName;
    private final ObjectMapper mapper;

    public ActiveMqEquipmentAlertPublisher() {
        this(MqConfig.BROKER_URL, MqConfig.QUEUE, new ObjectMapper());
    }

    public ActiveMqEquipmentAlertPublisher(String brokerUrl, String queueName) {
        this(brokerUrl, queueName, new ObjectMapper());
    }

    ActiveMqEquipmentAlertPublisher(String brokerUrl, String queueName, ObjectMapper mapper) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.mapper = mapper;
    }

    @Override
    public void publish(EquipmentAlert alert) {
        try {
            String json = mapper.writeValueAsString(alert);
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            try (Connection connection = factory.createConnection()) {
                connection.start();
                try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                    Queue queue = session.createQueue(queueName);
                    try (MessageProducer producer = session.createProducer(queue)) {
                        // Guaranteed delivery: the broker persists the message
                        // until a consumer acknowledges it.
                        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                        TextMessage message = session.createTextMessage(json);
                        message.setStringProperty("alertId", alert.getId());
                        message.setStringProperty("wardId", alert.getWardId());
                        producer.send(message);
                    }
                }
            }
            log.info("Published equipment failure alert {} for ward {} to queue {}",
                alert.getId(), alert.getWardId(), queueName);
        } catch (Exception e) {
            log.warn("Failed to publish equipment failure alert for ward {}: {}",
                alert == null ? null : alert.getWardId(), e.getMessage());
            throw new EquipmentPublishException("failed to deliver equipment failure alert: " + e.getMessage(), e);
        }
    }

    /** Raised when the alert cannot be delivered to the queue. Callers must not swallow it. */
    public static final class EquipmentPublishException extends RuntimeException {
        public EquipmentPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
