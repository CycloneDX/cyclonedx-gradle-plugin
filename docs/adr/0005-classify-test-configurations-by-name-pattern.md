---
status: accepted
---

# Classify Test Configurations by name pattern

A component in a Direct SBOM is labeled `cdx:maven:package:test` only when every contributing
configuration is a Test Configuration. We classify Test Configurations by matching configuration
names against `testConfigs` (a list of full-match regexes on `CyclonedxDirectTask`), with default
`['^test.*']` so existing output stays unchanged. We deliberately do not derive Test Configurations
from Gradle source sets or Test tasks in this change: name patterns match `includeConfigs` /
`skipConfigs`, stay configuration-cache friendly, and leave richer Gradle-model detection as a
possible later enhancement. Projects with atypical test configuration names override `testConfigs`.

## Considered Options

- **Name-pattern list (`testConfigs`)** — chosen. Default preserves `startsWith("test")` behavior;
  override covers custom source sets such as `e2eTest*`.
- **Derive from source sets / Test tasks** — deferred. More accurate for some builds, but couples
  labeling to the Project model and is a larger behavior change.
- **Hard-coded broader default (e.g. `(?i).*test.*`)** — rejected. Would reclassify components under
  the SBOM Output Contract without an opt-in and likely require a major version (ADR 0004).

## Consequences

- Empty `testConfigs` means no configuration is a Test Configuration (all labeled `test=false`).
- Aggregate SBOMs keep labels from Contributing Projects' Direct SBOMs; `testConfigs` exists only
  on `cyclonedxDirectBom`.
