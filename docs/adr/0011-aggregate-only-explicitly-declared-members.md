---
status: accepted
---

# Aggregate only explicitly declared members

Each project that should produce a Direct SBOM applies the plugin itself and publishes that document as a consumable
`cyclonedxDirectBom` variant. An Aggregating Project declares its Contributing Projects on its resolvable
`cyclonedxAggregation` configuration, modeled on Gradle's `jacoco-report-aggregation` and `test-report-aggregation`
plugins, and the Aggregate SBOM task resolves exactly those members, merges their Direct SBOMs, and synthesizes the
edges from its main component to each member's main component.

Membership is explicit only. Nothing is discovered: applying the plugin to the root no longer configures
subprojects, and the Aggregating Project contributes its own Direct SBOM only when it declares itself
(`cyclonedxAggregation project(":")`), so an Aggregate SBOM contains nothing the build did not name. An Aggregate SBOM
task without declared members has nothing to aggregate and is skipped, which lets every project apply the plugin
without becoming an Aggregating Project.

A declared member fails aggregation loudly when it cannot be resolved, publishes no Direct SBOM, or publishes one that
is missing or unparseable. A member whose `cyclonedxDirectBom` task is disabled publishes no artifact, so a file left
by an earlier run can never stand in for it; excluding a project means removing its declaration.

This is the only membership model that is both configuration-cache-safe and compatible with Gradle Project Isolation
([issue #847](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/847)): implicit, root-driven membership
needs the root to read and configure its subprojects at configuration time, which Isolation forbids. A shared mutable
build service was rejected because its accumulated state is not restored on a configuration-cache hit and it assumes
every project was configured before the task graph is built.

It costs users per-project application and member declarations, and existing root-only setups stop aggregating
subprojects, so it shipped in a major version per
[ADR 0004](0004-version-the-sbom-output-contract-with-the-plugin.md). Zero-wiring whole-tree aggregation remains
available from an initialization script that applies the plugin to every project and declares each as a member; that
script iterates projects and therefore runs only with Project Isolation disabled.
