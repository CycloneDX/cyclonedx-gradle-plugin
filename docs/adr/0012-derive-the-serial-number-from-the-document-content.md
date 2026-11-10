---
status: accepted
---

# Derive the serial number from the document content when a timestamp is supplied

A **Direct SBOM** or **Aggregate SBOM** was never the same file twice: `metadata.timestamp` was the current time and
the serial number a random UUID, so every archive or image that embeds the document changed on each build
([issue #292](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/292)). A task's `timestamp` input now
supplies the time, as an ISO 8601 instant or as seconds since the epoch, and when it is set the serial number, if one
is included, is a name-based UUID over the serialized document without a serial number. The document is then a
**Reproducible SBOM**: the same inputs and plugin version give the same bytes, and the serial names the content.
Without a timestamp nothing changes: the current time and a random serial, as before.

## Considered options

- **Honor `SOURCE_DATE_EPOCH` by default.** What reproducible-builds.org asks of tools, and what Apache Grails and
  the HiveMQ license plugin do around this plugin. Gradle itself ignores the variable, reading it would make it a
  configuration input of every build whether or not it wants reproducible output, and
  [ADR 0004](0004-version-the-sbom-output-contract-with-the-plugin.md) wants new output behavior to be opt-in. A build
  that wants the variable assigns `providers.environmentVariable("SOURCE_DATE_EPOCH")` to the property.
- **Omit the timestamp instead of writing it.** The Maven plugin's reproducible mode, and what the CycloneDX property
  taxonomy requires of a document marked `cdx:reproducible`. NTIA's minimum elements expect a creation time, and every
  consumer this was written for wants the commit time in the document. For the same reason the `cdx:reproducible`
  property is not written: with a timestamp present, the claim is contestable.
- **Derive the serial number from the coordinates.** The Maven plugin hashes group, artifact, version and the metadata
  properties. Two different documents for the same version then share one serial, and the serial is what
  `urn:cdx:<serial>/<version>#<ref>` references identify, so it must differ when the content differs.
- **A separate switch for the serial number.** A content-derived serial over a document stamped with the current
  time changes on every build, so the switch has no meaningful setting without a supplied timestamp. The timestamp
  alone decides.

## Consequences

- The specification says every generated BOM SHOULD carry a unique serial number even when the content is unchanged.
  A **Reproducible SBOM** deviates deliberately, which is why it takes an explicit input to get one.
- Serializer formatting and ordering are outside the **SBOM Output Contract**, so a plugin upgrade may change the
  bytes and with them the serial. Reproducibility is promised for one plugin version.
- The JSON and XML outputs of one task carry the same serial, computed once over the JSON form of the document, or
  over its XML form for schema 1.0 and 1.1, which have no JSON.
- The document also carries metadata read from the environment: the build-system reference, which on a CI service
  names the individual run, and the VCS reference. They are hashed like everything else, so a build that wants the
  same bytes on every CI run switches the build-system reference off or supplies its own.
- `dependsOn` lists of a **Direct SBOM** are sorted by ref unconditionally. Ordering is outside the contract, and the
  **Aggregate SBOM** already sorted them.
