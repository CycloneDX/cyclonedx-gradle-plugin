---
status: accepted
---

# Own the properties DSL with plugin types, not CycloneDX model types

The `metadataProperty` / `componentProperty` DSL is expressed through a plugin-owned `PropertySpec` interface and a
serializable `PropertyDto` carrier, rather than exposing `org.cyclonedx.model.Property` on the task surface. `PropertyDto`
implements `equals`/`hashCode` and is snapshotted as an `@Input`, so a changed property name or value correctly
invalidates the task and its configuration-cache entry. The CycloneDX model type is constructed only at build time, inside
`DslUtils`.

This deliberately diverges from the existing `externalReferences` input, which exposes the non-serializable
`org.cyclonedx.model.ExternalReference` and is therefore marked `@Internal` — meaning changes to it do not participate in
up-to-date checks. Reusing the CycloneDX model type here would reintroduce that gap; a plugin-owned serializable DTO
avoids it and keeps the user-facing surface independent of the `cyclonedx-core-java` library.

## Consequences

- The DSL surface for properties is a plugin-owned compatibility surface under [ADR 0004](0004-version-the-sbom-output-contract-with-the-plugin.md); `PropertySpec` and the `metadataProperty` / `componentProperty` blocks are covered accordingly.
- `PropertyDto` duplicates the shape of `org.cyclonedx.model.Property` and requires a small conversion in `DslUtils`. This duplication is intentional — it is what buys serializability and correct cache invalidation — and should not be "simplified" back to the model type.
- `SbomMetaData` stores POM-sourced properties as `SbomMetaData.SerializableProperty`, a thin `Serializable` wrapper around `org.cyclonedx.model.Property`, following the same pattern as `SbomMetaData.ExternalReference`. `org.cyclonedx.model.Property` is not serializable and cannot be held directly in the configuration-cache-serialized graph.
- This establishes the pattern (plugin-owned spec interface + serializable DTO + `@Input`) that further DSL work is expected to follow. Aligning the existing `externalReferences` input to it is anticipated but deferred: it is a breaking change to user scripts and is left for a future major release. This ADR does not commit that work.
