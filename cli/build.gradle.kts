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
            "-Xname-based-destructuring=complete",
            "-Xexplicit-backing-fields",
            "-Xcontext-sensitive-resolution",
        )
    }
}

application {
    mainClass.set("MainKt")
}
