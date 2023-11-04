package de.roamingthings.lambda.circuitbreaker.persistence;

import de.roamingthings.lambda.circuitbreaker.CircuitBreakerConfig;

import java.time.Instant;

public interface PersistenceStore {
    void configure(CircuitBreakerConfig config);

    void saveCircuitBreakerOpen(String circuitBreakerId, Throwable cause, Instant now);

    Status fetchCircuitBreakerState(String circuitBreakerId, Instant now);

    /**
     * Status of the record:
     * <ul>
     *  <li>OPEN: circuit breaker is open</li>
     *  <li>CLOSED: circuit breaker is closed: the record does not exist or has a status of closed</li>
     * </ul>
     */
    enum Status {
        OPEN("OPEN"), CLOSED("CLOSED");

        private final String status;

        Status(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return status;
        }
    }
}
