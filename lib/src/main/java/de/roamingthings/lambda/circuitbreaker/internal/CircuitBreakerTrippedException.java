package de.roamingthings.lambda.circuitbreaker.internal;

public class CircuitBreakerTrippedException extends RuntimeException {

    public CircuitBreakerTrippedException(String message, Throwable cause) {
        super(message, cause);
    }
}
