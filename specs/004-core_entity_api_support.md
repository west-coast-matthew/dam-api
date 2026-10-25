# core entity api support

**Status:** completed

## Goal

We have defined an initial set of JPA entities. we need at this point to establish a few initial rounds of REST apis for clients to consume in this immediate effort.  

## Requirements

### workspace entity
create a crud api for this entity.
create a new dto called WorkspaceInstanceVDto with the minimal attributes 'name', 'description', etc. no relations will be passed in the payload.

### catalog entity
implement a management api in a similar fashion as the workspace endpoint, using CatalogInstanceDto

### category entity
implement a management endpoint in a similar fashion as the above two, using CategoryInstanceDto as the dto object used in the payload

### lightbox entity
add a management endpoint for this

### asset entity
add a nmaanagement endpoint for this
we need to honor the versioning flag for this api call, and when enabled for a given asset, updates should reflect an versioning strategy where a new copy is created reflecting the updates.
we should use an somewhat denormalized dto named AssetInstanceDto in order to manage the payload between a given client and this endpoint. assets may be either video, popular image formats (jpeg, svg, webp, etc) or pdf files. there is no support for audio at this time. we can derive the media type based on the file extension, however the client may supply the media in a zip format.
modify the category name is unique within a given category

## Notes

Decisions made when running this spec:

- DTO for workspaces is named `WorkspaceInstanceDto` (the requirement text had `WorkspaceInstanceVDto`).
  Lightboxes use `LightboxInstanceDto` to match the others.
- The versioning flag is a new `Asset.versioningEnabled` column (default false); see
  `db/004-asset_versioning_enabled.sql`. When it is on, replacing the file keeps the previous file
  as an `AssetVersion` row and the asset keeps its id. Metadata-only updates do not create versions.
- File upload is handled here (multipart), stored per `STORAGE_SPECIFICATION.md`. A zip must contain
  exactly one image, video or PDF; bulk upload is left to spec 010.
- Category names must be unique among the top-level categories of a catalog.
- Lightbox items are left to spec 009. Until spec 006 adds users, `userId` is supplied by the client.

