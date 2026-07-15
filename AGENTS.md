# AGENTS.md

This file provides guidance to coding agents working in this repository. `CLAUDE.md` is a symlink to it, so Claude
Code (claude.ai/code) reads the same content; `apm.yml` also targets Codex and Copilot.

## What this is

A Gradle plugin (`org.cyclonedx.bom`) that generates CycloneDX SBOMs from Gradle builds. It is itself built with
Gradle and published to the Gradle Plugin Portal and Maven Central.

## Planning changes — read this first

Every non-trivial change is planned through a **`/grill-with-docs`** session. Do not jump from a request straight to
editing code, and do not substitute your own plan for the session. Work that skips it will not be accepted for review.

**You cannot start this yourself.** The skill is marked `disable-model-invocation: true` — it is user-invocable only,
and the interview it runs is a back-and-forth that needs the user answering. So when a request would change behavior,
architecture, or public surface, stop and ask the user to run `/grill-with-docs`, then follow the session where it
leads. Proceed without it only when the user explicitly declines or the change is genuinely trivial (a typo, a
comment, a version bump).

The session interviews the user one decision at a time and, via `/domain-modeling`, writes the outcome down as it
crystallises: new or sharpened terms in `CONTEXT.md`, and an ADR in `docs/adr/` for anything architectural. Those
artifacts are the point — a plan that leaves no trace in them has not been through the process.

## Commands

```bash
./gradlew build                 # compile, spotlessCheck, and full test matrix
./gradlew spotlessApply         # required before commit — the build fails on formatting violations
./gradlew publishToMavenLocal   # to try the plugin against a real external project
```

Testing runs against a Java toolchain matrix. The default `test` task is deliberately **disabled** and only aggregates
`testJava8`, `testJava11`, `testJava17`, `testJava21`, `testJava25` — so `./gradlew test` runs the whole matrix and
takes a long time. During development, target one version:

```bash
./gradlew testJava21                                          # one Java version
./gradlew testJava21 --tests "*DependencyResolutionSpec"      # one spec
./gradlew testJava21 --tests "*DependencyResolutionSpec.should resolve*"   # one feature method
```

`GradleVersionsSpec` alone runs the plugin against ~18 Gradle versions on each Java version; it dominates matrix
runtime. Avoid running the full matrix locally unless you are validating a release.

## Constraints that break the build if ignored

- **Bytecode target is Java 8** (`options.release = 8`) even though the toolchain is Java 25. No APIs newer than
  Java 8 in `src/main`.
- **NullAway runs as an ERROR** over `org.cyclonedx.gradle`. Every package is `@NullMarked` via `package-info.java`;
  anything nullable needs an explicit `@Nullable` (JSpecify). NullAway is disabled for test compilation.
- **Errorprone `DefaultCharset` and `MissingOverride` are ERRORs** — always pass an explicit charset.
- **Spotless / PalantirJavaFormat**, 120 columns, plus a mandatory Apache-2.0 license header on Java files. Run
  `spotlessApply` rather than hand-formatting.

## Commits

Sign off every commit (`git commit -s`). The DCO bot blocks the merge without it, and fixing it after the fact means
rewriting the branch.

Use [conventional commit](https://www.conventionalcommits.org/en/v1.0.0/) subjects — `feat:`, `fix:`, `docs:`,
`style:`, `refactor:`, `test:`, `chore:`, plus `build:` and `ci:` as used in history. Nothing validates this
mechanically, but adherence is unanimous, so a non-conforming subject stands out in the log.

## Architecture

`CyclonedxPlugin.apply` registers, for the project it is applied to and every subproject, a **`cyclonedxDirectBom`**
task (`CyclonedxDirectTask`, output `build/reports/cyclonedx-direct/`) producing that project's *Direct SBOM*. On the
applied project only, it registers **`cyclonedxBom`** (`CyclonedxAggregateTask`, output `build/reports/cyclonedx/`),
which merges those documents into an *Aggregate SBOM*.

The two are wired through Gradle configurations rather than direct task coupling: each project exposes a consumable
`cyclonedxDirectBom` configuration carrying its SBOM as an artifact, and the aggregate project resolves a
`cyclonedxBom` configuration whose dependencies are added lazily (`addAllLater`) so that projects whose direct task is
disabled are excluded at resolution time. `CyclonedxAggregateTask` treats a *missing* expected input SBOM as an error
rather than silently emitting an incomplete document.

The generation pipeline inside `CyclonedxDirectTask`:

1. `SbomGraphProvider` (a `Callable<SbomGraph>`) selects in-scope configurations by applying `includeConfigs` /
   `skipConfigs` regexes, resolves them, and collects artifacts.
2. `DependencyGraphTraverser` walks each resolved graph into `SbomGraph` / `SbomComponent` / `SbomComponentId` —
   a plain, fully `Serializable` model deliberately free of Gradle types.
3. `SbomBuilder` turns that model into a `cyclonedx-core-java` `Bom`, applying task configuration (component
   coordinates, schema version, serial number) and metadata from `MavenProjectLookup` / `MavenHelper` (POM lookup,
   effective-POM resolution, licenses) and the `utils` classes (`GitUtils`, `EnvironmentUtils`,
   `ExternalReferencesUtil`).

Two invariants explain much of the odd-looking code. **Configuration cache compatibility**: tasks must not touch
`Project` at execution time, which is why coordinates arrive as `Supplier<String>`/`Provider` and why the graph model
is serializable — both tasks are `@CacheableTask`. **Classloader isolation**: `SbomGraphProvider.withPluginClassLoader`
swaps the thread context classloader around Maven-model work, which otherwise resolves against Gradle's loader.

## Project conventions worth reading before changing behavior

`CONTEXT.md` defines the domain language (**Direct SBOM**, **Aggregate SBOM**, **Contributing Project**, **Build
Environment Dependency**, **SBOM Output Contract**). Use those terms in code, docs, and commits.

`docs/adr/` holds accepted architecture decisions. Two constrain everyday changes:

- **ADR 0001** — resolved dependency graphs, not declarations or POM dependency lists, are the authority for component
  identity and relationships.
- **ADR 0004** — generated SBOMs are a public compatibility surface under semver. Changing the default schema, the
  default set of components or relationships, or a default output format/filename requires a **major** version. New
  behavior generally has to ship opt-in. Bug fixes restoring documented or valid behavior are not breaking.

`ADR 0003` explains why configuration lives directly on the tasks and there is no plugin extension: a prerequisite for
Project Isolation. Don't reintroduce a root-owned extension.

## Tests

Spock specs in `src/test/groovy` drive real builds through Gradle TestKit; `src/test/java` holds plain unit tests.
Build `TestUtils` fixtures rather than hand-rolling temp projects:

- `TestUtils.createFromString(buildContent, settingsContent)` — synthesize a build; overloads seed a fake `.git`
  remote for `GitUtils` coverage.
- `TestUtils.duplicate("<name>")` — copy a fixture from `src/test/resources/test-projects/`.
- `TestUtils.duplicateRepo("<name>")` — copy a fake Maven repo from `src/test/resources/test-repos/`.
- `TestUtils.arguments(...)` — always use for `withArguments`. It appends `--configuration-cache --parallel
  --no-watch-fs`, so specs exercise the configuration cache by default, and `--no-watch-fs` prevents the reused TestKit
  daemon from falsely reusing that cache after a spec rewrites a build script.

Specs that need the plugin resolved as a real published artifact (rather than `withPluginClasspath`) read the
`localRepoUrl` and `pluginVersion` system properties; the test tasks provide these by depending on
`publishAllPublicationsToLocalTestRepository`.

Gate specs on Java version with `@IgnoreIf`/`Assumptions` as the existing ones do — Gradle 9 requires Java 17+, and
Gradle < 9 does not run on Java 25.

## Releasing

The version lives in `build.gradle.kts` (`version = "..."`). README examples and the init-script snippet embed the
version literally, so bump those together.
