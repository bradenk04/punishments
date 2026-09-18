plugins {
  java
  id("com.gradleup.shadow") version "9.6.1"
  id("xyz.jpenilla.run-paper") version "3.1.0"
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
  implementation("com.h2database:h2:2.5.250")
  implementation("com.j256.ormlite:ormlite-jdbc:6.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
}

tasks.shadowJar {
  relocate("com.h2database", "com.bradenkennedy.punishment.libs.h2")
  relocate("net.kyori", "com.bradenkennedy.punishment.libs.kyori") 
}

tasks.jar {
  archiveClassifier.set("")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from({
    configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
  })
}

tasks.runServer {
    minecraftVersion("26.2")
  }
