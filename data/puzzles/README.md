# Puzzle catalog data contract

This directory is reserved for a sourced, reviewable puzzle metadata catalog. Do not add guessed values or treat a syntactically valid record as verified.

## Required fields

Each record should eventually include:

- `id`: stable integer puzzle identifier.
- `bit_length`: documented puzzle size, if the source defines it.
- `range_start` and `range_end`: exact hexadecimal bounds from a cited source; inclusive/exclusive semantics must be stated explicitly.
- `public_key`: compressed or uncompressed public key exactly as sourced, or null when unavailable.
- `address`: address exactly as sourced, or null when unavailable.
- `source_url`: direct source URL for the record or source dataset.
- `verification_status`: one of `unverified`, `format_checked`, `key_address_checked`, or `source_cross_checked`.
- `notes`: optional explanation of missing or conflicting fields.

## Validation rules

1. IDs must be unique and stable.
2. Missing source fields stay null; never infer a public key, address, range, or solved status.
3. Hexadecimal fields must contain only valid hex characters and must follow the documented prefix/length convention.
4. Range bounds must parse as non-negative integers and satisfy start < end. Document whether the end is inclusive.
5. A declared bit length must be consistent with the documented range convention; flag discrepancies instead of silently changing data.
6. Where a public key and address are both present, validate their relationship with an independent, trusted implementation and record the method/version.
7. Formatting checks are not proof that a record is authentic. Preserve source URL, retrieval date, and any checksum for the source file.
8. Conflicting sources must be recorded for review; do not choose one silently.
9. The catalog UI should display verification status and must not label unverified data as confirmed.
10. Catalog selection must not automatically launch a native computation. Keep data browsing and engine execution as separate, explicit actions.

## Suggested record shape (template only)

This is a schema example, not a real puzzle record:

```json
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
```

## Before adding the real catalog

- Keep the original source file unchanged and record its SHA-256 checksum.
- Run duplicate-ID, schema, range, key-format, and address-consistency checks.
- Report record counts by verification status and by missing field.
- Review the resulting diff manually before merging.
- Never claim the catalog is complete or 100% correct based only on a passing build.
