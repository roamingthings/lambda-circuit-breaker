plugins {
    id("application")
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("io.micronaut.platform:micronaut-platform:4.1.6"))
    implementation("io.micronaut.starter:micronaut-starter-aws-cdk:4.1.5") {
      exclude(group = "software.amazon.awscdk", module = "aws-cdk-lib")
    }
    implementation("software.amazon.awscdk:aws-cdk-lib:2.104.0")
    testImplementation(platform("io.micronaut.platform:micronaut-platform:4.1.6"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClass.set("de.roamingthings.circuitbreaker.Main")
}

tasks.withType<Test> {
    dependsOn(":app:optimizedJitJarAll")
    useJUnitPlatform()
}

tasks.named<DefaultTask>("run") {
    dependsOn(":app:optimizedJitJarAll")
}
