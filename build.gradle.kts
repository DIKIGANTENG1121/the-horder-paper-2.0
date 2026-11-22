plugins {
    java
}

group = "net.neon.hordes"
version = "1.0.0-SNAPSHOT"

allprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.255-stable")
}

tasks.jar {
    archiveBaseName.set("HordesPaper")
}
