package de.roamingthings.lambda.circuitbreaker.internal;

import com.amazonaws.services.lambda.runtime.Context;
import de.roamingthings.lambda.circuitbreaker.CircuitBreaker;
import de.roamingthings.lambda.circuitbreaker.CircuitBreakerConfig;
import de.roamingthings.lambda.circuitbreaker.exceptions.CircuitBreakerOpenException;
import de.roamingthings.lambda.circuitbreaker.handlers.CircuitBreakerEnabledFailingFunction;
import de.roamingthings.lambda.circuitbreaker.handlers.CircuitBreakerEnabledForExceptionFailingFunction;
import de.roamingthings.lambda.circuitbreaker.handlers.SomeTrippingException;
import de.roamingthings.lambda.circuitbreaker.handlers.SomethingWentWrongException;
import de.roamingthings.lambda.circuitbreaker.model.InputEvent;
import de.roamingthings.lambda.circuitbreaker.persistence.PersistenceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static de.roamingthings.lambda.circuitbreaker.handlers.CircuitBreakerEnabledFailingFunction.CIRCUIT_BREAKER_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerAspectTest {

    @Mock
    private Context context;

    @Mock
    private PersistenceStore store;

    @Test
    void firstCall_shouldCreateInStoreWhenFunctionThrowsExceptionAndThrowsCircuitBreakerTrippedException() {
        CircuitBreaker.config()
                .withPersistenceStore(store)
                .withConfig(CircuitBreakerConfig.builder()
                        .build()
                ).configure();
        var function = new CircuitBreakerEnabledFailingFunction();
        var input = new InputEvent("Something");

        assertThatThrownBy(() ->function.handleRequest(input, context))
                .isInstanceOf(CircuitBreakerTrippedException.class)
                .cause()
                .isInstanceOf(SomethingWentWrongException.class);

        var circuitBreakerIdCaptor = ArgumentCaptor.forClass(String.class);
        var expiryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(store).saveCircuitBreakerOpen(circuitBreakerIdCaptor.capture(), any(), expiryCaptor.capture());
    }

    @Test
    void firstCall_shouldNotCreateInStoreWhenFunctionThrowsUnexpectedException() {
        CircuitBreaker.config()
                .withPersistenceStore(store)
                .withConfig(CircuitBreakerConfig.builder()
                        .build()
                ).configure();
        var function = new CircuitBreakerEnabledForExceptionFailingFunction();
        var input = new InputEvent("Something");

        assertThatThrownBy(() -> function.handleRequest(input, context))
                .isInstanceOf(SomethingWentWrongException.class);

        verify(store, never()).saveCircuitBreakerOpen(any(), any(), any());
    }

    @Test
    void firstCall_shouldCreateInStoreWhenFunctionThrowsExceptionWhenCircuitBreakerIsExplicitlyClose() {
        CircuitBreaker.config()
                .withPersistenceStore(store)
                .withConfig(CircuitBreakerConfig.builder()
                        .build()
                ).configure();
        doReturn(PersistenceStore.Status.CLOSED).when(store).fetchCircuitBreakerState(eq(CIRCUIT_BREAKER_ID), any());
        var function = new CircuitBreakerEnabledFailingFunction();
        var input = new InputEvent("Something");

        assertThatThrownBy(() ->function.handleRequest(input, context))
                .isInstanceOf(CircuitBreakerTrippedException.class)
                .cause()
                .isInstanceOf(SomethingWentWrongException.class);

        var circuitBreakerIdCaptor = ArgumentCaptor.forClass(String.class);
        var expiryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(store).saveCircuitBreakerOpen(circuitBreakerIdCaptor.capture(), any(), expiryCaptor.capture());
    }

    @Test
    void secondCall_shouldCreateInStoreWhenFunctionThrowsExceptionAndThrowsCircuitBreakerOpenException() {
        CircuitBreaker.config()
                .withPersistenceStore(store)
                .withConfig(CircuitBreakerConfig.builder()
                        .build()
                ).configure();
        doReturn(PersistenceStore.Status.OPEN).when(store).fetchCircuitBreakerState(eq(CIRCUIT_BREAKER_ID), any());
        var function = new CircuitBreakerEnabledFailingFunction();
        var input = new InputEvent("Something");

        assertThatThrownBy(() -> function.handleRequest(input, context))
                .isInstanceOf(CircuitBreakerOpenException.class);
    }

    @Test
    void firstCall_shouldCreateInStoreWhenFunctionThrowsExceptionAndThrowsCircuitBreakerOpenException() {
        CircuitBreaker.config()
                .withPersistenceStore(store)
                .withConfig(CircuitBreakerConfig.builder()
                        .build()
                ).configure();
        doReturn(PersistenceStore.Status.OPEN).when(store).fetchCircuitBreakerState(eq(CIRCUIT_BREAKER_ID), any());
        var function = new CircuitBreakerEnabledFailingFunction();
        var input = new InputEvent("Something");

        assertThatThrownBy(() -> function.handleRequest(input, context))
                .isInstanceOf(CircuitBreakerOpenException.class);
    }
}
