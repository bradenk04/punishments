plugins {
  java
  id("com.gradleup.shadow") version "9.6.1"
  id("xyz.jpenilla.run-paper") version "3.1.0"
}
repositories {
  mavenCentral()
  maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
sourceSets {
  main {
    java.setSrcDirs(listOf("../src/main/java"))
    java.exclude("com/bradenkennedy/punishment/api/model/**", "com/bradenkennedy/punishment/storage/**", "com/bradenkennedy/punishment/migration/**", "com/bradenkennedy/punishment/network/**")
    resources.setSrcDirs(listOf("../src/main/resources"))
  }
  test { java.setSrcDirs(listOf("../src/test/java")); resources.setSrcDirs(listOf("../src/test/resources")) }
}
dependencies {
  implementation(project(":common"))
  compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
  implementation("net.kyori:adventure-api:4.26.1")
  implementation("net.kyori:adventure-text-minimessage:4.26.1")
  implementation("net.kyori:adventure-platform-bukkit:4.4.1")
  implementation("org.incendo:cloud-paper:2.0.1")
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
tasks.test { useJUnitPlatform() }
tasks.shadowJar {
  archiveBaseName.set("punishments")
  filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
  mergeServiceFiles()
  relocate("org.h2", "com.bradenkennedy.punishment.libs.h2")
  relocate("com.j256.ormlite", "com.bradenkennedy.punishment.libs.ormlite")
  relocate("com.google.gson", "com.bradenkennedy.punishment.libs.gson")
  relocate("org.yaml.snakeyaml", "com.bradenkennedy.punishment.libs.yaml")
  relocate("net.kyori", "com.bradenkennedy.punishment.libs.kyori")
  relocate("org.incendo.cloud", "com.bradenkennedy.punishment.libs.cloud")
}
tasks.runServer {
  minecraftVersion("26.2")
  javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
  pluginJars.setFrom(tasks.shadowJar.flatMap { it.archiveFile })
}
