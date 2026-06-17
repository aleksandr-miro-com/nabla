plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

group = "com.example.nabla"
version = "0.1.0"

dependencies {
    // Align versions of all Kotlin components.
    implementation(platform(libs.kotlin.bom))

    implementation(libs.spring.boot.starter.web)
    api(libs.collaboration.engine.api)
    implementation(libs.collaboration.engine.core)
    implementation(libs.collaboration.engine.transport)

//    implementation(libs.grpc.netty.shade)
//    implementation("io.grpc:grpc-inprocess")

    api(platform(libs.netty.bom))
    implementation("io.netty:netty-handler")
    implementation("io.netty:netty-codec-http")

    implementation(platform(libs.micrometer.bom))
    implementation("io.micrometer:micrometer-core")

    // Use the Kotlin standard library.
    implementation(libs.kotlin.stdlib)

    // Use the Kotlin JUnit 5 integration.
    testImplementation(libs.kotlin.test.junit5)

    // Use the JUnit 5 integration.
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
