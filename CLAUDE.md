# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Digital Asset Management REST API. Java 21, Spring Boot 4.1 (Spring MVC, Spring Data JPA),
Maven, MariaDB. Base package `com.twofourseven.dam`. Original requirements are in `SETUP.md`.

## Commands

```bash
mvn -B test                         # all unit tests
mvn -B test -Dtest=HeartbeatServiceTest            # one test class
mvn -B test -Dtest=HeartbeatServiceTest#reportsUpWhenConnectionIsValid   # one test
mvn -B package                      # build the jar
bin/deploy [--skip-tests]           # build and run with /etc/app-config/dam/application.properties
bin/generate-api-docs [dir]         # static OpenAPI docs (openapi.json + index.html), default target/api-docs
```

Use the installed `mvn`. The `./mvnw` wrapper is also present but may not be able to download
Maven in sandboxed sessions.

## Architecture

Three tiers; each calls only the one below it:

- `controllers` — REST endpoints. Take and return VOs from `vo`. No business logic, no repository access.
- `services` — business logic and transactions. Convert entities to VOs.
- `repositories` — Spring Data JPA interfaces over `entities`.

Other packages:

- `vo` — request/response records. Entities are never returned from controllers.
- `exceptions` — `GlobalExceptionHandler` (`@RestControllerAdvice`) maps every exception to an
  `ErrorResponseVO` with the right status. Throw `ResourceNotFoundException` (404),
  `BadRequestException` (400) or `ConflictException` (409) from services rather than building error
  responses in controllers. Unknown exceptions become 500 with a generic message (details are
  logged, never returned).
- `config` — Spring `@Configuration` classes.
- `entities.core` — core JPA entities (Workspace → Catalog → Asset, plus Category, Tag,
  AssetVersion, Lightbox, LightboxItem, UserAssetInteraction, AssetDownloadLog). Entities use
  Lombok `@Getter`/`@Setter`; ids are `UUID`s assigned in Java. Those entities extend `AssignedIdEntity` (`Persistable`), so
  `save()` persists new objects instead of merging a copy; new UUID entities must extend it too. Workspace and Catalog names are
  required and unique (`@NotBlank`, `unique = true`). `MediaType` here is the asset enum, not
  Spring's `org.springframework.http.MediaType`.
- Startup initial data (spec 018): `config/AppInitializer` listens for `ApplicationReadyEvent` and, when
  no workspace exists, calls `AppInitializationService.initialize()` (impl `AppInitializationServiceImpl`),
  which creates workspace `default` → catalog `default catalog` → its default category `default category`.
  Failures (e.g. database down) are logged, not thrown. Off with `dam.init.enabled=false`.
- `WorkspaceInitializationService` creates the first workspace + catalog
  (`initializeDefaultWorkspace()` or `setupWorkspace(...)`).
- The schema is not generated (`spring.jpa.hibernate.ddl-auto=none`); tables must exist in MariaDB.
  Schema changes go in `db/NNN-description.sql` (numbered after the spec) and are applied by hand.

Default categories (spec 014): every catalog has one top-level default category
(`Category.isDefault`), created with the catalog via `Category.createDefaultFor(catalog)` in both
`CatalogService.create` and `WorkspaceInitializationService`. Look it up with
`CategoryService.defaultCategoryOf(catalog)` / `defaultCategory(catalogId)` (`GET /categories/default?catalogId=`),
which also creates it for older catalogs. An asset must have at least one category (`@Size(min = 1)`
on `Asset.categories`); `AssetService` puts assets without one in the default. The default can be
renamed but not deleted (409) or nested (400); deleting another category moves assets left without
a category to the default.

Category editor support (db/016): categories have an optional `description` and a 0-based sibling
`position` (column `sort_order`); `CategoryInstanceDto` also returns a read-only `assetCount`.
`POST /categories/changes?catalogId=` takes a `CategoryChangeSetVO` (`created` with client `tempId`s,
`updated`, `deleted`) and applies it in one transaction, returning the catalog's categories. There a
`parentId` is a string (an existing UUID or a `tempId`), deletes take descendants (after moves),
sibling names are unique case-insensitively at every level, and cycles are a 400. dam-ui's category
editor uses this endpoint.

REST endpoints (spec 004): `/workspaces`, `/catalogs`, `/categories`, `/lightboxes`, `/assets`, using
the `*InstanceDto` records in `vo`. Create = 201 with Location (asset uploads: 202, see below), delete = 204. Deleting a workspace,
catalog or category that still has children is a 409. Top-level category names are unique per catalog.

Assets:
- `POST /assets` is multipart: `file` plus `asset` (JSON `AssetInstanceDto`). `PUT /assets/{id}` is a
  JSON metadata update (null = unchanged); `PUT /assets/{id}/file` replaces the file.
- `POST /assets/bulk` (spec 010): multipart `file` (zip) + `asset` (JSON applied to every file). Each
  supported file in the zip, in any folder, becomes an asset; unsupported files are skipped and listed
  in `BulkUploadResultVO`. No categories given = catalog default. One transaction for the whole zip.
  Reading is `UploadedMediaReader.readEach`; capped by `dam.storage.max-bulk-files`.
- Media type comes from the extension (`MediaTypeResolver`): images, video, PDF. Audio is rejected.
  A `.zip` must hold exactly one supported media file (`UploadedMediaReader`).
- Files are written through `AssetStorageService` (`FileSystemAssetStorageService`) using the layout
  in `STORAGE_SPECIFICATION.md`; the database stores paths relative to the workspace folder.
- When `Asset.versioningEnabled` is true, replacing the file keeps the old one as an `AssetVersion`
  (`version_N.ext`); otherwise it is overwritten. File clean-up is tied to the transaction outcome
  via `TransactionCallbacks`.

Thumbnails (spec 001), package `services.thumbnails`: `ThumbnailGenerationService` (impl
`ThumbnailGenerationServiceImpl`) takes bytes or a stream plus a file name and returns a `Thumbnail`
(JPEG bytes); it doesn't store anything. The work is done by pluggable `ThumbnailGenerator` beans; the
first supporting one in `@Order` order wins, so a deployment overrides a built-in with a higher-precedence
bean. Built-ins: `ImageMagickImageThumbnailGenerator` (jpg/jpeg/gif/png/svg/bmp/psd),
`ImageMagickPdfThumbnailGenerator` (first page; needs Ghostscript), `FfmpegVideoThumbnailGenerator`
(avi/mkv/mp4/mov, frame at `dam.thumbnails.video-frame-offset`, first frame if the video is shorter) and
`StaticAudioThumbnailGenerator` (`resources/thumbnails/audio.jpg`). Tools run through `CommandRunner`
(no shell, timeout), which tests mock. Settings: `ThumbnailProperties` (`dam.thumbnails.*`).

Thumbnail processing (spec 019): `POST /assets` (202 + Location) and `POST /assets/bulk` (202) return once the
assets are saved as `PENDING_PROCESSING`; `PUT /assets/{id}/file` also re-queues. `AssetService` calls
`AssetThumbnailService.generateAfterCommit(ids)`, which queues the ids after commit on its own pool of
`dam.thumbnails.threads` workers (owned by the service, not an `Executor` bean, so Boot's default task executor
stays). A worker reads the original via `AssetStorageService.open`, stores `thumbnail.jpg`
(`AssetStorageService.thumbnailPath`) and sets READY/FAILED with `AssetRepository.updateStatusIfFileUnchanged`
(skipped if the file was replaced meanwhile). Types without thumbnail support go to READY. The queue is in
memory: assets left PENDING_PROCESSING by a restart are not retried.

Upload activity (spec 020): `GET /assets/activity?categoryId=&period=&limit=` returns `AssetUploadActivityVO`
for a category and its whole subtree: upload operations, totals, newest assets and one entry per direct child
(`subCategories`). `period` is parsed with Boot's `DurationStyle` (`15m`, `8h`, `7d`, `PT15M`; default 24h, max
366d); `limit` is 1–100 (default 10). Assets from one upload request share `Asset.uploadBatchId`
(`db/020-asset_upload_batch.sql`; null for older assets, which count as their own operation); a batch with more
than one media type has file type `mixed`. The SQL is native MariaDB (recursive CTEs) in
`repositories/AssetActivityRepository`, which reads UUIDs with `addScalar(..., UUID.class)`; it isn't covered by
unit tests.

Upload summary (spec 021, for dam-ui's recent uploads panel): `GET /assets/activity/summary?catalogId=&categoryId=&period=`
returns `AssetUploadSummaryVO`, the window broken down by category: only categories with uploads, in editor order
(depth-first by position), each with its `path`/`pathIds` and its own `uploadEvents` (assets directly in it, so a
parent doesn't repeat its children). `catalogId` covers the whole catalog; `categoryId` narrows it to that subtree
(the catalog is taken from it; both given must match). Totals count each asset and operation once.

Asset browsing (spec 022, for dam-ui's browse panel): `GET /assets/search?catalogId=` returns an `AssetPageVO`
(`items`, `page`, `size`, `totalItems`, `totalPages`), filtered by `categoryId` (+ `includeSubcategories`),
`addedFrom`/`addedTo` (ISO days, inclusive), `tags` (repeated or comma-separated; `tagMatch=all|any`) and `q`
(file name). Paging is 0-based, `size` 1–200 (default 24), `sort` added|name|size|type (newest/oldest still accepted) with
`direction` asc|desc (default: added and size descending, name and type ascending). `AssetBrowseService` builds
JPA `Specification`s (`repositories/AssetSpecifications`, EXISTS subqueries so paging counts stay right).
`GET /assets/{id}/file[?download=true]` streams the original; `GET /assets/{id}/thumbnail` the JPEG thumbnail
(404 until it exists).

`GET /heartbeat` returns `HeartbeatVO`: 200 when every check is UP, 503 otherwise. It currently
checks the database connection. New dependency checks go in `HeartbeatService.check()`.

## Configuration

- `src/main/resources/application.properties` — bundled defaults, no credentials. The app starts
  even when the database is down (Hikari `initialization-fail-timeout=-1`, Hibernate JDBC metadata
  access off with an explicit MariaDB dialect) so `/heartbeat` can report it. Keep that working.
- Database connection is set with `dam.db.server`, `dam.db.port`, `dam.db.name`, `dam.db.username`,
  `dam.db.password`; `application.properties` builds `spring.datasource.*` from them. They're bound
  and validated by `config/DatabaseProperties`, so startup fails if one is blank. Its `toString()`
  hides the password; keep it that way.
- Credentials come from `/etc/app-config/dam/application.properties`, layered on top through
  `--spring.config.additional-location`. Template: `config/application.properties.sample`.
- Never commit real credentials. `config/application.properties` is gitignored.
- Asset files live under `dam.storage.root-dir` (`StorageProperties`); directories are created on
  first write. Upload limits: `spring.servlet.multipart.*`, `dam.storage.max-extracted-size` (per file inside a
  zip) and `dam.storage.max-bulk-files`.
- Logging: console only (`logback-spring.xml`); `com.twofourseven.dam` at DEBUG. Use SLF4J loggers.

## Testing

- Unit tests must not need a database or a running Spring context. Use
  `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks` to mock the tier below.
- Controller tests: `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())`
  (see `HeartbeatControllerTest`).
- Test packages mirror main packages.
- API docs (spec 013): `docs/ApiDocsGenerator` (JUnit tag `api-docs`) loads the context with a mock
  web environment and writes the springdoc spec + a Redoc page. It only runs with `-Papi-docs`.
  springdoc is test scope, so controllers carry no OpenAPI annotations; status codes and error
  responses come from `docs/ConventionalResponses` (201 for POST create, 204 for DELETE, errors as
  `ErrorResponseVO`) and titles from `docs/ReadableOperations`. Update those if the conventions change.

## Image processing

Image operations shell out to command line tools that are already installed (ImageMagick
`magick`/`convert` is at `/usr/local/bin`). Do not add Java image-processing libraries to replace them.

## Specs workflow

Specs use the global `spec` skills (`spec-create`, `spec-checkout`, `spec-list`, `spec-run`,
`spec-commit`, `spec-push`, `spec-cancel`); see the `spec` skill for conventions. Specs live in
`spec/NNN_lower_case_title.md`, work happens on `spec_NNN_title` branches merged into `develop`.
Only set a spec to `completed` after its work is done and `mvn -B test` passes.
