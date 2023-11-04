package de.roamingthings.lambda.circuitbreaker.persistence;

import de.roamingthings.lambda.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public abstract class PersistenceStoreBase implements PersistenceStore {

    private boolean configured = false;
    private long expirationInSeconds = 5 * 60L; // 5 minutes default

    private static final Logger log = LoggerFactory.getLogger(PersistenceStoreBase.class);

    @Override
    public void configure(CircuitBreakerConfig config) {
        if (configured) {
            // prevent being reconfigured multiple times
            return;
        }

        expirationInSeconds = config.getExpirationInSeconds();

        configured = true;
    }

    @Override
    public void saveCircuitBreakerOpen(String circuitBreakerId, Throwable cause, Instant now) {
        log.debug("Saving circuit breaker status as OPEN");
        createCircuitBreakerStatus(circuitBreakerId, PersistenceStore.Status.OPEN, cause, now);
    }

    @Override
    public Status fetchCircuitBreakerState(String circuitBreakerId, Instant now) {
        return fetchRecord(circuitBreakerId)
                .filter(statusRecord -> !statusRecord.isExpired(now))
                .map(CircuitBreakerStatusRecord::status)
                .orElse(Status.CLOSED);
    }

    public abstract void createRecord(CircuitBreakerStatusRecord statusRecord, Instant now);

    /**
     * Update item in persistence store. This method should be able to overwrite an existing item.
     * @param statusRecord CircuitBreakerStatusRecord instance
     */
    abstract void updateRecord(CircuitBreakerStatusRecord statusRecord);

    /**
     * Get an item in persistence store
     *
     * @return {@link Optional} containing the record or empty if not found
     */
    abstract Optional<CircuitBreakerStatusRecord> fetchRecord(String circuitBreakerId);

    private void createCircuitBreakerStatus(String circuitBreakerId, PersistenceStore.Status status, Throwable cause, Instant now) {
        String causeMessage = null;
        if (cause != null) {
            causeMessage = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
        }
        var statusRecord = new CircuitBreakerStatusRecord(
                circuitBreakerId,
                status,
                calculateExpiryEpochSecond(now),
                causeMessage
        );
        createRecord(statusRecord, now);
    }

    /**
     * @param now current time
     * @return unix timestamp of expiry date for the record
     */
    private long calculateExpiryEpochSecond(Instant now) {
        return now.plus(expirationInSeconds, ChronoUnit.SECONDS).getEpochSecond();
    }
}
