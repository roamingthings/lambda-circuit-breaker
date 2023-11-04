package de.roamingthings.lambda.circuitbreaker.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import de.roamingthings.lambda.circuitbreaker.Safeguarded;
import de.roamingthings.lambda.circuitbreaker.model.InputEvent;
import de.roamingthings.lambda.circuitbreaker.model.OutputEvent;

public class CircuitBreakerEnabledForExceptionFailingFunction implements RequestHandler<InputEvent, OutputEvent> {

    public static final String CIRCUIT_BREAKER_ID = "a-circuit-breaker";

    @Override
    @Safeguarded(id = CIRCUIT_BREAKER_ID, trippedBy = SomeTrippingException.class)
    public OutputEvent handleRequest(InputEvent input, Context context) {
        throw new SomethingWentWrongException("Something went wrong");
    }
}
