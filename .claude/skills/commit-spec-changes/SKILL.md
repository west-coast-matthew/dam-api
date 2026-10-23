---
name: commit-spec-changes
description: Commit all changes on the current spec branch with the given message, switch to main, and merge the spec branch into main.
argument-hint: <commit message>
disable-model-invocation: true
---

# commit-spec-changes

Commit the work on the current spec branch using the message in `$ARGUMENTS`, then merge the
branch into `main`.

## Steps

1. **Get the message.** If `$ARGUMENTS` is empty or only whitespace, ask the user for a commit
   message (it is required) and do not continue until you have one.

2. **Review what will be committed.** Run `git status --porcelain`. If anything looks like it
   shouldn't be committed (credentials, `.env` files, `config/application.properties`, large
   binaries, build output), list it and ask before continuing.

3. **Run the helper**, passing the message exactly as given in one quoted argument:

   ```bash
   .claude/scripts/specs.sh commit-merge "<message>"
   ```

   It:
   - Aborts with a `WARNING:` if you're on `main`, on a detached HEAD, or on a branch that isn't
     a spec branch (`NNN-name`). Nothing is changed in that case.
   - Stages all changes (`git add -A`) and commits them with the message. If there's nothing to
     commit, it skips the commit and still merges the branch's existing commits.
   - Switches to `main` and merges the spec branch with `--no-ff`, so the merge shows in history
     as `Merge branch 'NNN-name'`.
   - On a merge conflict, aborts the merge and switches back to the spec branch. The commit is
     kept. Nothing on `main` changes.

4. **Don't work around a warning or conflict.** Show it to the user and stop. Don't resolve
   conflicts, reset, or force anything unless the user asks.

5. **Report** the files committed, the commit, and the merge commit in a few lines. The user is
   now on `main`. The spec branch is kept, and nothing is pushed.
