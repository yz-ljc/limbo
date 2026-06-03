plugins {
    id("java")
}

group = "top.yzljc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com")
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("io.netty:netty-all:4.1.118.Final")

    // Querz NBT library from JitPack (matches Minecraft's NBT binary format exactly)
    implementation("com.github.Querz:NBT:6.1")

    // JSON parsing for registry data files
    implementation("com.googlecode.json-simple:json-simple:1.1.1")

}

tasks.test {
    useJUnitPlatform()
}