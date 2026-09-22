package co.wethinkcode.healthsafe;

/** Publishes a computed on-call schedule as a broadcast event. */
public interface StaffingEventPublisher {

    /**
     * Broadcasts the schedule. Implementations speak to the
     * {@code staffing-events-topic} topic; the call must be best-effort from the
     * REST layer's point of view — a broker failure must not fail the HTTP request.
     */
    void publish(Schedule schedule);
}
