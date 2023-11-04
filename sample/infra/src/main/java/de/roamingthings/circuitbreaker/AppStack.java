package de.roamingthings.circuitbreaker;

import io.micronaut.aws.cdk.function.MicronautFunction;
import io.micronaut.aws.cdk.function.MicronautFunctionFile;
import io.micronaut.starter.application.ApplicationType;
import io.micronaut.starter.options.BuildTool;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.IFunction;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.CfnFunction;
import software.amazon.awscdk.services.lambda.SnapStartConf;
import software.amazon.awscdk.services.stepfunctions.DefinitionBody;
import software.amazon.awscdk.services.stepfunctions.Pass;
import software.amazon.awscdk.services.stepfunctions.RetryProps;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.Succeed;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.constructs.IConstruct;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static software.amazon.awscdk.RemovalPolicy.DESTROY;
import static software.amazon.awscdk.services.dynamodb.BillingMode.PAY_PER_REQUEST;

public class AppStack extends Stack {

    public AppStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public AppStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        var circuitBreakerTable = Table.Builder.create(this, "CircuitBreakerTable")
                .tableName("circuit-breaker")
                .partitionKey(Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .timeToLiveAttribute("expiration")
                .billingMode(PAY_PER_REQUEST)
                .removalPolicy(DESTROY)
                .build();

        var environmentVariables = Map.of(
                "CIRCUIT_BREAKER_TABLE", circuitBreakerTable.getTableName()
        );
        Function function = MicronautFunction.create(ApplicationType.DEFAULT,
                        false,
                        this,
                        "SampleFunction")
                .runtime(Runtime.JAVA_17)
                .handler("de.roamingthings.circuitbreaker.SampleHandler")
                .environment(environmentVariables)
                .code(Code.fromAsset(functionPath()))
                .timeout(Duration.seconds(10))
                .memorySize(2048)
                .logRetention(RetentionDays.ONE_DAY)
                .tracing(Tracing.DISABLED)
                .architecture(Architecture.X86_64)
                .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
                .build();

        circuitBreakerTable.grantReadWriteData(function);

        var alias = Alias.Builder.create(this, "SampleFunctionAlias")
                .aliasName("current")
                .version(function.getCurrentVersion())
                .build();

        createStateMachine(alias);
    }

    private void createStateMachine(IFunction sampleFunction) {
        var addTime = Pass.Builder.create(this, "AddTime")
                .parameters(Map.of(
                        "value.$", "$.value",
                        "startTime.$", "$$.Execution.StartTime"
                ))
                .build();
        var invokeSafeguardedFunction = LambdaInvoke.Builder.create(this, "InvokeSafeguardedFunction")
                .lambdaFunction(sampleFunction)
                .retryOnServiceExceptions(true)
                .build();

        var chain =
                addTime.next(
                        invokeSafeguardedFunction
                                .addRetry(RetryProps.builder()
                                        .interval(Duration.seconds(15))
                                        .backoffRate(2)
                                        .maxAttempts(3)
                                        .errors(List.of(
                                                "de.roamingthings.lambda.circuitbreaker.exceptions.CircuitBreakerOpenException",
                                                "de.roamingthings.lambda.circuitbreaker.internal.CircuitBreakerTrippedException"
                                        ))
                                        .build())
                );

        StateMachine.Builder.create(this, "CircuitBreakerSampleStateMachine")
                .definitionBody(DefinitionBody.fromChainable(chain))
                .build();
    }

    public static String functionPath() {
        return "../app/build/libs/" + functionFilename();
    }

    public static String functionFilename() {
        return MicronautFunctionFile.builder()
                .optimized()
                .graalVMNative(false)
                .version("0.1")
                .archiveBaseName("app")
                .buildTool(BuildTool.GRADLE)
                .build();
    }
}
