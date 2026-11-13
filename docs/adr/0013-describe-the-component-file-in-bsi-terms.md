---
status: accepted
---

# Describe the Component File in the terms of BSI TR-03183-2

BSI TR-03183-2, the German guideline written for the EU Cyber Resilience Act, requires of every component its file name
and three flags: executable or not, archive or not, structured or not (section 5.2.2, explained in sections 8.1.4 to
8.1.7). In CycloneDX they travel as the properties `bsi:component:filename`, `bsi:component:executable`,
`bsi:component:archive` and `bsi:component:structured`, under the `bsi` namespace registered in the CycloneDX property
taxonomy and defined by BSI. The plugin already resolves and hashes the file of every component and wrote nothing about
it, so builds that needed the fields post-processed the document
([issue #865](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/865)). A **Direct SBOM** now describes each
**Component File** with these four properties when `includeBsiFields` is set. The main component's file is named by
`componentFileName`, which defaults to the `jar` task's archive file name where the `java` plugin is applied and that task
is enabled; a build that ships another archive, such as Spring Boot's `bootJar`, names it itself.

The flags are derived from the file name alone, by a fixed table. A Java archive (`.jar`, `.war`, `.ear`, `.aar`) is
`executable`, `archive` and `structured`: section 8.1.4 names Java and shared libraries as executable, and section 8.1.6
names zip-format archives as structured. A general archive (`.zip`, `.tar`, `.tar.gz`, `.tgz`, `.7z`, the formats
section 8.1.6 lists) is `non-executable`, `archive` and `structured`, the way table 7 classifies an rpm package: the
archive file itself is not run, whatever it contains. A shared library (`.so`, `.dll`, `.dylib`) is `executable`, `no
archive` and `unstructured`: section 8.1.4 names shared libraries as executable, and section 8.1.6 makes a binary, which
carries no metadata of what it was linked from, unstructured. Any other file gets its name and no flags.

## Considered options

- **Read `archive` as a combination of several components, so that only a fat jar is one**, with `no archive` as the
  default, which is the reading proposed in issue #865. Table 7 of the guideline classifies an rpm package, which bundles
  one project's files, as an archive, and a jar bundles its class files the same way. Whether a jar contains other
  components cannot be told from the outside either, so the plugin would write a default where the guideline asks for an
  observation.
- **Default the flags for a file whose format the table does not know.** The BSI taxonomy wants each flag exactly once
  per component, which a default would satisfy, but `non-executable` is wrong for a shared library and `unstructured` is
  wrong for a tarball. The guideline's own rule for information that cannot be established is to omit it (section
  3.2.1). A missing format belongs in the table.
- **Let the build override the flags per component.** The fat-jar reading and formats outside the table would need it;
  both are settled by the table, and an override is public surface that is hard to remove. It can be added if a format
  turns up that a file name cannot classify.
- **Write the properties for every build.** They change the document for every user, and a BSI-compliant document needs
  more than they give (schema 1.6, a manufacturer, SHA-512 hashes). ADR 0004 asks for opt-in.
- **Resolve a project dependency's archive to name its file.** Reading another project's jar is what
  [issue #776](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/776) rejected: the task would consume a `jar`
  output without depending on it. A project's own **Direct SBOM** names its main component instead, and the
  **Aggregate SBOM** carries that component over.

## Consequences

- Off by default, so the **SBOM Output Contract** is unchanged for every existing build and this ships in a minor
  version.
- For a build that opts in, the property names, their values and the table are part of the contract. Adding a file
  format to the table is additive; changing the value a format maps to is a major.
- A consumer checking the BSI taxonomy's cardinality sees the three flags missing on a component whose file format the
  table does not know. The fix is a table entry, not a default.
- No new task input. The file names come from the artifacts the task already fingerprints by relative path, and the
  switch and the main component's name are plain inputs.
- Schema versions below 1.3 drop the properties, as they drop every property the plugin writes.
- The flags describe the file format, not the contents: a jar that bundles other libraries is still one `archive`
  marked `structured`, and nothing says whether its original components can still be told apart.
