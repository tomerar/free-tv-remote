import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

val keystoreProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

// Release signing: nothing configured = an unsigned local release APK (fine for development).
// Published releases set REQUIRE_RELEASE_SIGNING=true (or -PrequireReleaseSigning=true): then a missing
// or partial configuration fails the build at once instead of producing an unsigned or wrongly signed APK.
val releaseSigningSettings =
    linkedMapOf(
        "storeFile" to signingValue("storeFile", "SIGNING_STORE_FILE"),
        "storePassword" to signingValue("storePassword", "SIGNING_STORE_PASSWORD"),
        "keyAlias" to signingValue("keyAlias", "SIGNING_KEY_ALIAS"),
        "keyPassword" to signingValue("keyPassword", "SIGNING_KEY_PASSWORD"),
    )
val releaseSigningRequired =
    findProperty("requireReleaseSigning") == "true" || System.getenv("REQUIRE_RELEASE_SIGNING") == "true"

val verifyReleaseSigning =
    tasks.register("verifyReleaseSigning") {
        group = "verification"
        description = "Fails when release signing is required but not (fully) configured. Prints no secrets."
        val settings = releaseSigningSettings.toMap()
        val required = releaseSigningRequired
        val keystore = settings["storeFile"]?.let { rootProject.file(it) }
        doLast {
            val missing = settings.filterValues { it == null }.keys
            if (missing.isNotEmpty() && (required || missing.size < settings.size)) {
                throw GradleException(
                    "Release signing is not configured completely. Missing: ${missing.joinToString()}. " +
                        "Provide keystore.properties (storeFile, storePassword, keyAlias, keyPassword) or the " +
                        "SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD " +
                        "environment variables. See docs/RELEASING.md.",
                )
            }
            if (required && keystore?.isFile != true) {
                throw GradleException("Release signing is required but the keystore file was not found: ${settings["storeFile"]}")
            }
        }
    }

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }

// The commit a build was made from, shown in Settings > About so hardware test reports name an exact build.
// Falls back to "unknown" when git is unavailable (e.g. building from a source archive).
val gitCommit: String =
    runCatching {
        providers
            .exec {
                commandLine("git", "rev-parse", "--short=9", "HEAD")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
    }.getOrDefault("").ifEmpty { "unknown" }

android {
    namespace = "io.github.tomerar.freetvremote"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.tomerar.freetvremote"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "0.3.1"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStoreFile = signingValue("storeFile", "SIGNING_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en", "iw", "he")
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                // Robolectric reflects into JDK internals.
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.nio=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                )
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    dependenciesInfo {
        // F-Droid / reproducibility: no dependency metadata blob in the APK.
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(testFixtures(project(":protocol")))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

ktlint {
    version.set("1.8.0")
    android.set(false)
}
