# Digital Asset Management (API)

## Background

This application is intended to provide an API to an Digital Asset Management solution. Esssentially we are supporting the ability for users to store, label & categorize, and access digital media. This includes images, PDFs, and video. 


## Media storage

By default, assets will be stored on a file system. An attempt has been made to decouple the application from a specific storage location in order to support potentially storing media in a database or a remote location such as Amazon S3 buckets.

Metadata is stored in a relational database irregardless of the physical location where the assets are stored.

## Core concepts

### Workspaces
The highest level organizational unit. In most cases it is assumed you will have only a single workspace, however it is possible that an large organization would have several. 

Workspaces contain one or more catalogs.

Caution should be excercised when defining top level containers such as workspaces and catalogs as assets ultimately belong to a single catalog, and catalogs only belong to a single ttop level workspace. 

### Catalogs
A catalog is an more granular top level organizational unit. Assets may be associated in common groups within a catalog, but not associated with assets in another catalog, i.e. they are isolated within a given catalog partition.

### Asset
An asset is a piece of digital media that we are storing. This may be an image, audio file, video, or pdf. 

When assets are stored, a thumbnail is generated for it (unless it is an audio file, in which case a common default thumbnail is assigned.


### Tags
Tags are flat, non-hierarchical labels scoped to a catalog. An asset can carry any number of them, and they are matched by name ignoring case.

- Tags are assigned through the asset endpoints by name (`tags` in `AssetInstanceDto`): on a single upload (`POST /assets`), on a bulk upload (`POST /assets/bulk`, where the same tags go on every asset in the zip), and on an update (`PUT /assets/{id}`). Names are trimmed, blank names and case-insensitive duplicates are dropped, an existing tag's spelling is reused, and unknown names create a new tag in the asset's catalog. Names are limited to 255 characters.
- On update, a list replaces the asset's tags, an empty list clears them, and `null` leaves them unchanged.
- `GET /tags?catalogId=&q=&limit=` lists a catalog's tags (`TagVO`: `id`, `name`, `assetCount`) sorted by name, so clients can offer existing tags. `q` is a case-insensitive "contains" filter, and `limit` is 1–200 (default 50).
- Tags left without any assets are kept, so they stay available for selection.
- `GET /assets/search` filters by `tags` with `tagMatch=all|any`.

### Lightboxes
A lightbox is a user's saved, ordered set of assets from one catalog (a saved "shopping cart"), used to collect assets for a project, review them, or download them together.

- `/lightboxes` has CRUD for the lightbox itself (`LightboxInstanceDto`: `title`, `description`, `shared`, read-only `itemCount`). `POST /lightboxes` may also take `assetIds` to start the lightbox with those assets, in order (dam-ui saves a selection this way).
- `GET /lightboxes/{id}/items?page=&size=` pages through its assets in lightbox order (`AssetPageVO`, paged like `GET /assets/search`).
- `POST /lightboxes/{id}/items` (`{ "assetIds": [...] }`) appends assets, skipping ones already present. `DELETE /lightboxes/{id}/items/{assetId}` removes one, and `POST /lightboxes/{id}/items/remove` removes several. `PUT /lightboxes/{id}/items/order` sets the order and must list exactly the lightbox's assets.
- Assets must belong to the lightbox's catalog (otherwise 400). Removing an item or deleting a lightbox never deletes the asset.

### Group downloads
`POST /assets/download` (`{ "assetIds": [...] }`) returns the assets' original files as one zip (`assets-<yyyyMMdd-HHmmss>.zip`), and `GET /lightboxes/{id}/download` does the same for a lightbox, in its order, named after its title. Duplicate file names get ` (2)`, ` (3)`... before the extension. At most `dam.storage.max-download-files` (default 500) assets go in one zip.

## Folder based persistence

The STORAGE_SPECIFICATION.md details the persistence approach used for storing media.

