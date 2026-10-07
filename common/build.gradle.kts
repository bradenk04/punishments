plugins { `java-library` }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
sourceSets.main {
  java.setSrcDirs(listOf("../src/main/java"))
  java.exclude("com/bradenkennedy/punishment/storage/PunishmentRepository.java")
  java.include("com/bradenkennedy/punishment/storage/**", "com/bradenkennedy/punishment/migration/**", "com/bradenkennedy/punishment/network/**")
}
dependencies {
  api(project(":api"))
  api("com.j256.ormlite:ormlite-jdbc:6.1")
  implementation("com.h2database:h2:2.5.252")
  implementation("com.google.code.gson:gson:2.13.1")
  implementation("org.yaml:snakeyaml:2.4")
  runtimeOnly("com.mysql:mysql-connector-j:9.4.0")
  runtimeOnly("org.postgresql:postgresql:42.7.7")
  runtimeOnly("org.hsqldb:hsqldb:2.7.4")
  compileOnly("org.jetbrains:annotations:26.0.2")
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}
tasks.test { useJUnitPlatform() }

