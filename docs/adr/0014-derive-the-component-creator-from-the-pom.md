---
status: accepted
---

# Derive the Component Creator from the POM organization or source repository

BSI TR-03183-2 requires of every component its creator: the email address of the entity that created and maintains it,
or, where no address is available, a URL such as the creator's home page or the project's web page (section 5.2.2). In
CycloneDX 1.6 the field is `manufacturer`, on `metadata.component` for the primary component and on each entry of
`components` ([issue #865](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/865)). The plugin read the
organization and the scm of every POM already and wrote them as the component's `publisher` and as `website` and `vcs`
references; it wrote no creator, and it wrote the build's `organizationalEntity` only as the SBOM's own manufacturer.
When `includeBsiFields` is set, every component now carries a **Component Creator**: a resolved module gets the
organization URL of its POM, or the scm URL when the POM declares no organization, with the organization's name when
there is one; the document's main component and every project of the build get the `organizationalEntity` the task
configures, so `metadata.manufacturer` and `metadata.component.manufacturer` both name it, as the guideline asks for the
SBOM and for the primary component.

## Considered options

- **The email addresses of the POM's developers.** The guideline prefers an address, but a developer's address is a
  person's, not the entity's, and listing people's addresses in a document meant to be shipped is a privacy decision the
  plugin should not take. The URL the guideline accepts in its place is the organization's own.
- **The POM's `<url>` element.** It is "the project's web page" in the guideline's words, and many POMs declare it
  without an organization. It is not read today, so using it means a new field on the serializable graph and in the
  metadata that ADR 0010 declares as a task input. The organization and scm URLs are already there; `<url>` can be
  added later as a further fallback without changing what the existing ones produce.
- **`supplier` instead of `manufacturer`.** `supplier` exists since CycloneDX 1.2 and would reach older schemas, but it
  names the entity that distributes the component, not the one that created it, and the guideline's own mapping names
  `manufacturer`. Below 1.6 Core drops the field; since the build asked for the creators, the task says so in a warning
  and writes the properties alone.
- **Move the organizational entity to `metadata.component` instead of writing it twice**, as
  [PR #836](https://github.com/CycloneDX/cyclonedx-gradle-plugin/pull/836) proposes. The guideline wants both: the SBOM's
  creator in section 5.2.1 and the primary component's in section 5.2.2. Whether the default document should move the
  entity is that PR's question; with the switch off nothing changes here.
- **No creator for a project of the build.** A project dependency has no POM, so nothing can be read; but a **Direct
  SBOM** read on its own would then show the organization's own code without a creator. The organizational entity the
  task configures is the right answer for a project of the same build, and the graph does not record which build a
  project path belongs to, so an included build is treated the same way.

## Consequences

- With the switch on, the source of a module's creator is part of the **SBOM Output Contract**: adding a fallback such
  as the POM `<url>` is additive, changing the order of the existing ones is a major.
- A module whose POM declares neither an organization URL nor an scm URL gets no creator, and nothing says so in the
  document; a consumer checking the guideline sees the field missing.
- A project of the build is attributed to the entity of the task writing the document, not to the entity its own task
  configures. In a build whose projects belong to different organizations, a project's **Direct SBOM** therefore
  names its own organization as the creator of a sibling; the **Aggregate SBOM** does not, because the sibling's own
  main component replaces that entry.
- The organizational entity now reaches more of the document, so the task declares it, the license choice and the
  external references as inputs in a canonical form, beside the `@Internal` properties that hold the Core objects.
  Every consumer's first build after upgrading misses the cache once, as after ADR 0010.
