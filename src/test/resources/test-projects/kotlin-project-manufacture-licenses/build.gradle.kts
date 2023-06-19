import org.cyclonedx.model.*
import java.util.HashMap

plugins {
    java
    id("org.cyclonedx.bom") version "1.7.1"
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


tasks.cyclonedxBom {

    //declaration of Manufacture-Data
    var organizationalEntity = OrganizationalEntity()

    organizationalEntity.setName("Test")
    organizationalEntity.setUrls(listOf("www.test1.com", "www.test2.com"))

    var organizationalContact = OrganizationalContact()
    organizationalContact.setName("Max_Mustermann")
    organizationalContact.setEmail("max.mustermann@test.org")
    organizationalContact.setPhone("0000 99999999")
    organizationalEntity.addContact(organizationalContact)

    setOrganizationalEntity(organizationalEntity)

    //declaration of Licenses-Data
    var attachmentText = AttachmentText();
    attachmentText.setText("This is a Licenses-Test");

    var license = License()
    //license.setId("Mup") // either id or name
    license.setName("XXXX XXXX Software")
    license.setLicenseText(attachmentText);
    license.setUrl("https://www.test-Url.org/")

    var licenseChoice = LicenseChoice()
    //licenseChoice.setExpression("This is a Test Expression") // either license or expression
    licenseChoice.addLicense(license)

    setLicenseChoice(licenseChoice)

    /*
        val organizationalEntity_map: HashMap<String, String> = HashMap<String,String>()
        organizationalEntity_map.put("name", "Mario_test")
        organizationalEntity_map.put("url0", "urltest0")
        organizationalEntity_map.put("url1", "urltest1")
        setOrganizationalEntity(organizationalEntity_map)
    */

}
