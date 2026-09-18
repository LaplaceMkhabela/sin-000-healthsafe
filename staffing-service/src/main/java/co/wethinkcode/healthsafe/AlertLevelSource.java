package co.wethinkcode.healthsafe;

/** Source of the hospital Emergency Status (0-8). */
public interface AlertLevelSource {

    /**
     * Returns the current Emergency Status.
     *
     * @throws DownstreamUnavailableException when the source cannot be reached
     */
    int current();
}