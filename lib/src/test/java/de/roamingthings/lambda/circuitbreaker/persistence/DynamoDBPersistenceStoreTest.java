package de.roamingthings.lambda.circuitbreaker.persistence;

import de.roamingthings.lambda.circuitbreaker.test.DynamoDBTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class DynamoDBPersistenceStoreTest extends DynamoDBTestBase {

    private Map<String, AttributeValue> key;
    private DynamoDBPersistenceStore dynamoDBPersistenceStore;

    @BeforeEach
    public void setup() {
        dynamoDBPersistenceStore = DynamoDBPersistenceStore.builder()
                .withTableName(TABLE_NAME)
                .withDynamoDbClient(client)
                .build();
    }

    @AfterEach
    public void emptyDB() {
        if (key != null) {
            client.deleteItem(DeleteItemRequest.builder().tableName(TABLE_NAME).key(key).build());
            key = null;
        }
    }

    @Test
    void createRecord_shouldCreateRecordInDynamoDB() {
        var now = Instant.now();
        var expiry = now.plus(1, ChronoUnit.HOURS).getEpochSecond();
        var circuitBreakerId = "circuitBreakerIdValue";

        dynamoDBPersistenceStore.createRecord(new CircuitBreakerStatusRecord(
                        circuitBreakerId,
                        PersistenceStore.Status.OPEN,
                        expiry,
                        "The cause"),
                now
        );

        key = Collections.singletonMap("id", AttributeValue.fromS(circuitBreakerId));
        var item = client.getItem(GetItemRequest.builder().tableName(TABLE_NAME).key(key).build()).item();
        assertSoftly(softly -> {
            softly.assertThat(item).isNotNull();
            softly.assertThat(item.get("status").s()).isEqualTo("OPEN");
            softly.assertThat(item.get("expiration").n()).isEqualTo(String.valueOf(expiry));
            softly.assertThat(item.get("cause").s()).isEqualTo("The cause");
        });
    }

    @Test
    void createRecord_shouldNotFailOnSecondCallIfAlreadyOpen() {
        var now = Instant.now();
        var expiry = now.plus(1, ChronoUnit.HOURS).getEpochSecond();
        var circuitBreakerId = "circuitBreakerIdValue";
        var statusRecord = new CircuitBreakerStatusRecord(
                circuitBreakerId,
                PersistenceStore.Status.OPEN,
                expiry,
                "The cause");

        dynamoDBPersistenceStore.createRecord(statusRecord, now);
        assertThatNoException().isThrownBy(() -> dynamoDBPersistenceStore.createRecord(statusRecord, now));
    }

    @ParameterizedTest
    @EnumSource(value = PersistenceStore.Status.class, names = {"CLOSED", "OPEN"})
    void createRecord_shouldCreateRecordInDynamoDB_IfPreviousExpired(PersistenceStore.Status status) {
        var circuitBreakerId = "key";
        key = Collections.singletonMap("id", AttributeValue.fromS(circuitBreakerId));

        // GIVEN: Insert a fake item with same id and expired
        Map<String, AttributeValue> item = new HashMap<>(key);
        var now = Instant.now();
        var expiry = now.minus(30, ChronoUnit.SECONDS).getEpochSecond();
        item.put("expiration", AttributeValue.builder().n(String.valueOf(expiry)).build());
        item.put("status", AttributeValue.fromS(status.toString()));
        item.put("cause", AttributeValue.fromS("Some Cause"));
        client.putItem(PutItemRequest.builder().tableName(TABLE_NAME).item(item).build());

        // WHEN: call putRecord
        var expiry2 = now.plus(3600, ChronoUnit.SECONDS).getEpochSecond();
        dynamoDBPersistenceStore.createRecord(
                new CircuitBreakerStatusRecord(circuitBreakerId,
                        PersistenceStore.Status.OPEN,
                        expiry2,
                        null
                ), now);

        // THEN: an item is inserted
        var itemInDb = client.getItem(GetItemRequest.builder().tableName(TABLE_NAME).key(key).build()).item();
        assertThat(itemInDb).isNotNull();
        assertThat(itemInDb.get("status").s()).isEqualTo("OPEN");
        assertThat(itemInDb.get("expiration").n()).isEqualTo(String.valueOf(expiry2));
        assertThat(itemInDb.get("cause")).isNull();
    }

    @Test
    void fetchRecord_shouldRetrieveExistingRecord() {
        var now = Instant.now();
        var expiry = now.plus(1, ChronoUnit.HOURS).getEpochSecond();
        var circuitBreakerId = "circuitBreakerIdValue";
        var statusRecord = new CircuitBreakerStatusRecord(
                circuitBreakerId,
                PersistenceStore.Status.OPEN,
                expiry,
                "The cause");
        dynamoDBPersistenceStore.createRecord(statusRecord, now);

        var record = dynamoDBPersistenceStore.fetchRecord(circuitBreakerId);

        assertSoftly(softly -> {
            softly.assertThat(record)
                    .isPresent()
                    .get()
                    .isEqualTo(statusRecord);
        });
    }
}
