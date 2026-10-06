---
status: accepted
---

# Declare every file the SBOM was produced from as a task input

`cyclonedxDirectBom` is cacheable, and until now its only file input was the set of resolved artifacts. The POMs it
reads for **Metadata Enrichment** were resolved during execution and never entered the cache key, so a **Direct SBOM**
produced while a POM was missing or unreadable had the same key as one produced when that POM was healthy. The
incomplete document was stored on success, served from a shared build cache to every build whose artifacts matched, and
kept being served after the cause was gone. Every POM read to produce the document is now declared as an input beside
the artifacts: the **SBOM Evidence** and the task's inputs are the same set of files. A POM that appears, changes or
becomes readable then produces a different key, so a machine that read a broken POM computes a key no healthy machine
computes, and once the POM is healthy nothing addresses the poisoned entry any more.

The set is collected where the files are read, which is the component POM lookup and the model resolver Maven uses for
parent and imported POMs, and travels on the serializable graph as one task-wide collection. The input is a separate
property from the artifacts so that a build log or build scan names which kind of evidence changed.

The files alone are not enough. They are fingerprinted when the task runs, while the metadata the document is written
from was read when the task graph was configured, and the two moments can see different content: a POM rewritten by an
earlier task in the same build, or a configuration cache entry stored while a parent was missing and replayed after it
appeared. A document written from the older metadata would then be stored under the key of the newer files, which is
exactly the key a healthy build computes. So the metadata that enrichment contributed to each component, licenses,
publisher, description, external references and the unresolved-metadata value, is declared as an input as well, in a
canonical form that carries no file locations and writes every value with its length in front, so that two different
results cannot encode alike whatever the values contain. That line is the only thing separating a stale document from
a current one when both fingerprint the same files, so it cannot afford an ambiguity. A stale document is then stored
under a key only a build with the same stale metadata computes, and a build whose metadata is current never receives
it.

## Considered options

- **Decline to cache when metadata was unresolved.** Gradle offers no such decision after the fact: `cacheIf` and
  `doNotCacheIf` are evaluated before the task action runs, so a predicate reading a flag the action sets sees the value
  from before execution and the output is stored regardless. Measured on Gradle 9.7.1 with a task whose action flips a
  field both predicates read: both predicates logged the value from before the action ran, the entry was stored, and
  the next run with the outputs deleted came from the cache.
- **Fail the task when metadata was unresolved.** A failed task stores nothing, so this does contain the spread, for
  builds that opt in and at the moment the document is produced. It cannot help a document that was complete when it
  was written and became wrong later, for example when a POM is re-published with corrected licenses, because the key
  still only sees the artifacts. Whether a build may opt into failing is a separate decision; it does not replace
  correct inputs.
- **Declare one hash of the whole enrichment result as an input.** It covers everything the document depends on, which
  is why the metadata is declared, but a single value cannot say what changed: a cache miss would point at one hash
  instead of at the component whose metadata differs. The metadata is therefore declared per component in a readable
  form, so a build log or build scan names the component, and a changed POM file is still named by the file input
  beside it.
- **Read the POMs when the task runs instead of when the task graph is configured.** The metadata would then derive
  from the very files being fingerprinted, and a POM rewritten in between would yield a correct document rather than a
  consistently keyed stale one. It moves enrichment out of configuration into the task action, needs a model resolver
  that serves parents from files recorded earlier instead of resolving them, and parses every POM twice when the
  configuration cache is not reused. A parent that was missing when the graph was configured still cannot be found
  then. Declaring the metadata gives the same guarantee for the shared cache at a fraction of the change; reading at
  execution time remains the natural next step if the plugin moves enrichment there for other reasons.
- **Declare only the component's own POM.** Cheaper to collect, and wrong for the case reported in
  [issue #936](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/936): the license of
  `ch.qos.logback:logback-classic` lives in its parent POM, and a parent that fails to resolve leaves the component's
  own POM untouched.

## Consequences

- The first build after upgrading is a cache miss for every consumer, because every key changes. That happens once.
- The cache key is not part of the **SBOM Output Contract**. The document produced from the same evidence is unchanged,
  so this ships in a minor version.
- A metadata failure now yields a key that only machines in the same failed state compute. The incomplete document can
  still be produced and stored; it can no longer be served to a machine that reads the POM successfully.
- With metadata resolution disabled no POM is read and none is declared, so the inputs stay the artifacts alone.
- The inputs are computed when the task graph is configured, like the artifacts today, and a reused configuration
  cache entry replays the set it stored. A POM file that Gradle resolved and read, the component's own or a parent,
  is a configuration input, so a POM whose content changes makes the build reconfigure; `SbomEvidenceSpec` shows it
  for a parent whose license changes in place. Gradle does not record the absence of a parent or imported POM that
  failed to resolve, so a machine whose configuration cache was stored while that POM was missing keeps its old key,
  and its own stale document, until it reconfigures. Because the metadata is part of the key, it stores nothing under
  a key a healthy build computes; the staleness is local, and it predates this decision, since the whole graph was
  already stored in the configuration cache. `SbomEvidenceSpec` covers both a machine that keeps its configuration
  cache and one that starts afresh and shares the build cache with it.
