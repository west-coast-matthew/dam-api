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
  Lombok `@Getter`/`@Setter`; ids are `UUID`s assigned in Java. Workspace and Catalog names are
  required and unique (`@NotBlank`, `unique = true`). `MediaType` here is the asset enum, not
  Spring's `org.springframework.http.MediaType`.
- `WorkspaceInitializationService` creates the first workspace + catalog
  (`initializeDefaultWorkspace()` or `setupWorkspace(...)`).
- The schema is not generated (`spring.jpa.hibernate.ddl-auto=none`); tables must exist in MariaDB.

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
- Logging: console only (`logback-spring.xml`); `com.twofourseven.dam` at DEBUG. Use SLF4J loggers.

## Testing

- Unit tests must not need a database or a running Spring context. Use
  `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks` to mock the tier below.
- Controller tests: `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())`
  (see `HeartbeatControllerTest`).
- Test packages mirror main packages.

## Image processing

Image operations shell out to command line tools that are already installed (ImageMagick
`magick`/`convert` is at `/usr/local/bin`). Do not add Java image-processing libraries to replace them.

## Specs workflow

Work is tracked as specs in `specs/NNN-lower_case_name.md` with a `**Status:** ready|completed|cancelled`
line under the title. Skills in `.claude/skills/`: `create-spec`, `create-specs` (batch, main only), `commit-spec-changes` (commit + merge into main), `list-specs`, `cancel-spec`,
`run-spec`. They share `.claude/scripts/specs.sh` (bash 3.2 compatible, since it runs on macOS).
Each spec gets its own git branch named after the file without `.md`. Numbers come from the spec
files on the current branch, so create specs on `main` (`create-specs` enforces this) to keep numbers unique. Only set a spec to
`completed` after its work is done and `mvn -B test` passes.
