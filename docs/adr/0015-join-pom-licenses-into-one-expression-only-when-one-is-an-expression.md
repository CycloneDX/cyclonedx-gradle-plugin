---
status: accepted
---

# Join a POM's licenses into one expression only when one of them is an expression

`MavenHelper.resolveMavenLicenses` turns the `<license>` entries of a component's POM into the component's licenses.
Each entry is resolved through `cyclonedx-core-java`'s `LicenseResolver`, by name and then by URL; an entry with a
name that resolves to neither is kept as a license with that name and URL, and an entry without a name is left out.
Most entries resolve to a license object with an SPDX identifier. A few names in Core's mapping, such as "CDDL + GPLv2
with classpath exception", resolve to an SPDX expression, and any mapping that needs a license exception has to,
because an exception is not a license identifier. A CycloneDX 1.6 license list holds license objects or exactly one
expression, never both. Until now an expression replaced every license resolved before it, so the component lost those
licenses, and a license resolved after it was appended beside it, so the document failed the plugin's own schema
validation and the task failed with it.

When one entry resolves to an expression and the POM lists others, and every entry resolved to an SPDX identifier or
expression, the licenses are now written as one expression joining the entries with `OR`. The Maven POM reference
reads several licenses as alternatives: "If multiple licenses are listed, it is assumed that the user can select any
of them, not that they must accept all." The terms keep the declaration order and the form Core returned them in,
parentheses included, and a term equal to an earlier one is dropped. When an entry resolved to nothing but its name,
the list stays license objects, since free text cannot appear in an expression, and the expression is written as a
license whose name is the expression text. A single entry, and a list that resolves to license objects only, keep
their shape. The same rule applies under every schema version, although 1.7 allows objects and expressions in one
list, so that a document means the same whichever schema the build selects.

Every license read from a POM is a **Declared License** and is tagged `acknowledgement: declared`, which the 1.6
schema defines as the initial intentions of the authors regarding the licensing terms of their code. The license of
the main component configured through the task's `licenseChoice` is the build author's statement, not the POM's, and
is left as configured.

## Considered options

- **Join every list that resolves completely, license objects included,** by default or behind a task option. A list
  of license objects is valid and states nothing about whether its licenses all apply or are a choice; `OR` states a
  choice, and some POMs list licenses that all apply. Joining such a list by default changes valid output, which ADR
  0004 reserves for a major version, and an option is task API that the 3.x line would have to keep. Where an
  expression is present, joining is the only way to keep every license in a valid 1.6 document.
- **Split the expression into license objects.** An expression with `WITH` cannot be split, and splitting `OR` would
  drop the choice it states.
- **Put an unresolved name into the expression as a `LicenseRef-`.** The name would have to be reduced to the
  characters an SPDX reference allows, and a 1.6 document has no place to define what the reference stands for.
- **Keep the objects whenever `includeLicenseText` is set,** so their text survives. The shape of the licenses would
  then depend on an unrelated setting, and the builds that asked for the most detail would lose the expression.
- **Keep each entry as its own item under 1.7.** Nothing would be lost there, but the meaning of the document would
  depend on the schema version, and the helper that resolves licenses would need to know it.

## Consequences

- Under ADR 0004 the joining is a correction: it restores licenses that were dropped and a valid document where the
  task failed, so it ships in a minor version. The acknowledgement is optional metadata valid under the configured
  schema; Core omits it below 1.6.
- A joined expression carries no license URL and no license text, since an expression has neither, and the
  `includeLicenseText` documentation says so. Both follow from the SPDX identifiers in the expression.
- The plugin does not rewrite what Core returns. A deprecated SPDX identifier in Core's mapping lands in the expression
  as it is; correcting it belongs to Core's mapping.
