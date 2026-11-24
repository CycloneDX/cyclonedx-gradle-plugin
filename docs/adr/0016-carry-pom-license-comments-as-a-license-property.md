---
status: accepted
---

# Carry a POM license's comments as a license property

The Maven model defines a POM's license list as a choice between its entries, and a `<comments>` element on one entry
can say otherwise: `net.logstash.logback:logstash-logback-encoder` lists Apache-2.0 and MIT, and the MIT entry's
comment states that it covers code copied from slf4j, so both apply. The CycloneDX license object has no field for that
text, and the plugin never read it, so the document carried two licenses and nothing else, and a consumer that turns the
list into an SPDX expression wrote `Apache-2.0 OR MIT`, a choice the POM does not offer, with no way to tell. Each
**License Comment** is now written as the `cdx:maven:license:comments` property of the license object read from that
entry, with its whitespace collapsed to single spaces, and is never interpreted: the plugin cannot decide whether a
comment changes what the list means, and no tool can, so the text reaches the reader instead of being lost. The
newlines and indentation inside the element belong to the POM file, not to the statement; cyclonedx-core-java's JSON
generator already collapses them in every string it writes, and collapsing in the plugin makes the XML output and the
task's enrichment input agree with it.

The name is registered in the CycloneDX property taxonomy, under a new `cdx:maven:license` namespace, because the
taxonomy prohibits unofficial names under `cdx` and the text describes Maven metadata whoever produces the document.

## Considered options

- **Read the comment and write an `AND` expression.** The text is free prose in any language, and a wrong reading
  states a license obligation the POM never made. The document reports evidence; concluding from it is the reader's.
- **Keep dropping it.** That is the failure this decision removes: the only statement in the POM that qualifies the
  list was the one the document could not carry.
- **A property on the component.** The registered `cdx:maven:package` namespace would have served, but a POM with
  several licenses then has one text with no entry to belong to.
- **`expressionDetailed` properties on schema 1.7.** Would give a comment a home on an entry that resolves to an SPDX
  expression, at the price of changing the 1.7 output from `expression` to `expressionDetailed` for every document.
- **An opt-in switch.** A permanent public task property for text that is already public in the POM, and a document
  without commented licenses is unchanged either way.

## Consequences

- The property appears only where a POM license has a non-blank comment and metadata resolution is on, so other
  documents are unchanged. Under ADR 0004 that is optional metadata valid under the configured schema, which a minor
  version may add. The name is part of the **SBOM Output Contract**; renaming it is a major version.
- The value is prose for a reader. A consumer displays it and never parses it.
- An entry that resolves to an SPDX expression carries no comment, since expressions have no properties in any
  schema version the plugin writes through `expression`. Three names in cyclonedx-core-java's table resolve that way.
- Schema versions before 1.5 have no license properties; the serializer drops the comment there without a log line,
  as it drops every other field those versions lack.
- The comment is part of the component's metadata enrichment input, so a changed comment makes the task out of date,
  like a changed license name or URL.
