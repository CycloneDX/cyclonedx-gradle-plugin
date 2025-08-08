import org.cyclonedx.model.*
import java.util.HashMap

plugins {
    java
    id("org.cyclonedx.bom")
}

repositories {
    mavenLocal()
    mavenCentral()
}

group = "com.example"
version = "1.0.0"

dependencies {
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.8.11")
    implementation("org.springframework.boot:spring-boot-starter-web:1.5.18.RELEASE")
}


cyclonedxBom {
    organizationalEntity.set(OrganizationalEntity().apply {
        name = "Test"
        urls = listOf("www.test.com")
        contacts = listOf(OrganizationalContact().apply {
            name = "Max_Mustermann"
            email = "max.mustermann@test.org"
            phone = "0000 99999999"
        })
    })
    licenseChoice.set(LicenseChoice().apply {
            addLicense(License().apply {
                name = "XXXX XXXX Software"
                url = "https://www.test-Url.org/"
                setLicenseText(AttachmentText().apply { text = "This is a Licenses-Test" })
            })
        }
    )
}
