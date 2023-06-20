
# How to manually modify Metadata

The Plugin makes it possible to manually add Manufacture-Data and Licenses-Data to the Metadata of the BOM. <br>
The structure of the Metadata is shown on https://cyclonedx.org/docs/1.4/json/#metadata. <br>
The editing of the Manufacture and Licenses-Data is optional. If the Manufacture/Licenses-Date isn't edited,
then the respective structure won't appear in the BOM.

To enable the modification of the metadata the cyclonedx-core-java plugin must be implemented in the build.gradle.

---

## Adding Manufacture-Data

In order to be able to define the Manufacture-Data you must __import org.cyclonedx.model.*;__ into the build.gradle.
<br>
You can add the Manufacture-Data by passing an Object of the Type __OrganizationalEntity__ to the Plugin.

__Example (groovy):__
```groovy
cyclonedxBom {
    //declaration of the Object from OrganizationalEntity
    OrganizationalEntity organizationalEntity_temp = new OrganizationalEntity();

    //setting the Name[String] and Url[List] of the Object
    organizationalEntity_temp.setName("Test");
    organizationalEntity_temp.setUrls(["www.test1.com", "www.test2.com"]);
    //declaration of the Object from OrganizationalContact
    OrganizationalContact organizationalContact = new OrganizationalContact();
    //setting the Name[String], Email[String] and Phone[String] of the Object
    organizationalContact.setName("Max_Mustermann");
    organizationalContact.setEmail("max.mustermann@test.org");
    organizationalContact.setPhone("0000 99999999");
    //adding the Object[OrganizationalContact] to the Object[OrganizationalEntity]
    organizationalEntity_temp.addContact(organizationalContact);

    //passing organizationalEntity_temp to the plugin
    organizationalEntity = organizationalEntity_temp;
}
```

__Example (Kotlin):__
```kotlin
cyclonedxBom {
    //declaration of the Object from OrganizationalEntity
    var organizationalEntity = OrganizationalEntity()
    //setting the Name[String] and Url[List] of the Object
    organizationalEntity.setName("Test")
    organizationalEntity.setUrls(listOf("www.test1.com", "www.test2.com"))
    //declaration of the Object from OrganizationalContact
    var organizationalContact = OrganizationalContact()
    organizationalContact.setName("Max_Mustermann")
    organizationalContact.setEmail("max.mustermann@test.org")
    organizationalContact.setPhone("0000 99999999")
    //adding the Object[OrganizationalContact] to the Object[OrganizationalEntity]
    organizationalEntity.addContact(organizationalContact)
    //passing organizationalEntity to the plugin
    setOrganizationalEntity(organizationalEntity)
}
```
It should be noted that some Data like OrganizationalContact, Url, Name,... can be left out. <br>
OrganizationalEntity can also include multiple OrganizationalContact.

For details look at https://cyclonedx.org/docs/1.4/json/#metadata.


## Adding Licenses-Data

In order to be able to define the Manufacture-Data you must __import org.cyclonedx.model.*;__ into the build.gradle.

You can add the Licenses-Data by passing an Object of the Type __LicenseChoice__ to the Plugin.
The Object from LicenseChoice includes __either License or Expression__. It can't include both.

### License

__Example (groovy):__
```groovy
cyclonedxBom {
    //declaration of the Object from AttachmentText -> Needed for the setting of LicenseText
    AttachmentText attachmentText = new AttachmentText();
    attachmentText.setText("This is a Licenses-Test");
    //declaration of the Object from License
    License license = new License();
    //setting the Name[String], LicenseText[AttachmentText] and Url[String]
    license.setName("XXXX XXXX Software");
    //license.setId("Mup")     // either id or name -> both not possible
    license.setLicenseText(attachmentText);
    license.setUrl("https://www.test-Url.org/")
    //declaration of the Object form LicenseChoice
    LicenseChoice licenseChoice_tmp = new LicenseChoice();
    //adding the License[License] to LicenseChoice[LicenseChoice]
    licenseChoice_tmp.addLicense(license);
    //Passing licenseChoice_tmp to the plugin
    licenseChoice = licenseChoice_tmp;
}
```

__Example (Kotlin):__
```kotlin
cyclonedxBom {
    //declaration of the Object from AttachmentText -> Needed for the setting of LicenseText
    val attachmentText = AttachmentText()
    attachmentText.setText("This is a Licenses-Test")
    //declaration of the Object from License
    val license = License()
    //setting the Name[String], LicenseText[AttachmentText] and Url[String]
    license.setName("XXXX XXXX Software")
    //license.setId("Mup")     // either id or name -> both not possible
    license.setLicenseText(attachmentText)
    license.setUrl("https://www.test-Url.org/")
    //declaration of the Object form LicenseChoice
    val licenseChoice = LicenseChoice()
    //adding the License[License] to LicenseChoice[LicenseChoice]
    licenseChoice.addLicense(license)
    //Passing licenseChoice_tmp to the plugin
    setLicenseChoice(licenseChoice)
}
```
It should be noted that License requires __either Id or Name__, but both can't be included at the same time.

Text and Url are optional for inclusion and multiple License can be added to LicenseChoice.

---

### Expression

__Example (groovy):__
```groovy
cyclonedxBom {
    //declaration of the Object from LicenseChoice
    LicenseChoice licenseChoice_tmp = new LicenseChoice();
    //setting the Expression[String] of LicenseChoice
    licenseChoice.setExpression("This is a Test Expression");
    //passing licenseChoice_tmp to the plugin
    licenseChoice = licenseChoice_tmp;
}
```

__Example (Kotlin):__
```kotlin
cyclonedxBom {
    //declaration of the Object from LicenseChoice
    val licenseChoice = LicenseChoice()
    //setting the Expression[String] of LicenseChoice
    licenseChoice.setExpression("This is a Test Expression");
    //passing licenseChoice_tmp to the plugin
    setLicenseChoice(licenseChoice)
}
```
---
For details of the BOM structure look at https://cyclonedx.org/docs/1.4/json/#metadata.

