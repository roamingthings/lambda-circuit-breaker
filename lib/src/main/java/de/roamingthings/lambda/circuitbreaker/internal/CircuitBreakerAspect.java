package de.roamingthings.lambda.circuitbreaker.internal;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclarePrecedence;
import de.roamingthings.lambda.circuitbreaker.Safeguarded;
import org.aspectj.lang.annotation.Pointcut;

/**
 * Aspect that handles the {@link Safeguarded} annotation.
 * It uses the {@link CircuitBreakerHandler} to do the job.
 */
@Aspect
// CircuitBreakerHandler annotation should come first before large message
@DeclarePrecedence("de.roamingthings.lambda.circuitbreaker.internal.CircuitBreakerHandler, *")
public class CircuitBreakerAspect {

    @Pointcut("@annotation(safeguarded)")
    public void callAt(Safeguarded safeguarded) {
        // This method is empty, because the pointcut is only used as a location
    }

    @Around(value = "callAt(safeguarded) && execution(@Safeguarded * *.*(..))", argNames = "pjp,safeguarded")
    public Object around(ProceedingJoinPoint pjp,
                         Safeguarded safeguarded) throws Throwable {
        var id = safeguarded.id();
        var triggeringExceptions = safeguarded.trippedBy();
        var circuitBreakerHandler = new CircuitBreakerHandler(pjp, id, triggeringExceptions);
        return circuitBreakerHandler.handle();
    }
}
