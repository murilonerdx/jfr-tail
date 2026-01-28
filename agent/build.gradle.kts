dependencies {
    implementation(project(":common"))
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "io.jfrtail.agent.JfrTailAgent",
            "Agent-Class" to "io.jfrtail.agent.JfrTailAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }

    // Create Fat JAR including 'common' and all dependencies
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // Fix for Gradle implicit dependency error
    dependsOn(configurations.runtimeClasspath)
    
    from(sourceSets.main.get().output)
    
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
