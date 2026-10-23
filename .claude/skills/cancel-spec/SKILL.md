---
name: cancel-spec
description: Mark a spec in ./specs as cancelled, given its number.
argument-hint: <spec number>
disable-model-invocation: true
---

# cancel-spec

Cancel the spec whose number is in `$ARGUMENTS` (`3`, `03` and `003` all work).

## Steps

1. If `$ARGUMENTS` is empty or not a number, ask the user for the spec number.

2. Check the current status:

   ```bash
   .claude/scripts/specs.sh status <number>
   ```

   If it is already `completed`, warn that the work has already been applied and ask before
   continuing. If it is already `cancelled`, say so and stop.

3. Set the status:

   ```bash
   .claude/scripts/specs.sh set-status <number> cancelled
   ```

4. Confirm in one line, e.g. `003-add_thumbnails.md: cancelled`.
