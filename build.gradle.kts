val kotlin_version: String by project
val logback_version: String by project
val vips_ffm_version: String by project

plugins {
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.2"
    id("me.qoomon.git-versioning") version "6.4.3"
}

group = "com.ashampoo.imageproxy"
version = "0.0.1"

gitVersioning.apply {

    refs {
        /* Release / tags have real version numbers */
        tag("v(?<version>.*)") {
            version = "\${ref.version}"
        }
    }
}

application {
    mainClass.set("com.ashampoo.imageproxy.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")

    implementation("io.ktor:ktor-client-okhttp")

    implementation("app.photofox.vips-ffm:vips-ffm-core:$vips_ffm_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")
}
