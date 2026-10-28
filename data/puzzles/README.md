# Puzzle catalog data contract

This directory is reserved for a sourced, reviewable puzzle metadata catalog. Do not add guessed values or treat a syntactically valid record as verified.

## Required fields

Each record should eventually include:

- \`id\`: stable integer puzzle identifier.
- \`bit_length\`: documented puzzle size, if the source defines it.
- \`range_start\` and \`range_end\`: exact hexadecimal bounds from a cited source; inclusive/exclusive semantics must be stated explicitly.
- \`public_key\`: compressed or uncompressed public key exactly as sourced, or null when unavailable.
- \`address\`: address exactly as sourced, or null when unavailable.
- \`source_url\`: direct source URL supporting the record's specific claim; keep null when unavailable.
- \`source_scope\`: \`record\` only when a record-specific HTTPS source URL and label exist; otherwise \`missing\`. Dataset-wide links never qualify.
- \`source_label\`: describe exactly what the linked record source supports.
- \`dataset_source_url\` and \`dataset_source_snapshot\`: secondary catalog-wide comparison and its snapshot date; not per-record proof and never a replacement for \`source_url\`.
- \`dataset_record_url\`: a secondary per-puzzle atlas page for review convenience; its URL and contents are not treated as a primary source or execution approval.
- \`chain_tx_url\` and \`chain_tx_url_secondary\`: transaction-explorer links for a recorded spend, not proof of solver identity or live balance.
- \`verification_status\`: one of \`unverified\`, \`format_checked\`, \`key_address_checked\`, or \`source_cross_checked\`.
- \`notes\`: optional explanation of missing or conflicting fields.

## Validation rules

1. IDs must be unique and stable.
2. Missing record-level source fields stay null; never fill them with a generic catalog URL. Keep secondary dataset references in their separate fields.
3. Hexadecimal fields must contain only valid hex characters and may use an optional \`0x\` or \`0X\` prefix.
4. When range bounds are supplied, both must be supplied. If \`range_end_inclusive\` is true, the interval is \`[start, end]\` and start may equal end. If false, the interval is \`[start, end)\` and start must be less than end. If bounds are unavailable, both bounds and \`range_end_inclusive\` must be null.
5. When \`bit_length\` is supplied, the scalar interval must fit \`[2^(bit_length-1), 2^bit_length)\`. An inclusive end must be below the upper bound; an exclusive end may equal it. Discrepancies are errors, not values to silently repair.
6. Where a public key and address are both present, validate their relationship with an independent, trusted implementation and record the method/version.
7. Formatting checks are not proof that a record is authentic. Preserve per-record source URLs separately from dataset-wide cross-references, retrieval date, and any checksum. A chain transaction link proves only the transaction facts visible on-chain.
8. Conflicting sources must be recorded for review; do not choose one silently.
9. The catalog UI should display verification status and must not label unverified data as confirmed.
10. Catalog selection must not automatically launch a native computation. Keep data browsing and engine execution as separate, explicit actions.

## Suggested record shape (template only)

This is a schema example, not a real puzzle record:

\`\`\`json
{
  "id": null,
  "bit_length": null,
  "range_start": null,
  "range_end": null,
  "range_end_inclusive": null,
  "public_key": null,
  "address": null,
  "source_url": null,
  "retrieved_on": null,
  "verification_status": "unverified",
  "notes": "Template only; replace only with source-backed data."
}
\`\`\`

## Before adding the real catalog

- Keep the original source file unchanged and record its SHA-256 checksum.
- Run duplicate-ID, schema, range, key-format, and address-consistency checks.
- Report record counts by verification status and by missing field.
- Review the resulting diff manually before merging.
- Never claim the catalog is complete or 100% correct based only on a passing build.
