plugins {
    id("java")
    application
}

group = "top.yzljc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("io.netty:netty-all:4.1.118.Final")

    // Optional official release JAR for environments where the Via Maven repo is unreachable.
    val viaJar = providers.gradleProperty("viaJar")
    if (viaJar.isPresent) implementation(files(viaJar.get()))
    else implementation("com.viaversion:viaversion-common:5.12.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.google.guava:guava:33.4.8-jre")

}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed", "skipped") }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

application {
    mainClass = "top.yzljc.limbo.LimboServer"
}

tasks.jar {
    manifest.attributes["Main-Class"] = application.mainClass.get()
}

distributions {
    main {
        contents {
            from("README.md", "limbo.properties", "THIRD_PARTY_NOTICES.md")
            from("licenses") { into("licenses") }
            from("worlds") { into("worlds") }
        }
    }
}
