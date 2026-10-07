plugins { java; id("com.gradleup.shadow") version "9.6.1" }
repositories { mavenCentral(); maven { url = uri("https://repo.papermc.io/repository/maven-public/") } }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
dependencies {
  implementation(project(":common"))
  compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
  annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}
tasks.shadowJar {
  archiveBaseName.set("punishments-velocity")
  filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
  mergeServiceFiles()
  relocate("org.h2", "com.bradenkennedy.punishment.libs.h2")
  relocate("com.j256.ormlite", "com.bradenkennedy.punishment.libs.ormlite")
  relocate("com.mysql", "com.bradenkennedy.punishment.libs.mysql")
  relocate("org.postgresql", "com.bradenkennedy.punishment.libs.postgresql")
  relocate("org.hsqldb", "com.bradenkennedy.punishment.libs.hsqldb")
  relocate("com.google.gson", "com.bradenkennedy.punishment.libs.gson")
  relocate("org.yaml.snakeyaml", "com.bradenkennedy.punishment.libs.yaml")
}

