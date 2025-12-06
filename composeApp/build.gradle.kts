import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localBuildNumber = 65535

class BuildInfo {
    val majorVersion: Int = (project.findProperty(BuildInfo::majorVersion.name) as? String)?.toInt() ?: 1
    val minorVersion: Int = (project.findProperty(BuildInfo::minorVersion.name) as? String)?.toInt() ?: 0
    val buildNumber: Int = (project.findProperty(BuildInfo::buildNumber.name) as? String)?.toInt() ?: localBuildNumber
    val buildDateTime: Instant =
        (project.findProperty(BuildInfo::buildDateTime.name) as? String)?.let { Instant.parse(it) } ?: Instant.now()
    val buildHash: String = project.findProperty(BuildInfo::buildHash.name) as? String ?: ""
}

val buildInfo = BuildInfo()

val generateBuildConstants by tasks.registering {
    val outputDir = layout.projectDirectory.dir("src/commonMain/kotlin/org/dots/game")
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.asFile.resolve("BuildInfo.gen.kt")
        file.parentFile.mkdirs()

        file.writeText("""// The file is generated. DO NOT MODIFY MANUALLY

package org.dots.game

import kotlin.time.Instant

@Suppress("ConstPropertyName")
data object BuildInfo {
    const val ${BuildInfo::majorVersion.name}: Int = ${buildInfo.majorVersion}
    const val ${BuildInfo::minorVersion.name}: Int = ${buildInfo.minorVersion}
    const val ${BuildInfo::buildNumber.name}: Int = ${buildInfo.buildNumber}
    val ${BuildInfo::buildDateTime.name}: Instant = Instant.parse("${DateTimeFormatter.ISO_INSTANT.format(buildInfo.buildDateTime)}")
    const val ${BuildInfo::buildHash.name}: String = "${buildInfo.buildHash}"
}""")
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Werror")
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop") {
        compilerOptions {
            freeCompilerArgs.add("-Werror")
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
        }
        compilerOptions {
            freeCompilerArgs.add("-Werror")
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting

        val desktopTest by getting

        val wasmJsMain by getting

        commonMain.dependencies {
            implementation(project(":library"))
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.jetbrains.material)
            implementation(libs.ui)
            implementation(libs.components.resources)
            implementation(libs.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        wasmJsMain.dependencies {
            implementation(npm("pako", "2.1.0"))
        }
    }
    compilerOptions {
        // Do not enforce -Werror globally to avoid failing iOS/native metadata compilation
        // Retain other useful checks across all source sets
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes",
            "-Xreturn-value-checker=full",
            "-Xname-based-destructuring=complete",
            "-Xexplicit-backing-fields",
            "-Xcontext-sensitive-resolution",
        )
    }
}

tasks.named<Test>("desktopTest") {
    useJUnitPlatform() // Ensure JUnit 5 tests run for desktop testing
}

android {
    namespace = "org.dots.game"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.dots.game"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.ui.tooling)
}

compose.desktop {
    application {
        mainClass = "org.dots.game.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "org.dots.game"
            packageVersion = "${buildInfo.majorVersion}.${buildInfo.minorVersion}.${buildInfo.buildNumber}"
            vendor = "Dots Game Org"

            windows {
                perUserInstall = true
                menu = true
                copyright = "© 2025 KvanTTT (Ivan Kochurkin)"
            }

            linux {
                menuGroup = "Game"
            }
        }
    }
}

val localProperties = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val kataGoDotsEngineKey = "KataGoDotsEngine"
val kataGoDotsModelKey = "KataGoDotsModel"
val kataGoDotsConfigKey = "KataGoDotsConfig"

tasks.withType<Test> {
    localProperties.getProperty(kataGoDotsEngineKey)?.let { environment(kataGoDotsEngineKey, it) }
    localProperties.getProperty(kataGoDotsModelKey)?.let { environment(kataGoDotsModelKey, it) }
    localProperties.getProperty(kataGoDotsConfigKey)?.let { environment(kataGoDotsConfigKey, it) }
}