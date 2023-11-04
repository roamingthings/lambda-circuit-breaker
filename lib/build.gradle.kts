plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.aspectj)
}

val mavenArtifactId = "lambda-circuit-breaker"
val repositoryName = "roamingthings/lambda-circuit-breaker"
val longName = "Circuit Breaker for AWS Lambda Functions"
val longDescription = "A circuit breaker implementation intended for AWS Lambda functions, especially AWS Lambda functions running in\n" +
        "a StepFunctions state machine.."

group = "de.roamingthings"

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
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor = JvmVendorSpec.AMAZON
    }
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${repositoryName}")
            credentials {
                username = "dummy"
                password = project.findProperty("gpr.key") as String? ?: (System.getenv("GITHUB_TOKEN"))
                        ?: System.getenv("GITHUB_PUBLISH_PACKAGES_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("library") {
            groupId = project.group.toString()
            artifactId = mavenArtifactId
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set(longName)
                description.set(longDescription)
                url.set("https://github.com/${repositoryName}")

                scm {
                    connection.set("https://github.com/${repositoryName}.git")
                    developerConnection.set("https://github.com/${repositoryName}.git")
                    url.set("https://github.com/${repositoryName}")
                }
            }
        }
    }
}
