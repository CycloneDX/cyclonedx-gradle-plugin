# Migrating from plugin 3.x to 4.0

Plugin 4.0 changes how multi-project builds are aggregated. Plugin 3.x was applied once, usually to the root project;
it registered `cyclonedxDirectBom` in that project and every subproject, and `cyclonedxBom` aggregated all of them.
Plugin 4.0 follows the model of Gradle's `jacoco-report-aggregation` and `test-report-aggregation` plugins:

- each project that should produce a Direct SBOM applies the plugin itself; and
- the Aggregating Project declares its Contributing Projects explicitly on `cyclonedxAggregation`.

The change makes the plugin compatible with Gradle's
[Project Isolation](https://docs.gradle.org/current/userguide/isolated_projects.html), and it makes the contents of an
Aggregate SBOM exactly what the build declares. The reasoning is recorded in
[ADR 0011](adr/0011-aggregate-only-explicitly-declared-members.md).

Nothing else about the documents changes: the task names, output locations, formats, schema defaults, and component
identities are the same as in 3.x.

## What stops working

After upgrading a 3.x setup without changing it:

| 3.x setup | 4.0 behavior |
|-----------|--------------|
| Plugin applied only to the root project | Subprojects get no `cyclonedxDirectBom` task. |
| `./gradlew cyclonedxBom` | The root's `cyclonedxBom` declares no members, so it is **skipped** and writes no Aggregate SBOM. |
| `allprojects { tasks.named("cyclonedxDirectBom") { ... } }` in the root | Fails, because subprojects do not have the task unless they apply the plugin. |
| `tasks.cyclonedxDirectBom { enabled = false }` to exclude a project | No longer the way to exclude a project. If the project is declared as a member, aggregation fails. |
| Single-project build running `cyclonedxBom` | Skipped until the project declares itself as a member. |

Check for the skip: the console shows `cyclonedxBom SKIPPED` with `--console=plain`, and `--info` logs the reason.
A CI step that consumes `build/reports/cyclonedx/bom.json` from a clean workspace fails because the file is absent.

## Migrate a multi-project build

### 1. Apply the plugin to every Contributing Project

Add the plugin to each project whose Direct SBOM should be generated, for example in `app-a/build.gradle.kts`:

```kotlin
plugins {
    id("org.cyclonedx.bom") version "4.0.0"
}
```

When many projects need it, apply the plugin from a
[convention plugin](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html) that those
projects already use. A version declared once in the root `plugins` block with `apply false`, or in a version catalog,
keeps the projects on the same version.

### 2. Declare the members on the Aggregating Project

In the project that produces the Aggregate SBOM, usually the root:

```kotlin
plugins {
    id("org.cyclonedx.bom") version "4.0.0"
}

dependencies {
    cyclonedxAggregation(project(":"))      // only if the root's own dependencies belong in the SBOM
    cyclonedxAggregation(project(":app-a"))
    cyclonedxAggregation(project(":app-b"))
}
```

Groovy DSL:

```groovy
dependencies {
    cyclonedxAggregation project(':')
    cyclonedxAggregation project(':app-a')
    cyclonedxAggregation project(':app-b')
}
```

In 3.x the Aggregate SBOM always contained the root project's Direct SBOM. In 4.0 the Aggregating Project is a
Contributing Project only when it declares itself. Declare it when its build script declares dependencies that belong
in the Aggregate SBOM; leave it out when it is only a container for subprojects. The Aggregate SBOM's main component
always describes the Aggregating Project either way.

### 3. Move cross-project configuration into the projects

Replace `allprojects` or `subprojects` blocks that configure `cyclonedxDirectBom` with configuration in each project
or in the convention plugin from step 1. For example, the 3.x root script

```kotlin
allprojects {
    tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
        includeConfigs = listOf("runtimeClasspath")
    }
}
```

becomes, in the convention plugin or in each project:

```kotlin
tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs = listOf("runtimeClasspath")
}
```

With Project Isolation disabled, an `allprojects` block that first applies the plugin, with
`apply(plugin = "org.cyclonedx.bom")`, and then configures the task still works. It is no longer the recommended
pattern.

### 4. Replace task disabling with membership

To keep a project out of the Aggregate SBOM, do not declare it. In 3.x a disabled `cyclonedxDirectBom` task excluded
its project; in 4.0, disabling the task of a declared member makes `cyclonedxBom` fail, so a stale file from an earlier
build can never be aggregated in its place.

### 5. Run the root's aggregation explicitly

Every project that applies the plugin has a `cyclonedxBom` task, which is skipped when the project declares no members.
Run the Aggregating Project's task by path to avoid scheduling the others:

```shell
./gradlew :cyclonedxBom
```

## Migrate a single-project build

If you use `cyclonedxDirectBom`, nothing changes. If you use `cyclonedxBom`, declare the project as its own member:

```kotlin
dependencies {
    cyclonedxAggregation(project(":"))
}
```

For a single project, the Direct SBOM is usually the better fit, because its boundary is exactly that project.

## Migrate an initialization script

A 3.x initialization script that applied the plugin to `rootProject` produced a whole-tree Aggregate SBOM. In 4.0 the
script has to apply the plugin to every project and declare each one as a member. The README's
[initialization script](../README.md#apply-the-plugin-from-an-initialization-script) does both. Because it configures
every project from the root, it runs only with Project Isolation disabled.

## Adopt Project Isolation

Once each project applies the plugin itself and no build logic configures other projects, the build can run with
`-Dorg.gradle.unsafe.isolated-projects=true`, or `org.gradle.unsafe.isolated-projects=true` in `gradle.properties`. The
plugin is tested this way on Gradle 8.11 and newer. Project Isolation is incubating in Gradle; see Gradle's
documentation for its current status and the other plugins your build depends on.
