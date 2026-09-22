package co.wethinkcode.healthsafe;

/**
 * Publishes equipment failure alerts to the {@code equipment-failure-queue}
 * queue. Unlike the staffing topic broadcast, publishing here is load-bearing:
 * callers must know whether the alert reached the queue, so implementations
 * signal failure by throwing.
 */
public interface EquipmentAlertPublisher {

    /**
     * Delivers the alert to the queue.
     *
     * @throws ActiveMqEquipmentAlertPublisher.EquipmentPublishException when the
     *         alert cannot be delivered (callers translate this to a retryable
     *         error, not a silent drop)
     */
    void publish(EquipmentAlert alert);
}
