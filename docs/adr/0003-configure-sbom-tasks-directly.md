---
status: accepted
---

# Configure SBOM tasks directly

SBOM configuration belongs to the Direct and Aggregate SBOM tasks rather than to a shared plugin extension. Keeping
each producing task's inputs local to its project is a prerequisite for Gradle Project Isolation and avoids coupling
configuration to today's root-driven aggregation, but it does not by itself make the current plugin Project-Isolation
compatible. A root-owned extension would require copying state into tasks and encourage cross-project configuration;
direct task configuration costs some repetition across projects but keeps project configuration autonomous. The
explicit aggregator topology that completes Project Isolation compatibility is recorded in
[ADR 0011](0011-aggregate-only-explicitly-declared-members.md).
