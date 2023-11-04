plugins {
    `java-library`
    alias(libs.plugins.aspectj)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.aws.lambda.javaCore)
    implementation(libs.aws.sdk.dynamodb) {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation(libs.aws.sdk.urlConnectionClient)
    implementation(libs.aspectj.rt)
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.test.common)
    testImplementation(libs.aws.lambda.javaTest)

    testImplementation(libs.aws.dynamoDBLocal)

    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

configurations.all {
    exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    exclude(group = "software.amazon.awssdk", module = "apache-client")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
