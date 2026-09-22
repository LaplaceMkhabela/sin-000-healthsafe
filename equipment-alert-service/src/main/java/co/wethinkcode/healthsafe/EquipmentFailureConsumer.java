package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guaranteed-delivery consumer of the {@code equipment-failure-queue} queue.
 *
 * <p>Guarantees, in contrast to the stage 3 staffing topic:</p>
 * <ul>
 *   <li>the producer sends {@code PERSISTENT} messages, so alerts survive a
 *       broker restart and wait in the queue while this service is down;</li>
 *   <li>this consumer uses {@code CLIENT_ACKNOWLEDGE} and acknowledges a
 *       message only after it has been recorded — anything else is
 *       redelivered via {@code Session.recover()};</li>
 *   <li>unparseable poison messages are logged and acknowledged (dropped) so
 *       one bad payload cannot wedge the queue forever.</li>
 * </ul>
 *
 * <p>Runs a blocking receive loop on a daemon thread with reconnect retries,
 * so the REST endpoints stay up when the broker is unavailable.</p>
 */
public final class EquipmentFailureConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EquipmentFailureConsumer.class);
    private static final long RECONNECT_DELAY_MS = 10_000;
    private static final long REDELIVER_DELAY_MS = 5_000;

    private final String brokerUrl;
    private final String queueName;
    private final AlertStore store;
    private final ObjectMapper mapper;

    private volatile boolean running;
    private volatile Thread worker;
    private volatile Connection connection;

    public EquipmentFailureConsumer(AlertStore store) {
        this(MqConfig.BROKER_URL, MqConfig.QUEUE, store, new ObjectMapper());
    }

    EquipmentFailureConsumer(String brokerUrl, String queueName, AlertStore store, ObjectMapper mapper) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.store = store;
        this.mapper = mapper;
    }

    /** Starts the background receive loop (daemon). Best-effort: never throws. */
    public synchronized void startAsync() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "equipment-failure-consumer");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Handles one queue payload. Returns true when the message should be
     * acknowledged (recorded, or poison dropped with a warning) and false when
     * it must be redelivered.
     */
    boolean handlePayload(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Ignoring empty equipment failure message (acknowledging poison)");
            return true;
        }
        EquipmentAlert alert;
        try {
            alert = mapper.readValue(json, EquipmentAlert.class);
        } catch (Exception e) {
            log.warn("Ignoring unparseable equipment failure message, acknowledging to avoid a poison loop ({}): {}",
                e.getMessage(), json);
            return true;
        }
        if (alert.getId() == null || alert.getId().isBlank()
            || alert.getWardId() == null || alert.getWardId().isBlank()
            || alert.getDescription() == null || alert.getDescription().isBlank()) {
            log.warn("Ignoring equipment failure message with missing id/wardId/description, acknowledging: {}", json);
            return true;
        }
        if (!store.record(alert)) {
            log.warn("Failed to record equipment alert {}, will redeliver", alert.getId());
            return false;
        }
        log.info("Recorded equipment failure alert {} for ward {} (severity {})",
            alert.getId(), alert.getWardId(), alert.getSeverity());
        return true;
    }

    private void runLoop() {
        while (running) {
            try {
                ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
                connection = factory.createConnection();
                connection.start();
                Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                Queue queue = session.createQueue(queueName);
                MessageConsumer consumer = session.createConsumer(queue);
                log.info("Listening on equipment failure queue {} at {}", queueName, brokerUrl);
                while (running) {
                    Message message = consumer.receive(1_000);
                    if (message == null) {
                        continue;
                    }
                    try {
                        if (message instanceof TextMessage text) {
                            if (handlePayload(text.getText())) {
                                message.acknowledge();
                            } else {
                                session.recover();
                                Thread.sleep(REDELIVER_DELAY_MS);
                            }
                        } else {
                            log.warn("Ignoring non-text equipment failure message of type {}, acknowledging",
                                message.getClass().getSimpleName());
                            message.acknowledge();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.warn("Error handling equipment failure message, will redeliver: {}", e.getMessage());
                        try {
                            session.recover();
                        } catch (Exception recoverFailure) {
                            log.warn("Session recover failed: {}", recoverFailure.getMessage());
                            break;
                        }
                    }
                }
                consumer.close();
                session.close();
            } catch (Exception e) {
                if (running) {
                    log.warn("Equipment failure queue subscription failed (retry in {}s): {}",
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
                log.warn("Error closing equipment failure queue connection: {}", e.getMessage());
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
