plugins {
    application
}

dependencies {
    implementation(project(":agent"))
}

application {
    mainClass.set("io.jfrtail.sample.SampleApp")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.jfrtail.sample.SampleApp"
    }
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    // Fix for Gradle implicit dependency error
    dependsOn(configurations.runtimeClasspath)
    
    from(sourceSets.main.get().output)
    
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
