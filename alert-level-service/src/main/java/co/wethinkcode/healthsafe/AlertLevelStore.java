package co.wethinkcode.healthsafe;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory Emergency Status store. Defaults to 0 and only accepts 0-8,
 * where 8 is a full Code Blue.
 */
public final class AlertLevelStore {

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 8;

    private final AtomicInteger level = new AtomicInteger(MIN_LEVEL);

    /** Current Emergency Status, always 0-8. */
    public int current() {
        return level.get();
    }

    /**
     * Updates the status, rejecting anything outside 0-8 with {@link IllegalArgumentException}.
     *
     * @return the new level
     */
    public int update(int newLevel) {
        if (newLevel < MIN_LEVEL || newLevel > MAX_LEVEL) {
            throw new IllegalArgumentException(
                "Emergency Status must be between " + MIN_LEVEL + " and " + MAX_LEVEL + ", got " + newLevel);
        }
        level.set(newLevel);
        return level.get();
    }
}