plugins {
    kotlin("jvm")
    application
}

group = "org.dots.game"
version = "unspecified"

dependencies {
    implementation(project(":library"))
    implementation(libs.clikt)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Werror")
    }
}

application {
    mainClass.set("MainKt")
}
