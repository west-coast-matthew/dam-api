status: ready

# 009 — Support for lightboxes

## Summary

dam-ui is adding a selection set (a client-side "shopping cart" of assets picked while browsing) that can be downloaded as a group or saved as a lightbox (dam-ui spec 027). `/lightboxes` already has CRUD for the lightbox itself (`LightboxController` → `LightboxService`, `LightboxInstanceDto`), but there is no way to manage the assets inside a lightbox, create one from a selection, or download a group of assets. The `Lightbox` / `LightboxItem` entities (`sort_order`, `added_at`) already exist.

dam-ui will mock these endpoints for now. Its mock follows the shapes below, so keep the names and payloads as written.

## Requirements

1. **Create from a selection**: `POST /lightboxes` accepts an optional `assetIds` (list of UUIDs) on `LightboxInstanceDto`. The assets are added as items in the order given. Any unknown asset is a 404, an asset from another catalog is a 400, and duplicates in the list are ignored. `assetIds` is write-only (null in responses) and is ignored by `PUT /lightboxes/{id}`.
2. **List items**: `GET /lightboxes/{id}/items?page=&size=` returns an `AssetPageVO` of the lightbox's assets ordered by `sortOrder`, then `addedAt`. Paging works the same as `GET /assets/search` (0-based `page`, `size` 1–200, default 24).
3. **Add items**: `POST /lightboxes/{id}/items` with body `{ "assetIds": [...] }` (new `LightboxItemsVO`) appends the assets after the current last item. Assets already in the lightbox are skipped, not an error. Unknown assets are a 404 and assets from another catalog are a 400. Returns the updated `LightboxInstanceDto` (with `itemCount`).
4. **Remove items**: `DELETE /lightboxes/{id}/items/{assetId}` removes one asset (204, or 404 if it isn't in the lightbox). `POST /lightboxes/{id}/items/remove` with `LightboxItemsVO` removes several, ignoring ids that aren't in it, and returns the updated `LightboxInstanceDto`. Removing never deletes the asset itself.
5. **Reorder**: `PUT /lightboxes/{id}/items/order` with `LightboxItemsVO` sets the order. The list must contain exactly the lightbox's current assets (otherwise 400). `sortOrder` is rewritten 0..n-1.
6. **Group download**: `POST /assets/download` with `LightboxItemsVO` streams a zip (`application/zip`, `Content-Disposition: attachment; filename="assets-<yyyyMMdd-HHmmss>.zip"`) of the assets' original files, read through `AssetStorageService.open`. Duplicate file names in the zip get a ` (2)`, ` (3)` suffix before the extension. An empty list is a 400, and an unknown id is a 404 (checked before streaming starts). The size is capped by a new `dam.storage.max-download-files` setting (default 500; over the cap is a 400). `GET /lightboxes/{id}/download` does the same for a lightbox's items, in lightbox order, and uses the lightbox title (sanitized) as the zip name.
7. Keep the three-tier rules: the item and download logic goes in `LightboxService` (and a small download/zip helper service), not in controllers. Errors go through the existing exceptions.
8. Tests: service tests for create-with-assets, add/skip duplicates, cross-catalog 400, remove, reorder validation, and the zip name de-duplication. Controller tests for the new endpoints' status codes.
9. Update `CLAUDE.md` and `APPLICATION.md` with the new endpoints and the `dam.storage.max-download-files` setting.

## Acceptance criteria

- A lightbox can be created with assets, then its items listed (paged, in order), added to, removed from and reordered through the API.
- A set of assets, or a lightbox, can be downloaded as one zip.
- Invalid requests return the documented 400/404 responses as `ErrorResponseVO`.
- `mvn -B test` passes.
