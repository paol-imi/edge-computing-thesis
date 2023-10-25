plugins {
    // "java-library" allow us to use the "api" keyword
    id("java-library")
}

group = "com.edge-computing-thesis"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    api("io.lettuce:lettuce-core:6.2.3.RELEASE")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.openfaas:model:2.1.1")
    implementation("joda-time:joda-time:2.12.5")
    implementation("org.slf4j:slf4j-api:2.0.9")

    // slf4j java.util.logging implementation for testing
    testImplementation("org.slf4j:slf4j-jdk14:2.0.9")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

tasks.test {
    useJUnitPlatform()
}