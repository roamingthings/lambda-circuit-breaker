package de.roamingthings.lambda.circuitbreaker.handlers;

public class SomeTrippingException extends RuntimeException {

    public SomeTrippingException(String message) {
        super(message);
    }
}
