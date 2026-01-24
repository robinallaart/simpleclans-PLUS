plugins {
    `java`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "simpleclans.simpleclans"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("org.jetbrains:annotations:24.1.0")
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
    }

    jar {
        archiveBaseName.set("SimpleclansPlugin")
        archiveVersion.set(version.toString())
    }
}
