package de.roamingthings.circuitbreaker;

import de.roamingthings.lambda.circuitbreaker.Safeguarded;
import io.micronaut.function.aws.MicronautRequestHandler;

import java.time.Instant;

public class SampleHandler extends MicronautRequestHandler<InputEvent, OutputEvent> {

    @Override
    @Safeguarded(id = "sample")
    public OutputEvent execute(InputEvent input) {
        Instant startTime = null;
        try {
            startTime = Instant.parse(input.startTime());
        } catch (Exception e) {
            // Swallow exception
        }

        if ((startTime == null || executionOlderThan30Secs(startTime)) && input.value().equals("fail")) {
            throw new RuntimeException("Badaboom");
        }
        return new OutputEvent(input.value());
    }

    private static boolean executionOlderThan30Secs(Instant startTime) {
        return Instant.now().isBefore(startTime.plusSeconds(30));
    }
}
