plugins {
  java
  jacoco
  id("com.diffplug.spotless") version "8.10.3"
  id("com.gradleup.shadow") version "9.6.1"
  id("xyz.jpenilla.run-paper") version "3.1.0"
  id("com.modrinth.minotaur") version "2.10.0"
  id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

val pluginVersion = project.version.toString()
val releaseNotes = providers.fileContents(layout.buildDirectory.file("release-notes.md")).asText.orElse("")
val preRelease = pluginVersion.contains("-")
val gameVersion = "26.2"

repositories {
  mavenCentral()
  maven {
    url = uri("https://jitpack.io")
    content { includeGroup("com.github.MilkBowl") }
  }
  maven {
    url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
  }
  maven {
    url = uri("https://repo.papermc.io/repository/maven-public/")
  }
  maven {
    url = uri("https://libraries.minecraft.net")
  }
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
  }
}

val legacyH2: Configuration by configurations.creating

dependencies {
  legacyH2("com.h2database:h2:1.4.199")
  compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
  compileOnly("net.luckperms:api:5.5")
  compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

  compileOnly("com.mojang:brigadier:1.0.18")
  compileOnly("org.hsqldb:hsqldb:2.7.4")

  implementation("net.kyori:adventure-api:4.26.1")
  implementation("net.kyori:adventure-text-minimessage:4.26.1")
  implementation("net.kyori:adventure-platform-bukkit:4.4.1")
  implementation("com.h2database:h2:2.5.252")
  implementation("com.j256.ormlite:ormlite-jdbc:6.1")
  implementation("org.incendo:cloud-paper:2.0.1")
  implementation("org.bstats:bstats-bukkit:3.2.1")

  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  testImplementation("org.hsqldb:hsqldb:2.7.4")
  testImplementation("io.papermc.paper:paper-api:26.2.build.132-stable")
  testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.117.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.processResources {
  val tokens = mapOf("version" to pluginVersion)
  inputs.properties(tokens)
  filesMatching("plugin.yml") { expand(tokens) }
}

tasks.test {
  useJUnitPlatform()
  jvmArgumentProviders.add(LegacyH2Jar(legacyH2.incoming.files))
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  reports.xml.required.set(true)
}

spotless {
  java {
    palantirJavaFormat()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

tasks.runServer {
  minecraftVersion("26.2")
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
  })
  jvmArgs("-Dcom.mojang.eula.agree=true")
  pluginJars.setFrom(tasks.shadowJar.flatMap { it.archiveFile })
}

tasks.runServer {
  minecraftVersion("26.2")
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
  })
  jvmArgs("-Dcom.mojang.eula.agree=true")
  pluginJars.setFrom(tasks.shadowJar.flatMap { it.archiveFile })
}

tasks.shadowJar {
  filesMatching("META-INF/services/**") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }
  mergeServiceFiles()
  relocate("com.j256.ormlite", "com.lotusplugins.warrant.libs.ormlite")
  relocate("org.h2", "com.lotusplugins.warrant.libs.h2")
  relocate("net.kyori", "com.lotusplugins.warrant.libs.kyori")
  relocate("org.incendo.cloud", "com.lotusplugins.warrant.libs.cloud")
  relocate("org.bstats", "com.lotusplugins.warrant.libs.bstats")
}

modrinth {
  token.set(providers.environmentVariable("MODRINTH_TOKEN"))
  projectId.set(providers.environmentVariable("MODRINTH_PROJECT_ID"))
  versionNumber.set(pluginVersion)
  versionType.set(if (preRelease) "beta" else "release")
  uploadFile.set(tasks.shadowJar)
  gameVersions.add(gameVersion)
  loaders.addAll("spigot", "paper")
  changelog.set(releaseNotes)
  debugMode.set(providers.environmentVariable("MODRINTH_DEBUG").isPresent)
}

hangarPublish {
  publications.register("plugin") {
    version.set(pluginVersion)
    id.set(providers.environmentVariable("HANGAR_PROJECT_ID"))
    channel.set(if (preRelease) "Snapshot" else "Release")
    changelog.set(releaseNotes)
    apiKey.set(providers.environmentVariable("HANGAR_API_TOKEN"))
    platforms {
      paper {
        jar.set(tasks.shadowJar.flatMap { it.archiveFile })
        platformVersions.set(listOf(gameVersion))
      }
    }
  }
}

val verifyShadowJar = tasks.register<JavaExec>("verifyShadowJar") {
  dependsOn(tasks.shadowJar)
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
  })
  classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
  mainClass.set(file("src/shadowTest/java/VerifyShadowJar.java").absolutePath)
  args(tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
}

tasks.check {
  dependsOn(verifyShadowJar)
}

class LegacyH2Jar(@get:InputFiles @get:PathSensitive(PathSensitivity.NONE) val jar: FileCollection) :
    CommandLineArgumentProvider {
  override fun asArguments() = listOf("-DlegacyH2.jar=${jar.singleFile}")
}
