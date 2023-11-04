package de.roamingthings.lambda.circuitbreaker.test;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;

public abstract class DynamoDBTestBase {

    protected static final String TABLE_NAME = "circuit_breaker_table";
    protected static DynamoDBProxyServer dynamoProxy;
    protected static DynamoDbClient client;

    @BeforeAll
    public static void setupDynamo() {
        var dynamoDBPort = discoverFreePort();
        try {
            dynamoProxy = ServerRunner.createServerFromCommandLineArgs(new String[] {
                    "-inMemory",
                    "-port",
                    Integer.toString(dynamoDBPort)
            });
            dynamoProxy.start();
        } catch (Exception e) {
            throw new RuntimeException();
        }

        client = DynamoDbClient.builder()
                .httpClient(UrlConnectionHttpClient.builder().build())
                .region(Region.EU_CENTRAL_1)
                .endpointOverride(URI.create("http://localhost:" + dynamoDBPort))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("DUMMY", "DUMMY")))
                .build();

        createCircuitBreakerTable();
        checkTableCreatedOrFail();
    }

    @AfterAll
    public static void teardownDynamo() {
        try {
            dynamoProxy.stop();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    private static int discoverFreePort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            int port = socket.getLocalPort();
            socket.close();
            return port;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createCircuitBreakerTable() {
        client.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .keySchema(KeySchemaElement.builder().keyType(KeyType.HASH).attributeName("id").build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    private static void checkTableCreatedOrFail() {
        DescribeTableResponse response =
                client.describeTable(DescribeTableRequest.builder().tableName(TABLE_NAME).build());
        if (response == null) {
            throw new RuntimeException("Table was not created within expected time");
        }
    }
}
