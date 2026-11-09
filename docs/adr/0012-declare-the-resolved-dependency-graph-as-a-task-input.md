---
status: accepted
---

# Declare the Resolved Dependency Graph as a task input

`cyclonedxDirectBom` is cacheable, and its inputs described files only: the **SBOM Evidence** and the metadata read
from it ([ADR 0010](0010-declare-sbom-evidence-as-task-inputs.md)). Much of a **Direct SBOM** comes from the
**Resolved Dependency Graph** and from no file: the identity of a project component, whose artifact is deliberately
kept out of the inputs, the dependency relationships, and the test label derived from the configurations that
contributed a component. A project dependency whose version changed or that was removed, a relationship added between
two components already present, and a dependency moved to a **Test Configuration** all left every input unchanged, so
the task stayed up to date and a shared build cache served the earlier document
([issue #960](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/960)).
[ADR 0001](0001-use-resolved-dependency-graphs-as-sbom-evidence.md) already commits the plugin to tracking changes in
dependency selection for cache invalidation. The graph is now declared as an input of its own, one entry per component:
its coordinates and project path, the components it depends on, and the names of the configurations that contributed
it.

The value is derived from the same serializable graph the document is written from, so the two cannot disagree and no
configuration is resolved a second time. It is a separate property from the files and the metadata, so that a build
log names the graph, and not the files or the metadata, as what changed.

## Considered options

- **Declare the artifacts of project dependencies as input files.** The usual Gradle way, and what version 3.2.0 did.
  Reading a project's jar makes the task consume the output of that project's `jar` task without depending on it, which
  Gradle rejects ([issue #776](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/776)); that is why project
  artifacts are filtered out of the inputs. Files also say nothing about relationships or contributing configurations,
  so a document could still go stale in those two ways.
- **Walk Gradle's resolution result a second time to compute the input.** Independent of the model, but it has to
  repeat every rule of the traversal, such as skipping constraints and self-references and replacing the root
  component. An input that drifts from the traversal brings the same defect back without any sign of it.
- **Declare the test label instead of the configuration names.** Fewer re-runs, since a dependency moved between two
  configurations that are not **Test Configurations** would leave the input unchanged. It repeats the matching against
  the test configuration patterns away from the place that writes the label, and any later use of the contributing
  configurations in the document would need the input changed again. A re-run that writes an identical document is
  harmless; a missed one is the defect being fixed.
- **Declare one digest of the graph.** A single short value instead of one entry per component. Gradle reports only
  that the property changed, whichever shape it has, so finding the component behind a cache miss means reading the
  value: readable entries can be printed and compared between two builds, a digest cannot.
- **Fold the graph into the declared metadata.** One property fewer. The metadata has no entry for a project component
  and is empty when metadata resolution is disabled, and a cache miss would no longer say whether enrichment or the
  graph changed.

## Consequences

- The cache key is not part of the **SBOM Output Contract**. The document produced from the same graph is unchanged,
  so this ships in a minor version.
- A change in the graph that does not reach the document, such as a dependency moved between two configurations that
  are not **Test Configurations**, re-runs the task and writes the same document.
