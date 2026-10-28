plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    systemProperty("pano.test.classpath", sourceSets.test.get().runtimeClasspath.asPath)
    systemProperty("pano.test.directory", layout.buildDirectory.dir("test-vaults").get().asFile.absolutePath)
}
