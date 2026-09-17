plugins {
  java
}

repositories {
  mavenCentral()
  maven {
    url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
  }
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
