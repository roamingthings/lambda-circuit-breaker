package de.roamingthings.lambda.circuitbreaker;

import java.time.Duration;

public class CircuitBreakerConfig {

    private final long expirationInSeconds;

    public CircuitBreakerConfig(long expirationInSeconds) {
        this.expirationInSeconds = expirationInSeconds;
    }

    public long getExpirationInSeconds() {
        return expirationInSeconds;
    }

    /**
     * Create a builder that can be used to configure and create a {@link CircuitBreakerConfig}.
     *
     * @return a new instance of {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private long expirationInSeconds = 5 * 60L; // 5 minutes

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(
                    expirationInSeconds
            );
        }

        /**
         * The number of seconds to wait before a record is expired
         *
         * @param expiration expiration of the record in the store
         * @return the instance of the builder (to chain operations)
         */
        public Builder withExpiration(Duration expiration) {
            this.expirationInSeconds = expiration.getSeconds();
            return this;
        }
    }
}
