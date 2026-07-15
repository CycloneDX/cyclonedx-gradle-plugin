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

## Flagged ambiguities

- Prefer **SBOM** in explanatory prose. **BOM** remains correct when referring to CycloneDX specification concepts or
  public API names such as `cyclonedxBom`.
- Unqualified **dependency** can mean a declaration, a resolved component, or a relationship between components.
  Qualify the term when the distinction matters.
- The **SBOM Output Contract** covers parsed CycloneDX meaning, not byte-for-byte serialization. Formatting and ordering
  are not part of the compatibility guarantee when the SBOM remains semantically equivalent.

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
