package de.roamingthings.lambda.circuitbreaker.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.utils.StringUtils;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.roamingthings.lambda.circuitbreaker.Constants.AWS_REGION_ENV;
import static de.roamingthings.lambda.circuitbreaker.Constants.CIRCUIT_BREAKER_DISABLED_ENV;

public class DynamoDBPersistenceStore extends PersistenceStoreBase {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBPersistenceStore.class);

    private final String tableName;
    private final String keyAttr;
    private final String staticPkValue;
    private final String sortKeyAttr;
    private final String expiryAttr;
    private final String statusAttr;
    private final String causeAttr;
    private final DynamoDbClient dynamoDbClient;

    private DynamoDBPersistenceStore(
            String tableName,
            String keyAttr,
            String staticPkValue,
            String sortKeyAttr,
            String expiryAttr,
            String statusAttr,
            String causeAttr,
            DynamoDbClient dynamoDbClient) {
        this.tableName = tableName;
        this.keyAttr = keyAttr;
        this.staticPkValue = staticPkValue;
        this.sortKeyAttr = sortKeyAttr;
        this.expiryAttr = expiryAttr;
        this.statusAttr = statusAttr;
        this.causeAttr = causeAttr;

        if (dynamoDbClient != null) {
            this.dynamoDbClient = dynamoDbClient;
        } else {
            String idempotencyDisabledEnv = System.getenv().get(CIRCUIT_BREAKER_DISABLED_ENV);
            if (idempotencyDisabledEnv == null || idempotencyDisabledEnv.equalsIgnoreCase("false")) {
                this.dynamoDbClient = DynamoDbClient.builder()
                        .httpClient(UrlConnectionHttpClient.builder().build())
                        .region(Region.of(System.getenv(AWS_REGION_ENV)))
                        .build();
            } else {
                // we do not want to create a DynamoDbClient if circuit breaker is disabled
                // null is ok as circuit breaker won't be called
                this.dynamoDbClient = null;
            }
        }
    }

    @Override
    public void createRecord(CircuitBreakerStatusRecord statusRecord, Instant now) {
        var item = new HashMap<>(createKey(statusRecord.circuitBreakerId()));
        item.put(this.expiryAttr, AttributeValue.fromN(String.valueOf(statusRecord.expiryTimestamp())));
        item.put(this.statusAttr, AttributeValue.fromS(statusRecord.status().toString()));
        if (statusRecord.cause() != null) {
            item.put(this.causeAttr, AttributeValue.fromS(statusRecord.cause()));
        }

        try {
            log.debug("Putting record for circuit breaker: {}", statusRecord.circuitBreakerId());

            var expressionAttributeNames = Stream.of(
                            new AbstractMap.SimpleEntry<>("#id", this.keyAttr),
                            new AbstractMap.SimpleEntry<>("#expiry", this.expiryAttr)
                    )
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            var expressionAttributeValues = Stream.of(
                            new AbstractMap.SimpleEntry<>(":now", AttributeValue.fromN(String.valueOf(now.getEpochSecond())))
                    )
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            dynamoDbClient.putItem(
                    PutItemRequest.builder()
                            .tableName(tableName)
                            .item(item)
                            .conditionExpression("attribute_not_exists(#id) OR #expiry < :now")
                            .expressionAttributeNames(expressionAttributeNames)
                            .expressionAttributeValues(expressionAttributeValues)
                            .build()
            );
        } catch (ConditionalCheckFailedException e) {
            log.debug("Failed to put record for already existing open circuit breaker key: {}", statusRecord.circuitBreakerId());
        }
    }

    @Override
    void updateRecord(CircuitBreakerStatusRecord statusRecord) {
        log.debug("Updating record for circuit breaker key: {}", statusRecord.circuitBreakerId());

        var updateExpression = "SET #expiry = :expiry, #status = :status, #cause = :cause";

        var expressionAttributeNames = Stream.of(
                        new AbstractMap.SimpleEntry<>("#expiry", this.expiryAttr),
                        new AbstractMap.SimpleEntry<>("#status", this.statusAttr),
                        new AbstractMap.SimpleEntry<>("#cause", this.causeAttr)
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var expressionAttributeValues = Stream.of(
                        new AbstractMap.SimpleEntry<>(":expiry", AttributeValue.fromN(String.valueOf(statusRecord.expiryTimestamp()))),
                        new AbstractMap.SimpleEntry<>(":status", AttributeValue.fromS(statusRecord.status().toString())),
                        new AbstractMap.SimpleEntry<>(":cause", AttributeValue.fromS(statusRecord.cause()))
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(createKey(statusRecord.circuitBreakerId()))
                .updateExpression(updateExpression)
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build()
        );
    }

    @Override
    Optional<CircuitBreakerStatusRecord> fetchRecord(String circuitBreakerId) {
        GetItemResponse response = dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(createKey(circuitBreakerId))
                        .consistentRead(true)
                        .build()
        );

        if (!response.hasItem()) {
            return Optional.empty();
        }

        return Optional.ofNullable(itemToRecord(response.item()));
    }

    /**
     * Get the key to use for requests (depending on if we have a sort key or not)
     *
     * @param circuitBreakerKey circuit breaker key
     * @return AttributeValue map containing the key
     */
    private Map<String, AttributeValue> createKey(String circuitBreakerKey) {
        Map<String, AttributeValue> key = new HashMap<>();
        if (this.sortKeyAttr != null) {
            key.put(this.keyAttr, AttributeValue.builder().s(this.staticPkValue).build());
            key.put(this.sortKeyAttr, AttributeValue.builder().s(circuitBreakerKey).build());
        } else {
            key.put(this.keyAttr, AttributeValue.builder().s(circuitBreakerKey).build());
        }
        return key;
    }

    /**
     * Translate raw item records from DynamoDB to DataRecord
     *
     * @param item Item from dynamodb response
     * @return DataRecord instance
     */
    private CircuitBreakerStatusRecord itemToRecord(Map<String, AttributeValue> item) {
        var circuitBreakerId = item.get(sortKeyAttr != null ? sortKeyAttr : keyAttr).s();
        if (circuitBreakerId == null) {
            return null;
        }
        return new CircuitBreakerStatusRecord(
                circuitBreakerId,
                PersistenceStore.Status.valueOf(item.get(this.statusAttr).s()),
                Long.parseLong(item.get(this.expiryAttr).n()),
                item.get(this.causeAttr) != null ? item.get(this.causeAttr).s() : null
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Use this builder to get an instance of {@link DynamoDBPersistenceStore}.<br/>
     * With this builder you can configure the characteristics of the DynamoDB Table
     * (name, key, sort key, and other field names).<br/>
     * You can also set a custom {@link DynamoDbClient} for further tuning.
     */
    public static class Builder {
        public static final String LAMBDA_FUNCTION_NAME_ENV = "AWS_LAMBDA_FUNCTION_NAME";

        private static final String funcEnv = System.getenv(LAMBDA_FUNCTION_NAME_ENV);

        private String tableName;
        private String keyAttr = "id";
        private String staticPkValue = String.format("circuitBreaker#%s", funcEnv != null ? funcEnv : "");
        private String sortKeyAttr;
        private String expiryAttr = "expiration";
        private String statusAttr = "status";
        private String causeAttr = "cause";
        private DynamoDbClient dynamoDbClient;

        /**
         * Initialize and return a new instance of {@link DynamoDBPersistenceStore}.<br/>
         * Example:<br>
         * <pre>
         *     DynamoDBPersistenceStore.builder().withTableName("circuit_breaker_store").build();
         * </pre>
         *
         * @return an instance of the {@link DynamoDBPersistenceStore}
         */
        public DynamoDBPersistenceStore build() {
            if (StringUtils.isEmpty(tableName)) {
                throw new IllegalArgumentException("Table name is not specified");
            }
            return new DynamoDBPersistenceStore(
                    tableName,
                    keyAttr,
                    staticPkValue,
                    sortKeyAttr,
                    expiryAttr,
                    statusAttr,
                    causeAttr,
                    dynamoDbClient);
        }

        /**
         * Name of the table to use for storing execution records (mandatory)
         *
         * @param tableName Name of the DynamoDB table
         * @return the builder instance (to chain operations)
         */
        public Builder withTableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * DynamoDB attribute name for partition key (optional), by default "id"
         *
         * @param keyAttr name of the key attribute in the table
         * @return the builder instance (to chain operations)
         */
        public Builder withKeyAttr(String keyAttr) {
            this.keyAttr = keyAttr;
            return this;
        }

        /**
         * DynamoDB attribute value for partition key (optional), by default "circuitBreaker#[function-name]".
         * This will be used if the {@link #sortKeyAttr} is set.
         *
         * @param staticPkValue name of the partition key attribute in the table
         * @return the builder instance (to chain operations)
         */
        public Builder withStaticPkValue(String staticPkValue) {
            this.staticPkValue = staticPkValue;
            return this;
        }

        /**
         * DynamoDB attribute name for the sort key (optional)
         *
         * @param sortKeyAttr name of the sort key attribute in the table
         * @return the builder instance (to chain operations)
         */
        public Builder withSortKeyAttr(String sortKeyAttr) {
            this.sortKeyAttr = sortKeyAttr;
            return this;
        }

        /**
         * DynamoDB attribute name for expiry timestamp (optional), by default "expiration"
         *
         * @param expiryAttr name of the expiry attribute in the table
         * @return the builder instance (to chain operations)
         */
        public Builder withExpiryAttr(String expiryAttr) {
            this.expiryAttr = expiryAttr;
            return this;
        }

        /**
         * DynamoDB attribute name for status (optional), by default "status"
         *
         * @param statusAttr name of the status attribute in the table
         * @return the builder instance (to chain operations)
         */
        public Builder withStatusAttr(String statusAttr) {
            this.statusAttr = statusAttr;
            return this;
        }

        /**
         * DynamoDB attribute name for cause (optional), by default "cause"
         *
         * @param causeAttr name of the status attribute in the table
         * @return the builder instance (to chain operations)
         */
        public Builder withCauseAttr(String causeAttr) {
            this.causeAttr = causeAttr;
            return this;
        }

        /**
         * Custom {@link DynamoDbClient} used to query DynamoDB (optional).<br/>
         * The default one uses {@link UrlConnectionHttpClient} as a http client and
         * add com.amazonaws.xray.interceptors.TracingInterceptor (X-Ray) if available in the classpath.
         *
         * @param dynamoDbClient the {@link DynamoDbClient} instance to use
         * @return the builder instance (to chain operations)
         */
        public Builder withDynamoDbClient(DynamoDbClient dynamoDbClient) {
            this.dynamoDbClient = dynamoDbClient;
            return this;
        }
    }
}
