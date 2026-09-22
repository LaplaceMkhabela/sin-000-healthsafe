package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to the shared {@code staffing-events-topic} topic and records each
 * staffing update in the {@link StaffingUpdateStore}.
 *
 * <p>Runs on a daemon thread with reconnect retries so ward-service keeps
 * serving its REST endpoints even when the broker is down at startup (e.g.
 * {@code docker compose up} was run after the services).</p>
 */
public final class StaffingEventSubscriber implements MessageListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StaffingEventSubscriber.class);
    private static final long RECONNECT_DELAY_MS = 10_000;

    private final String brokerUrl;
    private final String topicName;
    private final StaffingUpdateStore store;
    private final ObjectMapper mapper;

    private volatile boolean running;
    private volatile Thread worker;
    private volatile Connection connection;

    public StaffingEventSubscriber(StaffingUpdateStore store) {
        this(MqConfig.BROKER_URL, MqConfig.TOPIC, store, new ObjectMapper());
    }

    StaffingEventSubscriber(String brokerUrl, String topicName, StaffingUpdateStore store, ObjectMapper mapper) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
        this.store = store;
        this.mapper = mapper;
    }

    /** Starts the background listener thread (daemon). Best-effort: never throws. */
    public synchronized void startAsync() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "staffing-events-subscriber");
        worker.setDaemon(true);
        worker.start();
    }

    /** Parses one topic payload and records it; ignores poison messages without throwing. */
    void onTextMessage(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            StaffingEvent event = mapper.readValue(json, StaffingEvent.class);
            if (event.getWardId() == null || event.getWardId().isBlank()) {
                log.warn("Ignoring staffing event with no wardId: {}", json);
                return;
            }
            store.update(event);
            log.info("Received staffing update for ward {} ({} doctors on call, status {})",
                event.getWardId(), event.getDoctorsOnCall(), event.getEmergencyStatus());
        } catch (Exception e) {
            log.warn("Ignoring unparseable staffing event ({}): {}", e.getMessage(), json);
        }
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage text) {
                onTextMessage(text.getText());
            } else {
                log.warn("Ignoring non-text staffing message of type {}", message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("Error handling staffing message: {}", e.getMessage());
        }
    }

    private void runLoop() {
        while (running) {
            try {
                ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
                connection = factory.createConnection();
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic = session.createTopic(topicName);
                MessageConsumer consumer = session.createConsumer(topic);
                consumer.setMessageListener(this);
                log.info("Subscribed to staffing topic {} at {}", topicName, brokerUrl);
                // Block until closed; the listener does the work.
                while (running) {
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                consumer.close();
                session.close();
            } catch (Exception e) {
                if (running) {
                    log.warn("Staffing topic subscription failed (retry in {}s): {}",
                        RECONNECT_DELAY_MS / 1_000, e.getMessage());
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                closeConnection();
            }
        }
    }

    private void closeConnection() {
        Connection c = connection;
        connection = null;
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                log.warn("Error closing staffing topic connection: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        closeConnection();
        Thread w = worker;
        worker = null;
        if (w != null) {
            w.interrupt();
        }
    }
}
