---
name: create-spec
description: Create a new numbered spec file under ./specs, open it in MacDown, and switch to a new git branch named after it.
argument-hint: <spec name>
disable-model-invocation: true
---

# create-spec

Create a new spec from the name in `$ARGUMENTS`.

## Steps

1. **Get the name.** If `$ARGUMENTS` is empty or only whitespace, ask the user for the spec name
   (it is required) and do not continue until you have one.

2. **Check git state.** Run `git status --porcelain`. If there are uncommitted changes, tell the
   user they will be carried over to the new branch, and ask whether to continue.

3. **Create the file** with the helper, which assigns the next number, lowercases the name, replaces
   spaces with underscores, adds `.md`, writes the title and sets `**Status:** ready`:

   ```bash
   .claude/scripts/specs.sh create "<name>"
   ```

   It prints the new file's path, e.g. `specs/004-add_image_upload.md`.

4. **Create and switch to the feature branch.** The branch name is the file name without `.md`,
   including the number prefix:

   ```bash
   git checkout -b 004-add_image_upload
   ```

   If the branch already exists, stop and tell the user rather than reusing it.

5. **Open it in MacDown:**

   ```bash
   open -a MacDown "<path to new spec>"
   ```

6. Report the file path and branch name in one or two lines. Do not fill in the spec body unless
   the user asks; the user writes it in MacDown.
