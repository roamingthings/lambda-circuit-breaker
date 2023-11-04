package de.roamingthings.lambda.circuitbreaker;

import de.roamingthings.lambda.circuitbreaker.persistence.PersistenceStore;

public class CircuitBreaker {

    private CircuitBreakerConfig config;
    private PersistenceStore persistenceStore;

    private CircuitBreaker() {
        // Prevent direct instantiation
    }

    public static CircuitBreaker getInstance() {
        return Holder.instance;
    }

    /**
     * Acts like a builder that can be used to configure the {@link CircuitBreaker}
     *
     * @return a new instance of {@link Config}
     */
    public static Config config() {
        return new Config();
    }

    public CircuitBreakerConfig getConfig() {
        return config;
    }

    private void setConfig(CircuitBreakerConfig config) {
        this.config = config;
    }

    public PersistenceStore getPersistenceStore() {
        if (persistenceStore == null) {
            throw new IllegalStateException("Persistence Store is not set. Please make sure configuration is finalized by calling 'configure()'");
        }
        return persistenceStore;
    }

    private void setPersistenceStore(PersistenceStore persistenceStore) {
        this.persistenceStore = persistenceStore;
    }

    private static class Holder {
        private static final CircuitBreaker instance = new CircuitBreaker();
    }

    public static class Config {

        private CircuitBreakerConfig config;
        private PersistenceStore store;

        /**
         * Use this method after configuring persistence layer (mandatory) and idem potency configuration (optional)
         */
        public void configure() {
            if (store == null) {
                throw new IllegalStateException(
                        "Persistence Layer is null, configure one with 'withPersistenceStore()'");
            }
            if (config == null) {
                config = CircuitBreakerConfig.builder().build();
            }
            CircuitBreaker.getInstance().setConfig(config);
            CircuitBreaker.getInstance().setPersistenceStore(store);
        }

        public Config withPersistenceStore(PersistenceStore persistenceStore) {
            this.store = persistenceStore;
            return this;
        }

        public Config withConfig(CircuitBreakerConfig config) {
            this.config = config;
            return this;
        }
    }
}
