---
status: accepted
---

# Preserve the documented Gradle Task API during Core upgrades

In plugin 3.x, Core upgrades must preserve the documented Gradle Task API, including the Core types it exposes under
[ADR 0006](0006-isolate-only-version-branching-from-core-in-the-3x-line.md), and the SBOM Output Contract under
[ADR 0004](0004-version-the-sbom-output-contract-with-the-plugin.md). Methods on Core classes available through the
plugin's classpath are covered where the documented Gradle Task API relies on them.

Core 13.4.0 removes `Component.setPurl(PackageURL)` and its `packageurl-java` dependency. The documented `projectType`
property still uses `Component.Type`. The plugin declares `packageurl-java` directly to retain package URL generation
and dependency visibility. Build logic that calls the removed setter can pass the canonical string with
`component.setPurl(packageUrl.canonicalize())`.
