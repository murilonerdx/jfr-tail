plugins {
    java
    id("org.springframework.boot") version "3.3.0"
}

group = "io.jfrtail"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.1"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":jfr-tail-spring-starter")) // Use Spring Boot Starter
}

tasks.test {
    useJUnitPlatform()
}
