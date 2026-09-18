package co.wethinkcode.healthsafe;

/**
 * Raised when a downstream service cannot be reached, times out, or answers with
 * an unexpected status. Endpoints translate this into HTTP 503 for callers.
 */
public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String message) {
        super(message);
    }

    public DownstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}