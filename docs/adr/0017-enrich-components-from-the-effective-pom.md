---
status: accepted
---

# Enrich components from the effective POM

**Metadata Enrichment** was ported from the CycloneDX Maven plugin. It read a component's organization, scm, description
and management URLs from the raw POM and, where that POM declared a parent, walked up to it by computing the parent's
location from the artifact's path with Maven repository layout rules. That walk works in a Maven local repository, which
is where the port came from. Gradle's module cache keeps the group as one dotted directory and puts a hash directory
below the version, so the computed path never exists and the walk silently finds nothing. `MavenProjectLookup` already
built the **Effective POM** of every component through Gradle's own resolution, but copied only its licenses onto the
raw project. A module whose organization and scm live in its parent, as Jackson, Logback and Jetty modules do, had no
publisher and no `website` or `vcs` reference. Measured on one application build with 3.6.1: of 86 components whose own
POM declares neither element, none had a `website` reference and two had a `vcs` reference; of 110 that declare them, 97
had `vcs`.

The lookup now returns the **Effective POM** as the project it reports, so every field **Metadata Enrichment** reads
comes from it: the publisher and the `website` reference from the organization, the `vcs` reference from the scm, and
the `build-system`, `distribution`, `issue-tracker` and `mailing-list` references from the management blocks and the
mailing lists. Maven's inheritance rules apply unchanged, including the one that appends the module's artifactId to an
inherited `url` and `scm.url` unless the parent sets `child.scm.url.inherit.append.path="false"`. That is what
`mvn help:effective-pom` prints and what the CycloneDX Maven plugin emits, and the POM author controls it. Where the
**Effective POM** cannot be built, the component still reports what its own POM declares, as
[ADR 0009](0009-report-what-the-sbom-could-not-describe.md) decided.

**The description is the one exception: it is written only where the component's own POM declares it.** Maven inherits
it like any other field, but a parent's description describes the parent, and the parents most modules share describe
a foundation: `oss-parent` says "Sonatype helps open source projects to set up Maven repositories", `apache` says "The
Apache Software Foundation provides support for the Apache community", `logback-parent` says "logback project pom.xml
file". In a sample of 600 POMs from one module cache, 97 declare no description and have a parent, so about one module
in six would carry such a sentence as its own. The Maven plugin does write it; here a module without a description keeps
none, as before.

The lookup keeps each component's model until the whole graph is traversed, so that a component met in several
configurations is built once. The **Effective POM** carries the managed dependencies with every imported BOM expanded
into them, which the raw model did not, and a build on Spring or Jackson would retain thousands of entries per
component. The sections enrichment never reads are dropped before the model is kept: managed dependencies,
dependencies, build, profiles, repositories, reporting, properties and modules. A section forgotten in that list costs
memory, never a value, which is why dropping is preferred over copying the fields that are read.

The pass over the POM embedded in the artifact's jar, and its parent walk, stay as they are and keep running first. The
embedded POM is the only metadata for a component resolved from a jar-only repository, the walk still works for
`mavenLocal()` and other Maven-layout repositories, and since enrichment fills only what is still empty, a component
that had a value before keeps exactly that value. The walk is not a fallback for an **Effective POM** that could not be
built: it serves the embedded POM only.

## Considered options

- **Copy organization, scm, description and the management blocks onto the raw project, as the licenses were.**
  Explicit, but it restates Maven's inheritance list by hand and misses the next field someone reads. Returning the
  **Effective POM** makes every field effective at once, and dropping the heavy sections fails safe where copying
  would fail silent.
- **Inherit the description too, as Maven and the Maven plugin do.** Consistent, and wrong for one module in six, which
  would describe itself as a foundation. A value the document states about a component has to be about that component.
- **Walk the parents through Gradle's resolution and take the parent's literal `scm.url`.** That avoids the appended
  artifactId, which may not be a browsable page. It also reimplements inheritance and diverges from Maven and from the
  Maven plugin for the same POM; an author who does not want the path appended has an attribute for that.
- **Delete the parent walk.** It is dead code for a build resolving from Gradle's cache, but it still serves
  Maven-layout repositories, and removing it changes their output without fixing anything.
- **Let the Effective POM win over the embedded POM.** The two POMs can differ, as kryo 4.0.3's do: the jar's POM
  declares the scm and the repository's does not. Keeping the embedded POM first keeps every value that was reported
  before.
- **Read the project `url` as a `website` reference, as the Maven plugin does.** Nothing reads it today, so adding it
  is new behavior rather than a correction, and belongs in its own opt-in change under
  [ADR 0004](0004-version-the-sbom-output-contract-with-the-plugin.md).

## Consequences

- Components inheriting their metadata gain a publisher and external references they lacked. Under ADR 0004 this is a
  correction rather than new default behavior: metadata resolution was documented as resolving publishers and external
  references, and the parent walk's stated purpose was to do what an effective POM does. A minor version may ship it.
- A module whose own POM has no description has none in the document, as before; a parent's description is never
  written as a module's.
- An inherited `vcs` reference carries the module's artifactId appended to the parent's URL, which is Maven's reading of
  the POMs.
- Modules under a parent that declares a `distributionManagement` repository gain a `distribution` reference to that
  repository's URL, often a staging repository. The CycloneDX type for a publishing endpoint is `distribution-intake`,
  which the Maven plugin writes from schema 1.5 on; this plugin's mapping predates this decision and is unchanged by it.
- The CycloneDX Maven plugin and this plugin now describe the same POM the same way, except for the description, the
  `website` reference, which this plugin takes from the organization and the Maven plugin from the project `url`, and
  the `distribution-intake` type.
