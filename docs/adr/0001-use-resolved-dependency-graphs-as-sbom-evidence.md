---
status: accepted
---

# Use resolved dependency graphs as SBOM evidence

The plugin treats Gradle's resolved dependency graphs, rather than dependency declarations or Maven POM dependency
lists, as the authority for component identities, selected versions, and dependency relationships. This makes the SBOM
describe what the build actually uses after constraints, conflict resolution, substitution, and transitive selection;
resolved artifacts and POMs may enrich component metadata, but they do not replace the resolved graph as the source of
relationships. The trade-off is that SBOM generation resolves selected configurations and must track changes in
dependency selection for cache invalidation, but using declarations instead would allow the SBOM to diverge from the
built software.
