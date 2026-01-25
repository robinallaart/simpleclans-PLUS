plugins {
    `java`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "simpleclans.simpleclans"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("org.jetbrains:annotations:24.1.0")

    implementation("org.json:json:20231013")
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("--enable-preview")
    }

    withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
    }

    withType<JavaExec> {
        jvmArgs("--enable-preview")
    }

    jar {
        archiveBaseName.set("SimpleclansPlugin")
        archiveVersion.set(version.toString())
    }
    shadowJar {
        archiveBaseName.set("SimpleclansPlugin")
        archiveVersion.set(version.toString())
        relocate("org.json", "simpleclans.libs.org.json")
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }
}
