---
name: list-specs
description: List every spec in ./specs with its status, and let the user pick one to run.
---

# list-specs

## Steps

1. Get the specs and their statuses:

   ```bash
   .claude/scripts/specs.sh list
   ```

   Each line is `NNN<TAB>status<TAB>file`. If there are none, say so and suggest `/create-spec`.

2. Show them as a table (number, status, file name), in number order, with the first spec marked
   as the current selection (`>`).

3. Let the user choose a spec to run with AskUserQuestion. Offer the `ready` specs first, in number
   order, starting with the first one (that is the highlighted default). AskUserQuestion shows at
   most 4 options, so if there are more, list the first 4 and the user can type any other number in
   "Other".

4. When the user picks a spec, run it by following the `run-spec` skill
   (`.claude/skills/run-spec/SKILL.md`) with that number.

## Keyboard picker

Claude Code can't take arrow keys inside a skill. For the full keyboard picker (up/down with
wrap-around, Enter runs the spec), tell the user to run this in their own terminal:

```bash
bin/list-specs
```
