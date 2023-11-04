package de.roamingthings.lambda.circuitbreaker.exceptions;

public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
