package de.roamingthings.circuitbreaker;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record InputEvent(String value, String startTime) {
}
