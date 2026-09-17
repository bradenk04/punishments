plugins {
  java
}

repositories {
  mavenCentral()
  maven {
    url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
  }
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
  }
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")

  implementation("net.kyori:adventure-api:4.17.0")
  implementation("net.kyori:adventure-text-minimessage:4.17.0")
  implementation("net.kyori:adventure-platform-bukkit:4.3.4")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
}

tasks.jar {
  archiveClassifier.set("")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from({
    configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
  })
}
