---
status: accepted
---

# Isolate only version-branching logic from Core in the 3.x line

Upgrading to `cyclonedx-core-java` 13.0.0 (#884) asked us to "prepare the next-major boundary" by isolating Core
construction behind plugin-owned internal values. We isolated only the places the plugin branches on
`org.cyclonedx.Version` and the schema-version allow-list behind an internal version type, rather than mirroring
`OrganizationalEntity`, `LicenseChoice`, and `ExternalReference` as plugin-owned types.

There are three such branches: the manufacturer/manufacture and Tool/ToolInformation thresholds in `SbomBuilder`, and
the `>= 1.2` extension of the artifact hash algorithm set in `HashUtils` that ADR 0007 moved into the plugin. The third
arrives with the Core upgrade itself, ahead of the internal version type, so it is written as a direct `Version`
comparison and folded in when that type lands.

## Consequences

- The five core-backed task properties (`schemaVersion`, `projectType`, `organizationalEntity`, `licenseChoice`,
  `externalReferences`) stay Core-typed on the public API in the 3.x line; only `SbomBuilder`'s internal branching is
  isolated.
- #883 owns building the DSL-facing internal configuration model that normalizes those five properties and maps them
  to Core at a compatibility boundary; it explicitly excludes mirroring the entire CycloneDX object model. Mirroring
  `OrganizationalEntity`/`LicenseChoice`/`ExternalReference` now, with no DSL yet consuming the mirror, would be
  speculative work #883 would likely redo or discard.
- If a further version-conditional branch on Core types appears before #883 lands, extend the same internal version
  type rather than introducing a second isolation mechanism.
