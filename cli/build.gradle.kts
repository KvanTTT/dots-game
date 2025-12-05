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
        freeCompilerArgs.addAll(
            "-Werror",
            "-Xreturn-value-checker=full",
        )
    }
}

application {
    mainClass.set("MainKt")
}
