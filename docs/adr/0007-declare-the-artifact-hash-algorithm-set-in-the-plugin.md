---
status: accepted
---

# Declare the artifact hash algorithm set in the plugin

`cyclonedx-core-java` 12.1.0 rewrote `BomUtils.calculateHashes`. Where 11.0.1 acquired each SHA3 digest inside a
`catch (Exception | NoSuchMethodError)` annotated "Not available in Java 8", the replacement throws
`IllegalArgumentException("Algorithm not available: SHA3-256")`. Java 8 ships no SHA3 provider, so on a Java 8 **build
JVM** every artifact hashed now aborts `cyclonedxDirectBom`. Rather than inherit Core's default algorithm list, the
plugin passes an explicit list to the three-arg `calculateHashes(File, Version, List<Hash.Algorithm>)` overload
introduced by that same release, filtering the SHA3 family by a `MessageDigest.getInstance` probe so a Java 8 build
JVM produces exactly the hash set 11.0.1 produced there.

## Considered options

- **Wait for upstream.** [cyclonedx-core-java#848](https://github.com/CycloneDX/cyclonedx-core-java/issues/848) has
  been open since 2026-06-12; the report's own framing is "I'm not sure it is worth the effort. Maybe you should just
  update the docs." Core's POM still sets `maven.compiler.release=8` and its classes are major 52, so upstream
  declares Java 8 support in its build and contradicts it in its hash path. Blocking a release on that reconciliation
  was rejected.
- **Widen the `catch` in `SbomBuilder`.** The exception aborts the whole call, so Java 8 SBOMs would lose *every*
  hash, not just SHA3 — an **SBOM Output Contract** regression under ADR 0004, and a larger one than the bug.
- **Bundle a pure-Java SHA3 provider.** Would make output identical across build JVMs, but adds a heavyweight runtime
  dependency to a plugin whose subject is dependency hygiene, and *adds* hashes to Java 8 output that 11.0.1 never
  emitted.
- **Keep inheriting Core's defaults, degrading only on failure.** Rejected because the fallback needs the same
  explicit list anyway, and because it would let a Core patch bump silently move the **SBOM Output Contract**.

## Consequences

- The algorithm set is now the plugin's declared output, not Core's. A future Core release that adds an algorithm to
  its defaults will not reach users until the plugin adopts it deliberately — which is what ADR 0004 requires of a
  change to parsed SBOM meaning.
- Only the SHA3 family is probed. `MD5`, `SHA-1`, `SHA-256`, `SHA-384` and `SHA-512` are requested unguarded, so a JVM
  lacking them (FIPS mode) still fails loudly exactly as 11.0.1 did, rather than silently emitting an SBOM whose
  missing hashes are indistinguishable from ones the schema never asked for.
- Selecting the list carries a schema-version conditional (`>= 1.2` adds `SHA-384` and `SHA3-384`), making it the
  third `org.cyclonedx.Version` branch ADR 0006 anticipated. It is written here as a direct comparison and folded into
  the internal `SchemaVersion` type when that type lands, per ADR 0006's own rule.
- Because the list is plugin-owned, it needs a plugin-owned test. The selection is exposed through a seam taking an
  availability predicate so the degraded path is provable on any JVM, instead of relying on `GradleVersionsSpec` under
  `testJava8` — a signal that only fires in a full-matrix run and reports Core's error rather than the plugin's intent.
- Writing that test surfaced that `junit-jupiter-engine` was absent from the test runtime classpath, so no plain JUnit 5
  test under `src/test/java` had ever been discovered. Adding the engine was a prerequisite for this guard to run at
  all, and incidentally activated `DependencyUtilsTest` and `EnvironmentUtilsTest`, which pass.
