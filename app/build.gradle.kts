plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("jacoco")
}

android {
    namespace = "com.example.enterprisedocumentredactor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.enterprisedocumentredactor"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            enableUnitTestCoverage = true   // JaCoCo instrumentation for unit tests
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    testOptions {
        unitTests {
            // Allow Android stubs to return defaults (0/null/false) instead of throwing.
            // Required so RectF, Log, etc. work in pure-JVM unit tests without Robolectric.
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

// ── JaCoCo ──────────────────────────────────────────────────────────────────

jacoco {
    toolVersion = "0.8.12"
}

val jacocoExcludes = listOf(
    // Android / Compose boilerplate
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    // Hilt generated
    "**/*Hilt*.*", "**/*_Factory*.*", "**/*_MembersInjector*.*",
    "**/Dagger*Component*.*", "**/di/**",
    // UI — Composables, Screens, Themes (tested via instrumented tests, not unit tests)
    "**/*Screen*.*", "**/*Activity*.*", "**/*Theme*.*",
    "**/*Color*.*", "**/*Type*.*", "**/*Component*.*",
    // Room generated
    "**/*_Impl*.*",
    // Pure data models / enums
    "**/model/**"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "JaCoCo unit-test coverage report — debug build."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val classesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    classDirectories.setFrom(fileTree(classesDir) { exclude(jacocoExcludes) })
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        }
    )
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "Fail the build if line coverage < 80% (HIPAA/GDPR compliance target)."
    dependsOn("jacocoTestReport")

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    val classesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    classDirectories.setFrom(fileTree(classesDir) { exclude(jacocoExcludes) })
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        }
    )
}

// ── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ML Kit
    implementation(libs.mlkit.entity.extraction)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // Coil
    implementation(libs.coil.compose)

    // Biometric
    implementation(libs.biometric)

    // Lifecycle Process (ProcessLifecycleOwner)
    implementation(libs.lifecycle.process)

    // ── Unit Tests ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.arch.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
