import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localBuildNumber = 65535

val majorVersion = (project.findProperty("majorVersion") as? String)?.toInt() ?: 1
val minorVersion = (project.findProperty("minorVersion") as? String)?.toInt() ?: 0
val buildNumber = (project.findProperty("buildNumber") as? String)?.toInt() ?: localBuildNumber

val generateBuildConstants by tasks.registering {
    val outputDir = layout.projectDirectory.dir("src/commonMain/composeResources/files")
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.asFile.resolve("build_info")
        file.parentFile.mkdirs()
        val buildDateTime = project.findProperty("buildDateTime") as? String ?: ""
        val buildCommit = project.findProperty("buildHash") as? String ?: ""
        file.writeText("$majorVersion,$minorVersion,$buildNumber,$buildDateTime,$buildCommit")
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
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

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    // Serve sources to debug inside browser
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting

        val desktopTest by getting

        commonMain.dependencies {
            implementation(project(":library"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-Xcontext-parameters",
                "-Xexpect-actual-classes",
            )
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
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.dots.game.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "org.dots.game"
            packageVersion = "$majorVersion.$minorVersion.$buildNumber"
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