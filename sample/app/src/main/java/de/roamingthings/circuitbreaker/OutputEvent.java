package de.roamingthings.circuitbreaker;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record OutputEvent(String value) {
}
