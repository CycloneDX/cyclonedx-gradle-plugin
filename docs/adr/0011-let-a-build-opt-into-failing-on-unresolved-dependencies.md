---
status: accepted
---

# Let a build opt into failing on unresolved dependencies

`cyclonedxDirectBom` resolves every scanned configuration leniently, so an **Unresolved Dependency** never fails the
task: a module Gradle could not find is absent from the **Direct SBOM** with a warning as its only trace, and a module
whose artifact could not be fetched is listed as a `pom` component without hashes and without a log line. In a full
build the compile task fails on the same dependency first; in a run where the SBOM task is the only consumer of a
configuration, which is how a dedicated CI job generates the document, the build is green and the document is wrong.
Since 3.2.0 the resolved artifacts are task inputs, so such a document is keyed apart from a healthy one in the build
cache; the problem is not that it spreads but that it is produced and published at all.

A build may now opt in, with `failOnUnresolvedDependencies` on the task, to failing instead. With the switch on, the
task fails when Gradle reported that a dependency or an artifact of a scanned configuration could not be resolved,
the buildscript configurations included where `includeBuildEnvironment` selects them (a failing script classpath
stops the build before any task exists, so only user-defined buildscript configurations can reach the task). One
failure names every distinct problem with Gradle's own message, the reason Gradle keeps in the causes of its failure,
and the configurations it occurred in, so the reader can tell a repository to fix from a configuration to skip. The
default stays off.

The switch covers **Unresolved Dependencies** only. **Unresolved Metadata** keeps being reported on the component, as
ADR 0009 decided: a component without a usable POM is a legitimate state, indistinguishable from a repository failure,
and the maintainers asked that it never fail the build. Nothing legitimate is left to allow, so there is no allow-list:
a dependency that cannot resolve is not a dependency the build has, and a configuration that cannot resolve by design
belongs in `skipConfigs`.

Whichever way a run ends, and with the switch on or off, the task deletes the Direct SBOM of an earlier run before it
does anything else. Gradle removes outputs it does not know about but keeps those of an earlier run of the task, so a
run that failed for any reason used to leave a document beside the failed task, and a document that exists only
because a later run failed misrepresents the build. ADR 0004 governs the content and delivery of the documents
produced, not the survival of stale files, so this ships with the feature. The **Aggregate SBOM** of an earlier run is
not touched: the aggregate task does not run when a Direct SBOM failed, and under ADR 0003 no Direct SBOM task reaches
into another project's outputs, so a pipeline has to publish SBOMs from successful builds only.

The failures come from the lenient artifact view the plugin already resolves, which reports every dependency and
artifact that did not resolve, the graph failures included, on Gradle 8.4 as on 9.8; the unresolved results the
traverser sees would only duplicate them, so the traverser no longer reports them. Gradle keeps the reason in the
causes of its failure, so the causes are rendered into the text; listing every repository's failure rather than the
first relies on Gradle's internal `MultiCauseException`, guarded so that a release which moves it falls back to the
first cause instead of failing lenient builds. The first line of Gradle's text names what did not resolve and is the
identity of a failure. Gradle reports the same failure once per configuration, and not always with the same lines
beneath: a later lookup is answered from its cache without the locations it searched, a repository is disabled after
its first failure and later lookups name that instead, and a repository limited to some configurations is searched
for those alone. The lines beneath are therefore kept per reason: a body whose lines are all contained in another's
is the same reason with less context and merges into it, anything else is listed as its own reason with its own
configurations, so nothing Gradle said is lost and nothing is attributed to a configuration that did not see it.

The failures travel on the serializable graph. One entry per failure, its configurations and first line, is declared
as a task input, because the files the task reads do not change when a module is simply not there, and a task that
is up to date, or served from the build cache, never runs the check; the first line carries nothing machine-specific,
so the key stays the same wherever the build runs. The task action throws, so the failure reads as an execution
failure of the task and behaves the same with and without the configuration cache, and with the switch off it logs
one warning per failure with Gradle's full text, from the action rather than from the provider, because a reused
configuration cache entry replays the graph without computing it again and the action is what still runs.

## Considered options

- **Resolve non-leniently by default.** The lenient view was introduced in 2.0 for Android builds whose configurations
  cannot be resolved at all, and ADR 0004 reserves a change of default behavior for a major version.
- **Represent the unresolved dependency as a component.** Rejected in ADR 0009: it changes the default set of
  components and contradicts ADR 0001, where the resolved graph is the authority for what the document contains.
- **Fail on Unresolved Metadata too, with an allow-list for expected gaps.** Proposed with the reporting in ADR 0009
  and declined in its review: unavailable metadata is not a defect of the build, and once it is out of scope the
  allow-list has nothing left to answer for.
- **Throw from the input provider instead of the task action.** Gradle reports that as being unable to determine the
  dependencies of the task, which misdescribes what happened. It has one advantage: a failure while the task graph is
  calculated stores no reusable configuration cache entry, so the next run resolves again, whereas a failure in the
  task action is replayed from a reused entry until `--refresh-dependencies` or another invalidation. Gradle's own
  dependency cache keeps a missing artifact on record either way, so the practical difference is small, and the
  attribution and wording of the failure were worth more.
- **Key failures by their full text, or by their first line and causes.** By full text, Gradle's cache answers split
  one missing module into one entry per configuration; by first line and causes, one dependency behind a repository
  that Gradle disabled after its first failure splits in two, with the count varying with resolution order.
- **Throw the collected failures as causes of the task's exception.** Gradle would render the tree with its own hints,
  such as the advice to adjust `metadataSources` for a module without a POM. The failures are collected when the task
  graph is configured and do not survive the configuration cache, so the task has text, and the hints are lost; the
  compile task prints them for the same problem.
- **Delete the Aggregate SBOM too.** The aggregate task does not run when a producer failed, and a build listener or
  flow action that reaches into another project's outputs contradicts ADR 0003; the limitation is documented instead.
- **Leave the outputs of the previous run in place.** Gradle does not remove them when a task fails before writing, so
  anything reading the report path directly would find a document next to a failed task.

## Consequences

- With the switch off, two things change for existing builds: a failed run no longer leaves the Direct SBOM of an
  earlier run behind, and one warning per failure, with Gradle's full text, replaces one per configuration. Neither
  touches the documents produced, so under ADR 0004 this ships in a minor version. The switch and the failures are
  new task inputs, so the first build after upgrading is a cache miss for every consumer, once.
- The failure text is for the build log and is not part of the **SBOM Output Contract**; it carries Gradle's messages
  verbatim, searched locations and causes included. It is built from Gradle's messages, not its failure objects, so
  Gradle's own hints are not part of it, and a Gradle release that words these messages differently changes how
  failures group, never whether the task fails.
- A build that turns the switch on in a job that scans configurations it never compiles will see failures that were
  silent before. That is the point, and `skipConfigs` is the remedy for configurations that are not meant to resolve.
- Recovery after the repository is healthy again needs `--refresh-dependencies`: Gradle records the absence of a
  module or artifact in its dependency cache and does not ask the repository again while that record stands, and a
  stored configuration cache entry replays the resolution it captured. The reverse holds too: a reused entry replays a
  healthy resolution after a remote repository lost a module, so the switch does not notice the loss until the entry
  is invalidated. The option bypasses both.
