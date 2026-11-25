status: completed

# 023 — Asset tagging support

## Summary

Users need to associate tags with assets in three places: when uploading a single asset, during bulk upload (the selected tags are applied to every asset in the batch), and when editing a single asset (for example from the browse panel). The `Tag` entity (catalog-scoped, linked to assets through `asset_tags`) is already in place, and `AssetInstanceDto.tags` is already accepted by `POST /assets`, `POST /assets/bulk` and `PUT /assets/{id}`, with unknown names created in the asset's catalog. `GET /assets/search` already filters by `tags` / `tagMatch`.

What is missing is a way for the UI to find out which tags exist in a catalog, so it can offer them for selection and autocomplete instead of free text only. This spec adds that endpoint and makes sure the existing tag behavior is covered by tests and documented.

## Requirements

1. Add a `TagController` mapped to `/tags` with `GET /tags?catalogId={uuid}&q={text}&limit={n}`:
   - `catalogId` is required; return 404 (`ResourceNotFoundException`) if the catalog doesn't exist.
   - `q` is optional and does a case-insensitive "contains" match on the tag name.
   - `limit` is optional (default 50, maximum 200); values outside 1..200 return 400 (`BadRequestException`).
   - Return a list of `TagVO(UUID id, String name, long assetCount)` sorted by name, ignoring case. `assetCount` is the number of assets linked to the tag.
2. Add a `TagService` for the query, and the repository method(s) it needs on `TagRepository` (a JPQL/projection query that counts assets per tag, avoiding N+1 loads).
3. Tag assignment on assets:
   - `POST /assets` and `POST /assets/bulk` apply `tags` to the new asset(s); for bulk, every asset in the batch gets the same tags, and each tag is resolved or created once.
   - `PUT /assets/{id}` with `tags` replaces the asset's tags. An empty list clears them, and `null` leaves them unchanged.
   - Tag names are trimmed. Blank names are dropped, duplicates are removed ignoring case, and the existing spelling of a matching tag is reused. Names longer than 255 characters are rejected with 400.
4. Tags left without any assets are kept, not deleted. They stay available for selection.
5. Update `APPLICATION.md` (and the OpenAPI docs, if they are generated from annotations) to describe the `/tags` endpoint and the tag semantics on create, bulk and update.

## Acceptance criteria

- `GET /tags?catalogId=…` returns the catalog's tags sorted by name, with correct `assetCount` values, and only for that catalog.
- `q` filters case-insensitively, `limit` is enforced, an unknown catalog returns 404, and an invalid limit returns 400.
- Controller tests (`TagControllerTest`) and service tests (`TagServiceTest`) cover the above.
- `AssetServiceTest` covers updating tags (replace, clear with an empty list, leave unchanged with null) and the trimming/de-duplication rules.
- `./mvnw test` passes.
