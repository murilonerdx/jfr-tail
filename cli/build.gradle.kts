plugins {
    java
}

dependencies {
    implementation(project(":common"))
    implementation("info.picocli:picocli:4.7.4")
    implementation("com.googlecode.lanterna:lanterna:3.1.1")
    implementation("io.javalin:javalin:6.1.3")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.jfrtail.cli.JfrTailCli"
    }
    
    // Create Fat JAR manually since Shadow plugin is failing
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // Fix for Gradle implicit dependency error
    dependsOn(configurations.runtimeClasspath)
    
    from(sourceSets.main.get().output)
    
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
