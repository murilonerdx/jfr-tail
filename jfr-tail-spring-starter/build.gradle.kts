plugins {
    `java-library`
}

dependencies {
    api(project(":agent"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.1"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
}
