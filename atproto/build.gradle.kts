plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
}

tasks.test {
    systemProperty("pano.test.classpath", sourceSets.test.get().runtimeClasspath.asPath)
    systemProperty("pano.test.directory", layout.buildDirectory.dir("test-vaults").get().asFile.absolutePath)
}
