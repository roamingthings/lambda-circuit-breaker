package de.roamingthings.circuitbreaker;

import de.roamingthings.lambda.circuitbreaker.CircuitBreaker;
import de.roamingthings.lambda.circuitbreaker.CircuitBreakerConfig;
import de.roamingthings.lambda.circuitbreaker.persistence.CircuitBreakerStatusRecord;
import de.roamingthings.lambda.circuitbreaker.persistence.DynamoDBPersistenceStore;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.serde.annotation.SerdeImport;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;

@Singleton
@SerdeImport(CircuitBreakerStatusRecord.class)
@Requires(property = "CIRCUIT_BREAKER_TABLE")
public class CircuitBreakerConfiguration implements ApplicationEventListener<StartupEvent> {

    private final String circuitBreakerTableName;
    private final DynamoDbClient dynamoDbClient;

    public CircuitBreakerConfiguration(
            @Value("${" + EnvironmentVariables.CIRCUIT_BREAKER_TABLE + "}") String circuitBreakerTableName,
            DynamoDbClient dynamoDbClient) {
        this.circuitBreakerTableName = circuitBreakerTableName;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        CircuitBreaker.config()
                .withPersistenceStore(
                        DynamoDBPersistenceStore.builder()
                                .withTableName(circuitBreakerTableName)
                                .withDynamoDbClient(dynamoDbClient)
                                .build())
                .withConfig(CircuitBreakerConfig.builder()
                        .withExpiration(Duration.ofMinutes(1))
                        .build())
                .configure();
    }
}
