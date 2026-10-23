---
name: run-spec
description: Read a spec from ./specs by number and implement it, then mark it completed.
argument-hint: <spec number>
disable-model-invocation: true
---

# run-spec

Execute the spec whose number is in `$ARGUMENTS` (`3`, `03` and `003` all work).

## Steps

1. If `$ARGUMENTS` is empty or not a number, ask the user for the spec number.

2. Find the spec and its status:

   ```bash
   .claude/scripts/specs.sh find <number>
   .claude/scripts/specs.sh status <number>
   ```

   - `cancelled`: tell the user and stop, unless they confirm they want to run it anyway.
   - `completed`: ask whether to run it again before continuing.

3. **Switch to the spec's branch.** The branch name is the file name without `.md`
   (e.g. `003-add_thumbnails`). If that branch exists and isn't the current one, check for
   uncommitted changes, then `git checkout` it. If it doesn't exist, ask whether to create it
   with `git checkout -b` or stay on the current branch.

4. **Read the whole spec** and the project's `CLAUDE.md`. If the spec is empty or too vague to
   act on, list what's missing and ask the user instead of guessing.

5. **Implement it.** Follow the conventions in `CLAUDE.md` (package layout, unit tests with
   Mockito, no database in unit tests). Add or update unit tests for the new behaviour.

6. **Verify.** Run `mvn -B test` and fix any failures. Check every acceptance criterion in the spec.

7. **Mark it completed** only when the work is done and tests pass:

   ```bash
   .claude/scripts/specs.sh set-status <number> completed
   ```

   If you could not finish, leave the status as `ready` and explain what's left.

8. Summarize what changed. Don't commit or push unless the user asks.
