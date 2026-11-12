---
status: accepted
---

# Report what the SBOM could not describe

A component whose POM cannot be obtained, read, or completed from its parent produced exactly the same SBOM entry as one
that resolves and declares nothing: no licenses, no publisher, no error. `MavenProjectLookup` returned `null` from four
separate paths, three of them without a log line, and the consumer of the document had no way to tell **Unresolved
Metadata** from an unlicensed component. Such a component now carries a `cdx:maven:package:metadata-unresolved` property
naming what happened, and the reason Gradle or Maven reported goes to the log at warning level.

| value | what happened |
|---|---|
| `pom-unresolved` | the repositories returned no usable POM artifact |
| `pom-unparseable` | a POM was returned and could not be read |
| `parent-unresolved` | the POM was read, and the failure identifies the parent it declares |
| `model-incomplete` | the effective model failed, and which of its inputs failed is not established |

The property lives under `cdx:maven` because it describes Maven metadata whoever reads it, and the name is registered in
the CycloneDX property taxonomy, which prohibits unofficial names under `cdx`.

Where a POM was read, what it declares is reported even when the effective model could not be completed. Maven replaces
the inherited list instead of merging into it: a component that states its own licenses does not inherit any, so those
statements are complete on their own, and discarding them because a parent or an import failed loses information the
build had in hand. Inherited fields are the ones at risk, which is what the property exists to disclose.

Maven does not report which of the model's inputs failed, so a failing parent and a failing import are indistinguishable
from the exception alone. **The parent is named only where the reported failure carries the coordinates the POM declares
for it**, which is what Gradle's own resolution failure does for a parent the repositories do not hold. Every other model
failure is recorded as `model-incomplete`, including an unreadable import and a parent whose POM was obtained and
rejected. The value reports an observation, never an inference: these values are part of the **SBOM Output Contract**,
and a consumer acts on them without access to the build that produced them.

A document that understates the build can also be produced without any metadata failing: a declared dependency that
does not resolve is dropped by the lenient artifact view, and the component is absent altogether, with an `info` line as
its only trace. That line is now a warning, since the document itself cannot carry the fact.

## Considered options

- **Emit a `NOASSERTION` license.** Neither the 1.6 nor the 1.7 schema defines that value, and writing a license the
  component does not claim states something it never said. It also speaks only to licenses, while the same failure
  removes the publisher and the external references too.
- **Log only.** The log stays on the machine that generated the document, which is not the machine that reads it, and a
  cached or published SBOM travels without it. That is the failure mode this decision exists to remove.
- **Restrict the property to components where a POM was expected.** The plugin cannot make that distinction: a module
  resolved from an artifact-only repository and a module whose POM the repository failed to return are the same
  observation, an unresolved POM artifact.
- **Name the parent whenever a component declares one.** A component with a healthy parent and an unreadable import is
  then recorded as a parent failure, which the evidence does not support. A value that claims more than what was
  observed is worse than a general one, because the document is what the consumer acts on.
- **Resolve the parent a second time to establish the cause.** That would tell a failing parent from a failing import in
  every case, at the price of another resolution on the failure path, which can reach the network. The failure already
  carries the parent's coordinates in the case that matters, so the cost buys little.
- **Discard what was read when the model fails.** That was the previous behavior, and it threw away declarations the
  component makes for itself, which Maven does not inherit and which are therefore complete without the model.
- **Fail the build when evidence is missing.** Fail-fast suits a document whose purpose is completeness. But a component
  with no POM is a legitimate state, indistinguishable here from a repository failure, so failing would break builds that
  are configured correctly. Whether a build may opt into failing is a separate decision, not part of reporting.
- **Represent an unresolvable dependency as a component in the document.** That changes the default set of included
  components, which ADR 0004 reserves for a major version, and it contradicts ADR 0001, where the resolved graph is the
  authority for what the document contains.

## Consequences

- The property appears only when metadata resolution failed, so healthy builds produce unchanged documents. Under
  ADR 0004 that is optional metadata valid under the configured schema, which a minor version may add.
- The value set is part of the **SBOM Output Contract**. Adding a case is additive; renaming one is not.
- A component whose POM was read now keeps what that POM declares where the effective model fails, which previously
  produced an empty license list. Under ADR 0004 that is a correction restoring valid output.
- The failure reason itself is not written to the document. It is free-form text that can carry local paths, and it
  belongs in the build log.
- `parent-unresolved` depends on the failure naming the parent. Where a future Gradle release reports that failure
  differently, the value falls back to `model-incomplete`, which understates the cause and never misstates it.
