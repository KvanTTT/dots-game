import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "org.dots.game"
version = "unspecified"

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        d8()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Werror",
            "-Xreturn-value-checker=full",
            "-Xname-based-destructuring=complete",
            "-Xexplicit-backing-fields",
            "-Xcontext-sensitive-resolution",
            "-XXexplicit-return-types=strict",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}