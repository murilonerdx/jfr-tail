import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("maven-publish")
}

group = "io.jfrtail"
version = "1.1.3"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    extensions.configure<org.gradle.api.publish.PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("JFR-Tail monitoring tool component: ${project.name}")
                    url.set("https://github.com/murilonerdx/jfr-tail")
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/murilonerdx/jfr-tail")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    if (project.name == "sample" || project.name == "spring-sample") {
        tasks.withType<GenerateMavenPom> { enabled = false }
        tasks.withType<PublishToMavenRepository> { enabled = false }
        tasks.withType<PublishToMavenLocal> { enabled = false }
    }
}
