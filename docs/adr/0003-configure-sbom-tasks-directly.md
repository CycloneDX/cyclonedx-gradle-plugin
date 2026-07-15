---
status: accepted
---

# Configure SBOM tasks directly

SBOM configuration belongs to the Direct and Aggregate SBOM tasks rather than to a shared plugin extension. Keeping
each producing task's inputs local to its project is a prerequisite for Gradle Project Isolation and avoids coupling
configuration to today's root-driven aggregation, but it does not by itself make the current plugin Project-Isolation
compatible. A root-owned extension would require copying state into tasks and encourage cross-project configuration;
direct task configuration costs some repetition across projects but keeps project configuration autonomous. The
proposed explicit aggregator topology remains owned by
[issue #848](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/848) until that proposal becomes an accepted
implementation decision.
