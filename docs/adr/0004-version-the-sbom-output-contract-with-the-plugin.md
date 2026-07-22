---
status: accepted
---

# Version the SBOM output contract with the plugin

Produced Direct and Aggregate SBOMs and their default delivery are a public compatibility surface governed by the
plugin's semantic versioning. Across releases within one plugin major version, the same resolved dependencies and
artifacts, task configuration, and relevant environment-derived metadata must produce a backwards-compatible SBOM. A
change that violates this invariant requires a new major plugin version.

## Consequences

- A major version is required to change the default CycloneDX schema, the default set of included components or
  dependency relationships, a default output format, or a default output filename or location.
- A released opt-in schema, format, or output behavior is also part of the public contract. Removing it or changing it
  incompatibly requires a major version.
- A minor version may add an opt-in schema, component category, or output behavior that leaves existing behavior
  unchanged. It may also add optional metadata that is valid under the configured schema when existing fields,
  component identities, and dependency relationships retain their meaning.
- Supported behavior may be deprecated in a minor version when it remains functional and migration guidance is
  provided. Removal or incompatible replacement waits for the next major version.
- A correction that restores documented behavior or validity is not breaking, even when output changes. This includes
  fixes for invalid documents, incorrect component identities, or missing dependency relationships.
- Intentionally volatile values, such as timestamps and generated serial numbers, are exempt. Serializer formatting,
  whitespace, and ordering are also outside the contract when the parsed CycloneDX model remains equivalent, although
  deterministic output remains a quality goal.

The policy may require new default behavior to remain behind configuration until the next major release, but it
protects downstream parsers, policies, and other consumers from unexpected output breakage.
