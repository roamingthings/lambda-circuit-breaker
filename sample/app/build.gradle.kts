plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.minimal.library") version "4.0.4"
    id("io.micronaut.aot") version "4.0.4"
    id("io.freefair.aspectj.post-compile-weaving") version "8.4"
}

version = "0.1"
group = "de.roamingthings"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    implementation("org.aspectj:aspectjrt:1.9.20.1")
    aspect("de.roamingthings:lambda-circuit-breaker:local")

    implementation("io.micronaut.aws:micronaut-function-aws")
    implementation("io.micronaut.aws:micronaut-aws-sdk-v2")
    implementation("io.micronaut.aws:micronaut-aws-lambda-events-serde")
    implementation("io.micronaut.crac:micronaut-crac")
    implementation("io.micronaut.serde:micronaut-serde-jackson")

    implementation("software.amazon.awssdk:dynamodb:2.21.11")

    compileOnly("io.micronaut:micronaut-http-client")

    runtimeOnly("ch.qos.logback:logback-classic")

    testImplementation("io.micronaut:micronaut-http-client")
}

configurations.all {
    exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    exclude(group = "software.amazon.awssdk", module = "apache-client")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

micronaut {
    runtime("lambda_java")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("de.roamingthings.*")
    }
    aot {
        optimizeServiceLoading.set(false)
        convertYamlToJava.set(false)
        precomputeOperations.set(true)
        cacheEnvironment.set(true)
        optimizeClassLoading.set(true)
        deduceEnvironment.set(true)
        optimizeNetty.set(true)
    }
}
