# Digital Asset Management (API)

REST API for a Digital Asset Management system. Java 21, Spring Boot 4.1, Maven, MariaDB.

## Quick start

1. Create the runtime config with your database credentials:

   ```bash
   sudo mkdir -p /etc/app-config/dam
   sudo cp config/application.properties.sample /etc/app-config/dam/application.properties
   sudo vi /etc/app-config/dam/application.properties
   ```

   | Property | Meaning | Default |
   |----------|---------|---------|
   | `dam.db.server` | MariaDB host name or IP | `localhost` |
   | `dam.db.port` | MariaDB port | `3306` |
   | `dam.db.name` | Database name | `dam` |
   | `dam.db.username` | Database user | required |
   | `dam.db.password` | Database password | required |

   The app won't start if a required value is missing.

2. Build and run:

   ```bash
   bin/deploy                # build, run tests, launch the jar
   bin/deploy --skip-tests   # build without tests
   ```

   To use a different config file: `DAM_CONFIG=/path/to/application.properties bin/deploy`

3. Check it's up:

   ```bash
   curl -i http://localhost:8080/heartbeat
   ```

   | Response | Meaning |
   |----------|---------|
   | `200 {"status":"UP","database":"UP",...}` | Healthy |
   | `503 {"status":"DOWN","database":"DOWN",...}` | Database unreachable |

## Development

```bash
mvn test       # unit tests (no database needed)
mvn package    # build target/dam-api-0.0.1-SNAPSHOT.jar
```

Logging is at DEBUG for `com.twofourseven.dam` and goes to the console. Change levels with
`logging.level.*` in `application.properties`.

## Specs workflow

Work is defined as specs in `./specs`, named `NNN-lower_case_name.md` (for example
`001-do_something.md`). Each spec has a status line near the top:

```markdown
**Status:** ready
```

| Status | Meaning |
|--------|---------|
| `ready` | Not applied yet (default) |
| `completed` | Applied by `/run-spec` |
| `cancelled` | Will not be run |

Run these skills inside Claude Code (start it with `claude` in this folder):

| Skill | What it does |
|-------|--------------|
| `/create-spec <name>` | Creates the next numbered spec, e.g. `/create-spec Add image upload` → `specs/004-add_image_upload.md`. Asks for a name if you leave it out. Creates and switches to git branch `004-add_image_upload`, then opens the spec in MacDown. |
| `/list-specs` | Lists all specs with their status and lets you pick one to run. |
| `/cancel-spec <number>` | Sets a spec's status to `cancelled`, e.g. `/cancel-spec 4`. |
| `/run-spec <number>` | Switches to the spec's branch, implements it, runs the tests, and marks it `completed`, e.g. `/run-spec 4`. |

### Keyboard picker

`/list-specs` inside Claude Code picks from a menu, since Claude Code can't take arrow keys in a
skill. For the full keyboard picker, run this in a terminal:

```bash
bin/list-specs
```

- Up/Down (or `k`/`j`) move the highlight, starting on the first spec
- Up from the first spec jumps to the last; Down from the last jumps to the first
- Enter runs the selected spec (`claude "/run-spec NNN"`)
- `q` or Esc quits

### Helper script

The skills share `.claude/scripts/specs.sh`, which you can also run directly:

```bash
.claude/scripts/specs.sh list
.claude/scripts/specs.sh create "My new spec"
.claude/scripts/specs.sh set-status 4 cancelled
```

## Project layout

```
bin/deploy                      build + launch with /etc/app-config/dam/application.properties
bin/list-specs                  keyboard spec picker
config/application.properties.sample
specs/                          numbered specs
src/main/java/com/twofourseven/dam/
  controllers/                  REST endpoints (web tier)
  services/                     business logic (business tier)
  repositories/                 Spring Data JPA (data tier)
  entities/                     JPA entities
  vo/                           request/response objects
  exceptions/                   custom exceptions + GlobalExceptionHandler
  config/                       Spring configuration
```
