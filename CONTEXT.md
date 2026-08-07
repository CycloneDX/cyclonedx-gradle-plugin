# CycloneDX SBOM Generation

This context names the documents and boundaries specific to generating CycloneDX SBOMs from Gradle projects.

## Language

**Direct SBOM**:
An SBOM whose boundary is one Gradle project and the resolved dependency evidence selected for that project.
_Avoid_: Per-module BOM, project BOM

**Aggregate SBOM**:
An SBOM that combines the **Direct SBOMs** of its **Contributing Projects** under one main component.
_Avoid_: Fat BOM, merged report

**Contributing Project**:
A Gradle project whose **Direct SBOM** belongs to a particular **Aggregate SBOM**.
_Avoid_: Every subproject, discovered project

**Build Environment Dependency**:
A dependency of the build logic rather than of the software being described. It is excluded from a **Direct SBOM**
unless that boundary is expanded deliberately.
_Avoid_: Project dependency, application dependency

**Metadata Enrichment**:
Additional descriptive evidence, such as licenses and organizational details, attached to a component beyond its
resolved identity and dependency relationships.
_Avoid_: Dependency resolution

**SBOM Output Contract**:
The public compatibility surface of produced **Direct SBOMs**, **Aggregate SBOMs**, and their default delivery. It is
versioned with the plugin according to [ADR 0004](docs/adr/0004-version-the-sbom-output-contract-with-the-plugin.md).
_Avoid_: Internal output, report format

**SBOM Attestation**:
A signed statement that binds an artifact, identified by a cryptographic digest, to the **Direct SBOM** or **Aggregate
SBOM** that describes it. The plugin produces the SBOM; a build platform or attestation service binds and signs it.
_Avoid_: SLSA SBOM, signed SBOM file

**Published SBOM**:
A **Direct SBOM** or **Aggregate SBOM** distributed as a versioned release artifact for the component or distribution
whose boundary it describes. Publication makes the document available to consumers but does not itself attest or sign
it; the SBOM and component repositories may differ when their identities and version relationship remain unambiguous.
_Avoid_: SBOM Attestation, embedded SBOM

**Test Configuration**:
A Gradle configuration treated as test evidence when labeling a component in a **Direct SBOM**. A component is marked
test only when every configuration that contributed it is a **Test Configuration**. Classification is by configurable
name pattern per [ADR 0005](docs/adr/0005-classify-test-configurations-by-name-pattern.md).
_Avoid_: Test dependency, test scope (unqualified), test source set

## Flagged ambiguities

- Prefer **SBOM** in explanatory prose. **BOM** remains correct when referring to CycloneDX specification concepts or
  public API names such as `cyclonedxBom`.
- Unqualified **dependency** can mean a declaration, a resolved component, or a relationship between components.
  Qualify the term when the distinction matters.
- The **SBOM Output Contract** covers parsed CycloneDX meaning, not byte-for-byte serialization. Formatting and ordering
  are not part of the compatibility guarantee when the SBOM remains semantically equivalent.
- The **SBOM Output Contract** is conditioned on the JVM executing the build. Evidence that JVM cannot produce, such as
  an artifact hash whose algorithm no installed security provider offers, is absent rather than fabricated or fatal. Two
  SBOMs generated from identical inputs on different JVMs may therefore differ, and that difference is not a contract
  violation.
- Unqualified **Java 8** can mean the bytecode target of `src/main`, a cell of the test matrix, or the JVM a consumer's
  Gradle build runs on. Say **build JVM** for the last of these; it is the one the **SBOM Output Contract** depends on.
- **Test Configuration** is a labeling concern for components already in a **Direct SBOM**. It is distinct from
  `includeConfigs` / `skipConfigs`, which decide which configurations are scanned into that document.
- An **SBOM Attestation** must preserve the boundary of the SBOM it binds. A per-project artifact normally uses that
  project's **Direct SBOM**; an **Aggregate SBOM** is appropriate only when the attested artifact represents the same
  set of **Contributing Projects**.
- A SLSA Build level applies to an artifact's build provenance and build platform, not to its SBOM. Say “an artifact
  with SLSA Build provenance and an attested CycloneDX SBOM”; avoid “SLSA-compliant SBOM.”

## Example dialogue

> **Developer:** Does every project in the build belong to the Aggregate SBOM?
>
> **Domain expert:** Only a Contributing Project does. Its Direct SBOM is the document consumed during aggregation.
>
> **Developer:** Are build-script libraries included in that Direct SBOM by default?
>
> **Domain expert:** No. They are Build Environment Dependencies and are included only when that boundary is expanded
> deliberately.
>
> **Developer:** Can a minor plugin release make an incompatible change to the generated component identities?
>
> **Domain expert:** No. Component identity is part of the SBOM Output Contract, so that change requires a new major
> plugin version.
