package de.roamingthings.lambda.circuitbreaker.internal;

import de.roamingthings.lambda.circuitbreaker.CircuitBreaker;
import de.roamingthings.lambda.circuitbreaker.exceptions.CircuitBreakerOpenException;
import de.roamingthings.lambda.circuitbreaker.persistence.PersistenceStore;
import org.aspectj.lang.ProceedingJoinPoint;

import java.time.Instant;
import java.util.stream.Stream;

public class CircuitBreakerHandler {

    private final ProceedingJoinPoint joinPoint;
    private final String circuitBreakerId;
    private final Class<? extends Exception>[] triggeringExceptions;
    private final PersistenceStore persistenceStore;

    public CircuitBreakerHandler(ProceedingJoinPoint joinPoint, String circuitBreakerId, Class<? extends Exception>[] triggeringExceptions) {
        this.joinPoint = joinPoint;
        this.circuitBreakerId = circuitBreakerId;
        this.triggeringExceptions = triggeringExceptions;
        this.persistenceStore = CircuitBreaker.getInstance().getPersistenceStore();
        this.persistenceStore.configure(CircuitBreaker.getInstance().getConfig());
    }

    /**
     * Main entry point for handling idempotent execution of a function.
     *
     * @return the result of proceeding
     * @throws Throwable if the invoked proceed throws anything
     */
    public Object handle() throws Throwable {
        // Check if circuit breaker is open
        if (isCircuitBreakerOpen(circuitBreakerId)) {
            throw new CircuitBreakerOpenException("Circuit with id " + circuitBreakerId + " is open");
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            if (isNullOrEmpty(triggeringExceptions) || isTrippingException(throwable)) {
                openCircuitBreaker(circuitBreakerId, throwable);
                throw new CircuitBreakerTrippedException("Circuit with id " + circuitBreakerId + " tripped", throwable);
            } else {
                throw throwable;
            }
        }
    }

    private boolean isTrippingException(Throwable throwable) {
        return Stream.of(triggeringExceptions)
                .anyMatch(exception -> exception.isAssignableFrom(throwable.getClass()));
    }

    private void openCircuitBreaker(String circuitBreakerId, Throwable cause) {
        var now = Instant.now();
        persistenceStore.saveCircuitBreakerOpen(circuitBreakerId, cause, now);
    }

    private boolean isCircuitBreakerOpen(String circuitBreakerId) {
        var now = Instant.now();
        var circuitBreakerStatus = persistenceStore.fetchCircuitBreakerState(circuitBreakerId, now);
        return circuitBreakerStatus == PersistenceStore.Status.OPEN;
    }

    private static boolean isNullOrEmpty(Class<? extends Exception>[] array) {
        return array == null || array.length == 0;
    }
}
