---
name: create-specs
description: Create several numbered specs at once on main. Each spec is committed to main and gets its own branch, without checking it out or opening it.
argument-hint: <name>, <name>, ...  (comma or newline separated)
disable-model-invocation: true
---

# create-specs

Create one spec per name in `$ARGUMENTS`. Names are separated by commas or new lines.

## Steps

1. **Get the names.** If `$ARGUMENTS` is empty or only whitespace, ask the user for the spec
   names (at least one is required) and do not continue until you have them.

2. **Run the batch** with the helper. Pass the arguments through exactly as given, in one quoted
   argument, so commas and new lines are kept:

   ```bash
   .claude/scripts/specs.sh create-many "<names exactly as given>"
   ```

   The helper does the checks and the work:
   - Aborts with a `WARNING:` if the current branch is not `main`, or if there are uncommitted
     changes. Nothing is created in that case.
   - For each name, in order: creates the next numbered spec in `specs/` (same naming and template
     as `create-spec`), commits it to `main` as `Add spec NNN: <name>`, and creates the branch
     `NNN-<name>` at that commit. It does **not** check out the branch or open the file.
   - Stops if a branch name already exists. Specs created before that point stay committed.

   Each created spec prints as `created<TAB>specs/NNN-name.md<TAB>NNN-name`.

3. **Don't work around a warning.** If the helper aborts, show the warning to the user and stop.
   Don't switch branches, commit, or stash on the user's behalf unless they ask.

4. **Report** a short table of spec file and branch for each spec created, and note that the user
   is still on `main`. To work on one: `git checkout <branch>`, or `/run-spec <number>`.
