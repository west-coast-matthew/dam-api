#!/usr/bin/env bash
# Helper for the spec skills (create-spec, list-specs, cancel-spec, run-spec).
# Compatible with macOS bash 3.2.
#
# Spec files live in ./specs and are named NNN-lower_case_name.md.
# Each spec carries a status line:  **Status:** ready|completed|cancelled
#
# Commands:
#   create <name>            create the next spec, print its path
#   find <number>            print the path of spec <number>
#   status <number>          print the status of spec <number>
#   set-status <number> <s>  set status to ready|completed|cancelled
#   list                     print "NNN<TAB>status<TAB>file" per spec
#   pick                     interactive picker; prints the chosen number
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SPECS_DIR="$ROOT/specs"
STATUS_RE='^\*\*Status:\*\* '

die() { echo "ERROR: $*" >&2; exit 1; }

spec_files() {
  ls "$SPECS_DIR" 2>/dev/null | grep -E '^[0-9]+-.*\.md$' | sort -n || true
}

slugify() {
  echo "$1" | tr '[:upper:]' '[:lower:]' \
    | sed -E 's/[[:space:]]+/_/g; s/[^a-z0-9_-]//g; s/_+/_/g; s/^_+//; s/_+$//'
}

# Accepts 1, 01 or 001
find_spec() {
  local n
  [[ "$1" =~ ^[0-9]+$ ]] || die "spec number must be numeric: $1"
  n=$(printf '%03d' "$((10#$1))")
  local match
  match=$(spec_files | grep -E "^${n}-" | head -n 1 || true)
  [[ -n "$match" ]] || die "no spec found with number $n"
  echo "$SPECS_DIR/$match"
}

get_status() {
  local s
  s=$(grep -E "$STATUS_RE" "$1" | head -n 1 | sed -E "s/${STATUS_RE}//" | tr -d '[:space:]' || true)
  echo "${s:-ready}"
}

cmd_create() {
  local name
  name=$(echo "${1:-}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')
  [[ -n "$name" ]] || die "a spec name is required"
  local slug; slug=$(slugify "$name")
  [[ -n "$slug" ]] || die "name '$name' has no usable characters"
  mkdir -p "$SPECS_DIR"
  local last next file
  last=$(spec_files | tail -n 1 | grep -oE '^[0-9]+' || true)
  next=$(printf '%03d' "$(( ${last:+10#$last} + 1 ))")
  file="$SPECS_DIR/${next}-${slug}.md"
  [[ ! -e "$file" ]] || die "$file already exists"
  cat > "$file" <<EOF
# $name

**Status:** ready

## Goal

<!-- What should be true when this spec is done? -->

## Requirements

-

## Acceptance criteria

-

## Notes

EOF
  echo "$file"
}

cmd_set_status() {
  local file status="${2:-}"
  file=$(find_spec "$1")
  case "$status" in ready|completed|cancelled) ;; *) die "status must be ready, completed or cancelled" ;; esac
  if grep -qE "$STATUS_RE" "$file"; then
    sed -i '' -E "s/${STATUS_RE}.*/**Status:** ${status}/" "$file"
  else
    # No status line yet: add one after the title
    sed -i '' -E "1a\\
\\
**Status:** ${status}
" "$file"
  fi
  echo "$(basename "$file"): $status"
}

cmd_list() {
  local f
  for f in $(spec_files); do
    printf '%s\t%s\t%s\n' "${f%%-*}" "$(get_status "$SPECS_DIR/$f")" "$f"
  done
}

# Arrow-key picker. Up from the first item wraps to the last, down from the
# last wraps to the first. Enter selects, q/Esc quits. UI goes to the tty;
# only the chosen spec number is printed to stdout.
cmd_pick() {
  local lines=() line
  while IFS= read -r line; do lines+=("$line"); done < <(cmd_list)
  local count=${#lines[@]}
  [[ $count -gt 0 ]] || die "no specs found in $SPECS_DIR"
  [[ -t 0 ]] || die "pick needs an interactive terminal"

  local sel=0 key rest i num status file
  local tty=/dev/tty
  trap 'tput cnorm >/dev/tty 2>/dev/null; stty echo 2>/dev/null' EXIT
  tput civis >$tty 2>/dev/null || true
  stty -echo

  draw() {
    printf '\033[H\033[2J' >$tty
    printf 'Specs  (up/down to move, enter to run, q to quit)\n\n' >$tty
    for ((i = 0; i < count; i++)); do
      IFS=$'\t' read -r num status file <<<"${lines[$i]}"
      if [[ $i -eq $sel ]]; then
        printf '\033[7m> %-4s %-10s %s\033[0m\n' "$num" "$status" "$file" >$tty
      else
        printf '  %-4s %-10s %s\n' "$num" "$status" "$file" >$tty
      fi
    done
  }

  while true; do
    draw
    IFS= read -rsn1 key <$tty || true
    if [[ "$key" == $'\033' ]]; then
      IFS= read -rsn2 -t 1 rest <$tty || rest=""
      case "$rest" in
        '[A') sel=$(( (sel - 1 + count) % count )) ;;
        '[B') sel=$(( (sel + 1) % count )) ;;
        '') printf '\033[H\033[2J' >$tty; return 1 ;;
      esac
    elif [[ "$key" == "k" ]]; then sel=$(( (sel - 1 + count) % count ))
    elif [[ "$key" == "j" ]]; then sel=$(( (sel + 1) % count ))
    elif [[ "$key" == "q" ]]; then printf '\033[H\033[2J' >$tty; return 1
    elif [[ -z "$key" ]]; then
      printf '\033[H\033[2J' >$tty
      echo "${lines[$sel]%%$'\t'*}"
      return 0
    fi
  done
}

cmd="${1:-}"; shift || true
case "$cmd" in
  create)     cmd_create "$@" ;;
  find)       find_spec "${1:?spec number required}" ;;
  status)     get_status "$(find_spec "${1:?spec number required}")" ;;
  set-status) cmd_set_status "${1:?spec number required}" "${2:-}" ;;
  list)       cmd_list ;;
  pick)       cmd_pick ;;
  *) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
