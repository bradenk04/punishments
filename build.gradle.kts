plugins {
  java
  id("com.gradleup.shadow") version "9.6.1"
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

  implementation("net.kyori:adventure-api:4.26.1")
  implementation("net.kyori:adventure-text-minimessage:4.26.1")
  implementation("net.kyori:adventure-platform-bukkit:4.4.1")
  implementation("com.h2database:h2:2.5.252")
  implementation("com.j256.ormlite:ormlite-jdbc:6.1")
  implementation("org.incendo:cloud-paper:2.0.1")

  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
}

tasks.test {
  useJUnitPlatform()
}

tasks.shadowJar {
  relocate("com.h2database", "com.bradenkennedy.punishment.libs.h2")
  relocate("net.kyori", "com.bradenkennedy.punishment.libs.kyori")
  relocate("org.incendo.cloud", "com.bradenkennedy.punishment.libs.cloud")
}

tasks.jar {
  archiveClassifier.set("")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from({
    configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
  })
}
