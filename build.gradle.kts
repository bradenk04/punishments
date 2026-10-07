plugins { base }
allprojects { group = "com.bradenkennedy"; version = "1.0.0" }
tasks.named("clean") { dependsOn(":api:clean", ":common:clean", ":paper:clean", ":velocity:clean") }
tasks.register("test") { dependsOn(":common:test", ":paper:test") }
tasks.register<Copy>("shadowJar") {
    dependsOn(":paper:shadowJar")
    from("paper/build/libs") { include("*-all.jar") }
    into(layout.buildDirectory.dir("libs"))
}
tasks.named("build") { dependsOn("test", "shadowJar", ":velocity:shadowJar") }
tasks.register("runServer") { dependsOn(":paper:runServer") }
