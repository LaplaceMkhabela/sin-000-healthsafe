package co.wethinkcode.healthsafe;

import java.util.Optional;

/** Source of ward data for the schedule computation. */
public interface WardLookup {

    /**
     * Finds a ward by id, or empty when the ward does not exist.
     *
     * @throws DownstreamUnavailableException when the ward source cannot be reached
     */
    Optional<Ward> findById(String wardId);
}