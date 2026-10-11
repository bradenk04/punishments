plugins {
  java
  id("com.gradleup.shadow")
  id("com.diffplug.spotless")
}
repositories {
  mavenCentral()
  maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
sourceSets.main {
  java.srcDir("../src/main/java")
  java.include("com/bradenkennedy/punishment/velocity/**", "com/bradenkennedy/punishment/api/model/**",
    "com/bradenkennedy/punishment/storage/PunishmentRepository.java",
    "com/bradenkennedy/punishment/storage/JdbcPunishmentRepository.java",
    "com/bradenkennedy/punishment/storage/model/NetworkChange.java", "com/bradenkennedy/punishment/network/**")
}
dependencies {
  compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
  annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
  compileOnly("org.jetbrains:annotations:26.0.2")
  runtimeOnly("com.mysql:mysql-connector-j:9.4.0")
  runtimeOnly("org.postgresql:postgresql:42.7.7")
}
tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
}
spotless { java { target("src/**/*.java"); palantirJavaFormat(); removeUnusedImports(); trimTrailingWhitespace(); endWithNewline() } }
tasks.shadowJar {
  archiveBaseName.set("punishments-velocity")
  filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
  mergeServiceFiles()
  relocate("com.mysql", "com.bradenkennedy.punishment.libs.mysql")
  relocate("org.postgresql", "com.bradenkennedy.punishment.libs.postgresql")
}
tasks.assemble { dependsOn(tasks.shadowJar) }
