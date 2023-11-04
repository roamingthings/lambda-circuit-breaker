package de.roamingthings.lambda.circuitbreaker;

import de.roamingthings.lambda.circuitbreaker.exceptions.CircuitBreakerOpenException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * &#64;Safeguarded is used to signal that the annotated method is safeguarded by a circuit breaker:<br/>
 * When the annotated method is called and the circuit breaker with the given {@code id} is closed the method will be
 * executed normally. When one of the exceptions listed in {@code triggeredBy} is caught during that call,
 * the circuit breaker will transition to the _open_ state.
 * <p>
 * When the circuit breaker is open, the annotated method will not be called.
 * Instead, a {@link CircuitBreakerOpenException} will be thrown.<br/>
 *
 * <pre>
 *     &#64;SafeGuarded(id = "my-circuit-breaker", triggeredBy = {MyException.class})
 *     public String callClient(String someParameter) {
 *         // ...
 *         return something;
 *     }
 * </pre>
 * <br/>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Safeguarded {

    /**
     * A logical identifier for the circuit breaker.
     *
     * @return The identifier
     */
    String id();

    /**
     * The exceptions that will trigger the circuit breaker to open.
     * <p>
     * When one of these exceptions is caught during the execution of the annotated method, the circuit breaker will
     * transition to the _open_ state.
     * <p>
     * If no exceptions are listed, the circuit breaker will open on any exception.
     *
     * @return The exception that trigger the circuit breaker to open
     */
    Class<? extends Exception>[] trippedBy() default {};
}
