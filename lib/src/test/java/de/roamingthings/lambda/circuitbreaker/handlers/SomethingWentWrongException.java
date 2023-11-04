package de.roamingthings.lambda.circuitbreaker.handlers;

public class SomethingWentWrongException extends RuntimeException {

    public SomethingWentWrongException(String message) {
        super(message);
    }
}
