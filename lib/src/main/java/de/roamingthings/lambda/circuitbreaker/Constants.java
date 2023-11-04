package de.roamingthings.lambda.circuitbreaker;

public abstract class Constants {

    public static final String CIRCUIT_BREAKER_DISABLED_ENV = "CIRCUIT_BREAKER_DISABLED";
    public static final String AWS_REGION_ENV = "AWS_REGION";

    private Constants() {
        // Prevent direct instantiation
    }
}
