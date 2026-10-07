plugins { `java-library`; `maven-publish` }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)); withSourcesJar() }
sourceSets.main { java.setSrcDirs(listOf("../src/main/java")); java.include("com/bradenkennedy/punishment/api/model/**", "com/bradenkennedy/punishment/storage/PunishmentRepository.java") }
dependencies { compileOnly("org.jetbrains:annotations:26.0.2") }
publishing { publications { create<MavenPublication>("api") { from(components["java"]) } } }

