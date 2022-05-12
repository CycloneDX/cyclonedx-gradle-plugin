plugins {
    java
    id("org.cyclonedx.bom") version "1.5.1"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.8.11")
    implementation("org.springframework.boot:spring-boot-starter-web:1.5.18.RELEASE")
}

tasks.cyclonedxBom {
    setIncludeBomSerialNumber(false)
}