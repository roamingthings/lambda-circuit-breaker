package de.roamingthings.lambda.circuitbreaker.persistence;

import java.time.Instant;

/**
 * @param expiryTimestamp This field is controlling how long the circuit breaker will stay open.
 *                        It is stored in _seconds since epoch_.
 *                        <p>
 *                        DynamoDB's TTL mechanism is used to remove the record once the
 *                        expiry has been reached, and subsequent execution of the request
 *                        will be permitted. The user must configure this on their table.
 */
public record CircuitBreakerStatusRecord(String circuitBreakerId, PersistenceStore.Status status, long expiryTimestamp, String cause) {

    /**
     * Check if data record is expired (based on expiration configured in the {@link de.roamingthings.lambda.circuitbreaker.CircuitBreakerConfig})
     *
     * @return Whether the record is currently expired or not
     */
    public boolean isExpired(Instant now) {
        return expiryTimestamp != 0 && now.isAfter(Instant.ofEpochSecond(expiryTimestamp));
    }
}
