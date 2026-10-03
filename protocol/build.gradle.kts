plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.wire)
    `java-test-fixtures`
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

wire {
    kotlin {}
}

dependencies {
    api(libs.wire.runtime)
    api(libs.coroutines.core)

    testFixturesApi(libs.coroutines.core)
    testFixturesApi(libs.wire.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
