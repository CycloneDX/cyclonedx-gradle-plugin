---
status: accepted
---

# Compose build SBOMs from project SBOMs

Each contributing Gradle project produces a Direct SBOM, and an Aggregate SBOM consumes and combines those
project-level documents instead of rediscovering every project's dependency graph in one build-wide operation. This
preserves project boundaries, provides useful per-project outputs, and makes aggregation depend on declared producer
outputs; it costs additional tasks and intermediate documents. Only selected contributing projects participate in
aggregation, and a missing expected Direct SBOM is an error rather than permission to emit a silently incomplete
Aggregate SBOM.
