import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.6.1"
    `maven-publish`
}

group = "jp.awabi2048"
version = "26.928.2"

repositories {
    mavenLocal()
    maven { url = uri("../.m2-paper26-kotlin2320") }
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.lucko.me/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io")
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

// src/main/kotlin 配下のJavaファイルもコンパイル対象に含める（kotlin-maven-plugin同等）
sourceSets {
    named("main") {
        java.srcDir("src/main/kotlin")
    }
    named("test") {
        java.srcDir("src/test/kotlin")
    }
}

// pom.xml の provided スコープ相当（compileOnly へ配置し、テストのコンパイル・実行双方で見えるよう testImplementation にも追加）
val providedDeps = listOf(
    "io.papermc.paper:paper-api:26.1.2.build.72-stable",
    "org.jetbrains.kotlin:kotlin-stdlib:2.3.20",
    "com.awabi2048:CC-System:26.926.1",
    "awabi2048:my-world-manager:26.814.10",
    "me.crylonz.deadchest:dead-chest:4.30.0",
    "net.milkbowl.vault:VaultAPI:1.7.3-b131",
    "net.luckperms:api:5.4",
    "com.github.ucchyocean:LunaChat:3.0.16",
    "com.sk89q.worldedit:worldedit-bukkit:7.3.16",
)

dependencies {
    providedDeps.forEach {
        compileOnly(it)
        testImplementation(it)
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

// pom.xml の resources filtering 相当（${project.version} を展開）
tasks.processResources {
    val versionString = project.version.toString()
    inputs.property("projectVersion", versionString)
    filesMatching("plugin.yml") {
        expand(mapOf("project" to mapOf("version" to versionString)))
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // pom.xml の shade フィルタ相当（依存JAR含め MANIFEST.MF を除外）
    exclude("META-INF/MANIFEST.MF")
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifactId = "CC-Content"
            from(components["shadow"])
        }
    }
    repositories {
        maven {
            name = "workspace"
            url = uri("../.m2-paper26-kotlin2320")
        }
    }
}
