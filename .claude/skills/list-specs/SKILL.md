---
name: list-specs
description: List every spec in ./specs with its status.
---

# list-specs

## Steps

1. Get the specs and their statuses:

   ```bash
   .claude/scripts/specs.sh list
   ```

   Each line is `NNN<TAB>status<TAB>file`. If there are none, say so and suggest `/create-spec`.

2. Show **all** of them as a table (number, status, file name without `.md`), in number order.
   Below the table, give a one-line count by status, e.g. `11 specs: 9 ready, 1 completed, 1 cancelled`.

That's all. Don't offer a selection or ask which spec to run. The user runs one with
`/run-spec <number>`.
