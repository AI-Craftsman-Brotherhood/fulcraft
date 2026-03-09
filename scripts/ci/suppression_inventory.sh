#!/usr/bin/env bash
set -euo pipefail

DEFAULT_SOURCE_ROOTS=(
  "app/src/main/java"
  "app/src/test/java"
  "app/src/integrationTest/java"
  "app/src/e2eTest/java"
)

KNOWN_SOURCE_SETS=("main" "test" "integrationTest" "e2eTest")

LAST_TOTAL=0
LAST_SUPPRESS_WARNINGS=0
LAST_SUPPRESS_FB_WARNINGS=0
LAST_NOPMD=0

usage_main() {
  cat <<'USAGE'
Usage: scripts/ci/suppression_inventory.sh <command> [options]

Commands:
  scan     Generate suppression inventory JSON
  compare  Compare current suppressions with baseline JSON

Run '<command> --help' for detailed options.
USAGE
}

usage_scan() {
  cat <<'USAGE'
Usage: scripts/ci/suppression_inventory.sh scan --output <path> [--source-root <path>]... [--merge-manual-from <path>]

Generate suppression inventory JSON.
Defaults source roots to:
  - app/src/main/java
  - app/src/test/java
  - app/src/integrationTest/java
  - app/src/e2eTest/java
USAGE
}

usage_compare() {
  cat <<'USAGE'
Usage: scripts/ci/suppression_inventory.sh compare --baseline <path> [--source-root <path>]... [--output-current <path>] [--fail-on-new]

Compare current suppressions with baseline JSON.
By default it reports differences without failing; pass --fail-on-new to fail on additions.
USAGE
}

error() {
  local message="$1"
  local code="${2:-1}"
  echo "ERROR: ${message}" >&2
  return "${code}"
}

json_escape() {
  local text="$1"
  text=${text//\\/\\\\}
  text=${text//\"/\\\"}
  text=${text//$'\n'/\\n}
  text=${text//$'\r'/\\r}
  text=${text//$'\t'/\\t}
  text=${text//$'\f'/\\f}
  text=${text//$'\b'/\\b}
  printf '%s' "${text}"
}

canonical_path() {
  local path="$1"
  if command -v realpath >/dev/null 2>&1; then
    realpath -m "${path}"
    return
  fi
  if command -v readlink >/dev/null 2>&1; then
    readlink -f "${path}" 2>/dev/null || printf '%s\n' "${path}"
    return
  fi
  printf '%s\n' "${path}"
}

relative_to_cwd() {
  local abs_path="$1"
  local cwd="${PWD%/}"
  if [[ "${abs_path}" == "${cwd}" ]]; then
    printf '.\n'
    return
  fi
  if [[ "${abs_path}" == "${cwd}/"* ]]; then
    printf '%s\n' "${abs_path#"${cwd}/"}"
    return
  fi
  printf '%s\n' "${abs_path}"
}

resolve_source_set() {
  local rel_path="$1"
  local p1 p2 p3 _rest
  IFS='/' read -r p1 p2 p3 _rest <<< "${rel_path}"
  if [[ "${p1}" == "app" && "${p2}" == "src" && -n "${p3}" ]]; then
    printf '%s\n' "${p3}"
    return
  fi
  printf 'unknown\n'
}

selected_source_roots() {
  local -n cli_roots_ref="$1"
  local -n out_roots_ref="$2"

  local -a candidates=()
  if (( ${#cli_roots_ref[@]} > 0 )); then
    candidates=("${cli_roots_ref[@]}")
  else
    candidates=("${DEFAULT_SOURCE_ROOTS[@]}")
  fi

  declare -A seen_resolved=()
  out_roots_ref=()

  local root resolved
  for root in "${candidates[@]}"; do
    resolved="$(canonical_path "${root}")"
    if [[ -n "${seen_resolved["${resolved}"]:-}" ]]; then
      continue
    fi
    seen_resolved["${resolved}"]=1
    out_roots_ref+=("${root}")
  done
}

build_parser_awk() {
  local awk_path="$1"
  cat > "${awk_path}" <<'AWK'
BEGIN {
  line_no = 0
  text = ""
}
{
  raw = $0
  line_no++
  temp = raw
  while (match(temp, /NOPMD/)) {
    print source_set "\t" rel_path "\t" line_no "\tNOPMD\tNOPMD"
    temp = substr(temp, RSTART + RLENGTH)
  }
  text = text raw "\n"
}
END {
  parse_annotations()
}

function trim(value) {
  sub(/^[[:space:]]+/, "", value)
  sub(/[[:space:]]+$/, "", value)
  return value
}

function decode_string(value,   out, i, ch, next_char) {
  out = ""
  for (i = 1; i <= length(value); i++) {
    ch = substr(value, i, 1)
    if (ch != "\\") {
      out = out ch
      continue
    }

    i++
    if (i > length(value)) {
      out = out "\\"
      break
    }

    next_char = substr(value, i, 1)
    if (next_char == "n") {
      out = out "\n"
    } else if (next_char == "r") {
      out = out "\r"
    } else if (next_char == "t") {
      out = out "\t"
    } else if (next_char == "b") {
      out = out "\b"
    } else if (next_char == "f") {
      out = out "\f"
    } else if (next_char == "\\") {
      out = out "\\"
    } else if (next_char == "\"") {
      out = out "\""
    } else if (next_char == "'") {
      out = out "'"
    } else {
      out = out next_char
    }
  }
  return out
}

function collect_string_literals(expr, out_rules,   i, ch, in_string, escaped, token, count) {
  delete out_rules
  in_string = 0
  escaped = 0
  token = ""
  count = 0

  for (i = 1; i <= length(expr); i++) {
    ch = substr(expr, i, 1)

    if (!in_string) {
      if (ch == "\"") {
        in_string = 1
        escaped = 0
        token = ""
      }
      continue
    }

    if (escaped) {
      token = token "\\" ch
      escaped = 0
      continue
    }

    if (ch == "\\") {
      escaped = 1
      continue
    }

    if (ch == "\"") {
      out_rules[++count] = decode_string(token)
      in_string = 0
      token = ""
      continue
    }

    token = token ch
  }

  return count
}

function split_top_level(expr, out_parts,   i, ch, in_string, escaped, depth_paren, depth_brace, depth_bracket, current, count, token) {
  delete out_parts
  in_string = 0
  escaped = 0
  depth_paren = 0
  depth_brace = 0
  depth_bracket = 0
  current = ""
  count = 0

  for (i = 1; i <= length(expr); i++) {
    ch = substr(expr, i, 1)

    if (in_string) {
      current = current ch
      if (escaped) {
        escaped = 0
      } else if (ch == "\\") {
        escaped = 1
      } else if (ch == "\"") {
        in_string = 0
      }
      continue
    }

    if (ch == "\"") {
      in_string = 1
      current = current ch
      continue
    }

    if (ch == "(") {
      depth_paren++
    } else if (ch == ")") {
      depth_paren--
    } else if (ch == "{") {
      depth_brace++
    } else if (ch == "}") {
      depth_brace--
    } else if (ch == "[") {
      depth_bracket++
    } else if (ch == "]") {
      depth_bracket--
    }

    if (ch == "," && depth_paren == 0 && depth_brace == 0 && depth_bracket == 0) {
      token = trim(current)
      if (token != "") {
        out_parts[++count] = token
      }
      current = ""
      continue
    }

    current = current ch
  }

  token = trim(current)
  if (token != "") {
    out_parts[++count] = token
  }

  return count
}

function extract_fb_rules(args_text, out_rules,   parts, part_count, i, eq_pos, key, value_expr, value_candidate) {
  delete out_rules
  value_expr = ""

  part_count = split_top_level(args_text, parts)
  for (i = 1; i <= part_count; i++) {
    eq_pos = index(parts[i], "=")
    if (eq_pos > 0) {
      key = trim(substr(parts[i], 1, eq_pos - 1))
      value_candidate = trim(substr(parts[i], eq_pos + 1))
      if (key == "value") {
        value_expr = value_candidate
        break
      }
    }
  }

  if (value_expr == "") {
    for (i = 1; i <= part_count; i++) {
      if (index(parts[i], "=") == 0) {
        value_expr = trim(parts[i])
        break
      }
    }
  }

  if (value_expr == "") {
    return 0
  }

  return collect_string_literals(value_expr, out_rules)
}

function parse_annotations(   i, length_text, state, ch, ch2, ch3, ann, j, current_line, c,
                              open_line, args, depth, in_string, escaped,
                              rule_count, k, rules) {
  length_text = length(text)
  state = "code"
  current_line = 1
  i = 1

  while (i <= length_text) {
    ch = substr(text, i, 1)
    ch2 = substr(text, i, 2)
    ch3 = substr(text, i, 3)

    if (state == "code") {
      if (ch2 == "//") {
        state = "line_comment"
        i += 2
        continue
      }
      if (ch2 == "/*") {
        state = "block_comment"
        i += 2
        continue
      }
      if (ch3 == "\"\"\"") {
        state = "text_block"
        i += 3
        continue
      }
      if (ch == "\"") {
        state = "string"
        i++
        continue
      }
      if (ch == "'") {
        state = "char"
        i++
        continue
      }

      ann = ""
      j = i
      if (substr(text, i, 17) == "@SuppressWarnings") {
        ann = "SuppressWarnings"
        j = i + 17
      } else if (substr(text, i, 19) == "@SuppressFBWarnings") {
        ann = "SuppressFBWarnings"
        j = i + 19
      }

      if (ann != "") {
        while (j <= length_text) {
          c = substr(text, j, 1)
          if (c ~ /[ \t\r\n]/) {
            if (c == "\n") {
              current_line++
            }
            j++
            continue
          }
          break
        }

        if (substr(text, j, 1) == "(") {
          open_line = current_line
          j++
          args = ""
          depth = 1
          in_string = 0
          escaped = 0

          while (j <= length_text) {
            c = substr(text, j, 1)

            if (in_string) {
              if (escaped) {
                escaped = 0
              } else if (c == "\\") {
                escaped = 1
              } else if (c == "\"") {
                in_string = 0
              }
            } else {
              if (c == "\"") {
                in_string = 1
              } else if (c == "(") {
                depth++
              } else if (c == ")") {
                depth--
                if (depth == 0) {
                  break
                }
              }
            }

            args = args c
            if (c == "\n") {
              current_line++
            }
            j++
          }

          if (depth == 0) {
            delete rules
            if (ann == "SuppressWarnings") {
              rule_count = collect_string_literals(args, rules)
            } else {
              rule_count = extract_fb_rules(args, rules)
            }

            if (rule_count == 0) {
              rules[1] = "<unspecified>"
              rule_count = 1
            }

            for (k = 1; k <= rule_count; k++) {
              print source_set "\t" rel_path "\t" open_line "\t" ann "\t" rules[k]
            }

            i = j + 1
            continue
          }

          i = j + 1
          continue
        }
      }

      if (ch == "\n") {
        current_line++
      }
      i++
      continue
    }

    if (state == "line_comment") {
      if (ch == "\n") {
        state = "code"
        current_line++
      }
      i++
      continue
    }

    if (state == "block_comment") {
      if (ch2 == "*/") {
        state = "code"
        i += 2
        continue
      }
      if (ch == "\n") {
        current_line++
      }
      i++
      continue
    }

    if (state == "string") {
      if (ch == "\\" && i < length_text) {
        if (substr(text, i + 1, 1) == "\n") {
          current_line++
        }
        i += 2
        continue
      }
      if (ch == "\"") {
        state = "code"
        i++
        continue
      }
      if (ch == "\n") {
        current_line++
      }
      i++
      continue
    }

    if (state == "char") {
      if (ch == "\\" && i < length_text) {
        if (substr(text, i + 1, 1) == "\n") {
          current_line++
        }
        i += 2
        continue
      }
      if (ch == "'") {
        state = "code"
        i++
        continue
      }
      if (ch == "\n") {
        current_line++
      }
      i++
      continue
    }

    if (state == "text_block") {
      if (ch3 == "\"\"\"") {
        state = "code"
        i += 3
        continue
      }
      if (ch == "\n") {
        current_line++
      }
      i++
      continue
    }
  }
}
AWK
}

extract_manual_fields() {
  local baseline_path="$1"
  local output_path="$2"

  : > "${output_path}"
  [[ -n "${baseline_path}" ]] || return 0
  [[ -f "${baseline_path}" ]] || return 0

  awk '
function extract_string(line, key,    pattern, matches) {
  pattern = "\"" key "\"[[:space:]]*:[[:space:]]*\"(([^\"\\\\]|\\\\.)*)\""
  if (match(line, pattern, matches)) {
    return matches[1]
  }
  return ""
}
BEGIN {
  in_grouped = 0
  in_object = 0
}
{
  if (!in_grouped) {
    if ($0 ~ /"grouped"[[:space:]]*:[[:space:]]*\[/) {
      in_grouped = 1
    }
    next
  }

  if ($0 ~ /"entries"[[:space:]]*:[[:space:]]*\[/) {
    in_grouped = 0
    next
  }

  if ($0 ~ /^[[:space:]]*\{[[:space:]]*$/) {
    in_object = 1
    source_set = ""
    file_path = ""
    annotation = ""
    rule = ""
    reason = ""
    alternative = ""
    revisit = ""
    next_review = ""
    next
  }

  if (!in_object) {
    next
  }

  value = extract_string($0, "source_set")
  if (value != "") {
    source_set = value
  }

  value = extract_string($0, "file")
  if (value != "") {
    file_path = value
  }

  value = extract_string($0, "annotation")
  if (value != "") {
    annotation = value
  }

  value = extract_string($0, "rule")
  if (value != "") {
    rule = value
  }

  value = extract_string($0, "reason")
  if ($0 ~ /"reason"[[:space:]]*:/) {
    reason = value
  }

  value = extract_string($0, "alternative")
  if ($0 ~ /"alternative"[[:space:]]*:/) {
    alternative = value
  }

  value = extract_string($0, "revisit_condition")
  if ($0 ~ /"revisit_condition"[[:space:]]*:/) {
    revisit = value
  }

  value = extract_string($0, "next_review_date")
  if ($0 ~ /"next_review_date"[[:space:]]*:/) {
    next_review = value
  }

  if ($0 ~ /^[[:space:]]*\}[[:space:]]*,?[[:space:]]*$/) {
    if (source_set != "" || file_path != "" || annotation != "" || rule != "") {
      key = source_set "|" file_path "|" annotation "|" rule
      printf "%s\t%s\t%s\t%s\t%s\n", key, reason, alternative, revisit, next_review
    }
    in_object = 0
  }
}
' "${baseline_path}" > "${output_path}"
}

extract_grouped_counts() {
  local snapshot_path="$1"
  local output_path="$2"

  : > "${output_path}"
  [[ -f "${snapshot_path}" ]] || return 0

  awk '
function extract_string(line, key,    pattern, matches) {
  pattern = "\"" key "\"[[:space:]]*:[[:space:]]*\"(([^\"\\\\]|\\\\.)*)\""
  if (match(line, pattern, matches)) {
    return matches[1]
  }
  return ""
}
function extract_number(line, key,    pattern, matches) {
  pattern = "\"" key "\"[[:space:]]*:[[:space:]]*([0-9]+)"
  if (match(line, pattern, matches)) {
    return matches[1]
  }
  return ""
}
BEGIN {
  in_grouped = 0
  in_object = 0
}
{
  if (!in_grouped) {
    if ($0 ~ /"grouped"[[:space:]]*:[[:space:]]*\[/) {
      in_grouped = 1
    }
    next
  }

  if ($0 ~ /"entries"[[:space:]]*:[[:space:]]*\[/) {
    in_grouped = 0
    next
  }

  if ($0 ~ /^[[:space:]]*\{[[:space:]]*$/) {
    in_object = 1
    source_set = ""
    file_path = ""
    annotation = ""
    rule = ""
    count = ""
    next
  }

  if (!in_object) {
    next
  }

  value = extract_string($0, "source_set")
  if (value != "") {
    source_set = value
  }

  value = extract_string($0, "file")
  if (value != "") {
    file_path = value
  }

  value = extract_string($0, "annotation")
  if (value != "") {
    annotation = value
  }

  value = extract_string($0, "rule")
  if (value != "") {
    rule = value
  }

  value = extract_number($0, "count")
  if ($0 ~ /"count"[[:space:]]*:/) {
    count = value
  }

  if ($0 ~ /^[[:space:]]*\}[[:space:]]*,?[[:space:]]*$/) {
    if (source_set != "" || file_path != "" || annotation != "" || rule != "") {
      if (count == "") {
        count = 0
      }
      key = source_set "|" file_path "|" annotation "|" rule
      printf "%s\t%s\n", key, count
    }
    in_object = 0
  }
}
' "${snapshot_path}" > "${output_path}"
}

build_inventory_snapshot() {
  local output_path="$1"
  local merge_manual_from="$2"
  shift 2
  local -a source_roots=("$@")

  local temp_dir
  temp_dir="$(mktemp -d)"

  local parser_awk="${temp_dir}/suppression_parser.awk"
  local manifest_file="${temp_dir}/manifest.tsv"
  local entries_raw_file="${temp_dir}/entries_raw.tsv"
  local entries_file="${temp_dir}/entries.tsv"
  local occurrences_file="${temp_dir}/occurrences.tsv"
  local grouped_file="${temp_dir}/grouped.tsv"
  local counts_by_rule_file="${temp_dir}/counts_by_rule.tsv"
  local counts_by_source_set_file="${temp_dir}/counts_by_source_set.tsv"
  local manual_fields_file="${temp_dir}/manual_fields.tsv"

  build_parser_awk "${parser_awk}"

  : > "${manifest_file}"
  : > "${entries_raw_file}"

  declare -A seen_files=()
  local root java_file abs_path rel_path source_set
  for root in "${source_roots[@]}"; do
    [[ -d "${root}" ]] || continue
    while IFS= read -r -d '' java_file; do
      abs_path="$(canonical_path "${java_file}")"
      if [[ -n "${seen_files["${abs_path}"]:-}" ]]; then
        continue
      fi
      seen_files["${abs_path}"]=1

      rel_path="$(relative_to_cwd "${abs_path}")"
      source_set="$(resolve_source_set "${rel_path}")"
      printf '%s\t%s\t%s\n' "${source_set}" "${rel_path}" "${abs_path}" >> "${manifest_file}"
    done < <(find "${root}" -type f -name '*.java' -print0)
  done

  if [[ -s "${manifest_file}" ]]; then
    LC_ALL=C sort -t $'\t' -k2,2 "${manifest_file}" -o "${manifest_file}"
    while IFS=$'\t' read -r source_set rel_path abs_path; do
      awk -v source_set="${source_set}" -v rel_path="${rel_path}" -f "${parser_awk}" "${abs_path}" >> "${entries_raw_file}"
    done < "${manifest_file}"
  fi

  if [[ -s "${entries_raw_file}" ]]; then
    LC_ALL=C sort -t $'\t' -k1,1 -k2,2 -k3,3n -k4,4 -k5,5 "${entries_raw_file}" > "${entries_file}"
  else
    : > "${entries_file}"
  fi

  awk -F'\t' '!seen[$1 FS $2 FS $3 FS $4]++ && NF >= 4 { print $1 "\t" $4 }' "${entries_file}" > "${occurrences_file}"

  if [[ -s "${entries_file}" ]]; then
    awk -F'\t' '
BEGIN {
  OFS = "\t"
  has_row = 0
}
function flush(   i) {
  if (!has_row) {
    return
  }
  printf "%s\t%s\t%s\t%s\t%d\t", src, file_path, annotation, rule, count
  for (i = 1; i <= line_count; i++) {
    printf "%s%s", (i == 1 ? "" : ","), lines[i]
  }
  printf "\n"
}
{
  key = $1 OFS $2 OFS $4 OFS $5
  if (!has_row) {
    src = $1
    file_path = $2
    annotation = $4
    rule = $5
    count = 0
    line_count = 0
    previous = key
    has_row = 1
  } else if (key != previous) {
    flush()
    src = $1
    file_path = $2
    annotation = $4
    rule = $5
    count = 0
    line_count = 0
    previous = key
  }

  count++
  lines[++line_count] = $3
}
END {
  flush()
}
' "${entries_file}" > "${grouped_file}"
  else
    : > "${grouped_file}"
  fi

  if [[ -s "${entries_file}" ]]; then
    awk -F'\t' '
{
  rule_counts[$5]++
}
END {
  for (rule in rule_counts) {
    printf "%s\t%d\n", rule, rule_counts[rule]
  }
}
' "${entries_file}" | LC_ALL=C sort -t $'\t' -k2,2nr -k1,1 > "${counts_by_rule_file}"
  else
    : > "${counts_by_rule_file}"
  fi

  declare -A source_total=()
  declare -A source_sw=()
  declare -A source_sfb=()
  declare -A source_nopmd=()

  local source_name annotation_name
  while IFS=$'\t' read -r source_name annotation_name; do
    [[ -n "${source_name}" ]] || continue

    source_total["${source_name}"]=$(( ${source_total["${source_name}"]:-0} + 1 ))
    case "${annotation_name}" in
      SuppressWarnings)
        source_sw["${source_name}"]=$(( ${source_sw["${source_name}"]:-0} + 1 ))
        ;;
      SuppressFBWarnings)
        source_sfb["${source_name}"]=$(( ${source_sfb["${source_name}"]:-0} + 1 ))
        ;;
      NOPMD)
        source_nopmd["${source_name}"]=$(( ${source_nopmd["${source_name}"]:-0} + 1 ))
        ;;
    esac
  done < "${occurrences_file}"

  : > "${counts_by_source_set_file}"
  local known
  for known in "${KNOWN_SOURCE_SETS[@]}"; do
    printf '%s\t%d\t%d\t%d\t%d\n' \
      "${known}" \
      "${source_total["${known}"]:-0}" \
      "${source_sw["${known}"]:-0}" \
      "${source_sfb["${known}"]:-0}" \
      "${source_nopmd["${known}"]:-0}" \
      >> "${counts_by_source_set_file}"
  done

  local unknown_sets_file="${temp_dir}/unknown_source_sets.txt"
  : > "${unknown_sets_file}"
  for source_name in "${!source_total[@]}"; do
    case "${source_name}" in
      main|test|integrationTest|e2eTest)
        ;;
      *)
        printf '%s\n' "${source_name}" >> "${unknown_sets_file}"
        ;;
    esac
  done

  if [[ -s "${unknown_sets_file}" ]]; then
    LC_ALL=C sort -u "${unknown_sets_file}" | while IFS= read -r source_name; do
      printf '%s\t%d\t%d\t%d\t%d\n' \
        "${source_name}" \
        "${source_total["${source_name}"]:-0}" \
        "${source_sw["${source_name}"]:-0}" \
        "${source_sfb["${source_name}"]:-0}" \
        "${source_nopmd["${source_name}"]:-0}" \
        >> "${counts_by_source_set_file}"
    done
  fi

  extract_manual_fields "${merge_manual_from}" "${manual_fields_file}"

  local total rule_entries_total sw_count sfb_count nopmd_count
  total="$(wc -l < "${occurrences_file}")"
  total="${total//[[:space:]]/}"

  rule_entries_total="$(wc -l < "${entries_file}")"
  rule_entries_total="${rule_entries_total//[[:space:]]/}"

  sw_count="$(awk -F'\t' '$2 == "SuppressWarnings" { count++ } END { print count + 0 }' "${occurrences_file}")"
  sfb_count="$(awk -F'\t' '$2 == "SuppressFBWarnings" { count++ } END { print count + 0 }' "${occurrences_file}")"
  nopmd_count="$(awk -F'\t' '$2 == "NOPMD" { count++ } END { print count + 0 }' "${occurrences_file}")"

  local generated_at generated_at_utc
  generated_at="$(date -u +%Y-%m-%d)"
  generated_at_utc="$(date -u +%Y-%m-%dT%H:%M:%S+00:00)"

  declare -A manual_reason=()
  declare -A manual_alternative=()
  declare -A manual_revisit=()
  declare -A manual_next_review=()

  local key reason alternative revisit_condition next_review_date
  while IFS=$'\t' read -r key reason alternative revisit_condition next_review_date; do
    [[ -n "${key}" ]] || continue
    manual_reason["${key}"]="${reason}"
    manual_alternative["${key}"]="${alternative}"
    manual_revisit["${key}"]="${revisit_condition}"
    manual_next_review["${key}"]="${next_review_date}"
  done < "${manual_fields_file}"

  local -a source_count_rows=()
  local -a rule_count_rows=()
  local -a grouped_rows=()
  local -a entry_rows=()

  mapfile -t source_count_rows < "${counts_by_source_set_file}"
  mapfile -t rule_count_rows < "${counts_by_rule_file}"
  mapfile -t grouped_rows < "${grouped_file}"
  mapfile -t entry_rows < "${entries_file}"

  mkdir -p "$(dirname "${output_path}")"

  {
    printf '{\n'
    printf '  "tool": "suppressions",\n'
    printf '  "generated_at": "%s",\n' "$(json_escape "${generated_at}")"
    printf '  "generated_at_utc": "%s",\n' "$(json_escape "${generated_at_utc}")"

    printf '  "source_roots": [\n'
    local i
    for i in "${!source_roots[@]}"; do
      local comma
      comma=","; [[ "${i}" -eq $(( ${#source_roots[@]} - 1 )) ]] && comma=""
      printf '    "%s"%s\n' "$(json_escape "${source_roots[${i}]}")" "${comma}"
    done
    printf '  ],\n'

    printf '  "counts": {\n'
    printf '    "total": %d,\n' "${total}"
    printf '    "rule_entries_total": %d,\n' "${rule_entries_total}"
    printf '    "SuppressWarnings": %d,\n' "${sw_count}"
    printf '    "SuppressFBWarnings": %d,\n' "${sfb_count}"
    printf '    "NOPMD": %d\n' "${nopmd_count}"
    printf '  },\n'

    printf '  "counts_by_source_set": {\n'
    for i in "${!source_count_rows[@]}"; do
      local row source_value total_value sw_value sfb_value nopmd_value comma
      row="${source_count_rows[${i}]}"
      IFS=$'\t' read -r source_value total_value sw_value sfb_value nopmd_value <<< "${row}"
      comma=","; [[ "${i}" -eq $(( ${#source_count_rows[@]} - 1 )) ]] && comma=""

      printf '    "%s": {\n' "$(json_escape "${source_value}")"
      printf '      "total": %d,\n' "${total_value:-0}"
      printf '      "SuppressWarnings": %d,\n' "${sw_value:-0}"
      printf '      "SuppressFBWarnings": %d,\n' "${sfb_value:-0}"
      printf '      "NOPMD": %d\n' "${nopmd_value:-0}"
      printf '    }%s\n' "${comma}"
    done
    printf '  },\n'

    printf '  "counts_by_rule": [\n'
    for i in "${!rule_count_rows[@]}"; do
      local row rule_value rule_count comma
      row="${rule_count_rows[${i}]}"
      IFS=$'\t' read -r rule_value rule_count <<< "${row}"
      comma=","; [[ "${i}" -eq $(( ${#rule_count_rows[@]} - 1 )) ]] && comma=""

      printf '    {\n'
      printf '      "rule": "%s",\n' "$(json_escape "${rule_value}")"
      printf '      "count": %d\n' "${rule_count:-0}"
      printf '    }%s\n' "${comma}"
    done
    printf '  ],\n'

    printf '  "grouped": [\n'
    for i in "${!grouped_rows[@]}"; do
      local row source_value file_value annotation_value rule_value count_value lines_csv comma
      row="${grouped_rows[${i}]}"
      IFS=$'\t' read -r source_value file_value annotation_value rule_value count_value lines_csv <<< "${row}"
      comma=","; [[ "${i}" -eq $(( ${#grouped_rows[@]} - 1 )) ]] && comma=""

      local group_key
      group_key="${source_value}|${file_value}|${annotation_value}|${rule_value}"

      printf '    {\n'
      printf '      "source_set": "%s",\n' "$(json_escape "${source_value}")"
      printf '      "file": "%s",\n' "$(json_escape "${file_value}")"
      printf '      "annotation": "%s",\n' "$(json_escape "${annotation_value}")"
      printf '      "rule": "%s",\n' "$(json_escape "${rule_value}")"
      printf '      "count": %d,\n' "${count_value:-0}"

      printf '      "lines": [\n'
      local -a line_values=()
      if [[ -n "${lines_csv:-}" ]]; then
        IFS=',' read -r -a line_values <<< "${lines_csv}"
      fi
      local j
      for j in "${!line_values[@]}"; do
        local line_comma
        line_comma=","; [[ "${j}" -eq $(( ${#line_values[@]} - 1 )) ]] && line_comma=""
        printf '        %d%s\n' "${line_values[${j}]}" "${line_comma}"
      done
      printf '      ],\n'

      printf '      "reason": "%s",\n' "${manual_reason["${group_key}"]:-}"
      printf '      "alternative": "%s",\n' "${manual_alternative["${group_key}"]:-}"
      printf '      "revisit_condition": "%s",\n' "${manual_revisit["${group_key}"]:-}"
      printf '      "next_review_date": "%s"\n' "${manual_next_review["${group_key}"]:-}"
      printf '    }%s\n' "${comma}"
    done
    printf '  ],\n'

    printf '  "entries": [\n'
    for i in "${!entry_rows[@]}"; do
      local row source_value file_value line_value annotation_value rule_value comma
      row="${entry_rows[${i}]}"
      IFS=$'\t' read -r source_value file_value line_value annotation_value rule_value <<< "${row}"
      comma=","; [[ "${i}" -eq $(( ${#entry_rows[@]} - 1 )) ]] && comma=""

      printf '    {\n'
      printf '      "source_set": "%s",\n' "$(json_escape "${source_value}")"
      printf '      "file": "%s",\n' "$(json_escape "${file_value}")"
      printf '      "line": %d,\n' "${line_value:-0}"
      printf '      "annotation": "%s",\n' "$(json_escape "${annotation_value}")"
      printf '      "rule": "%s"\n' "$(json_escape "${rule_value}")"
      printf '    }%s\n' "${comma}"
    done
    printf '  ]\n'
    printf '}\n'
  } > "${output_path}"

  LAST_TOTAL="${total}"
  LAST_SUPPRESS_WARNINGS="${sw_count}"
  LAST_SUPPRESS_FB_WARNINGS="${sfb_count}"
  LAST_NOPMD="${nopmd_count}"

  rm -rf "${temp_dir}"
}

describe_key() {
  local key="$1"
  local source_set file_path annotation rule
  IFS='|' read -r source_set file_path annotation rule <<< "${key}"
  printf '%s:%s [%s] %s' "${annotation}" "${rule}" "${source_set}" "${file_path}"
}

load_count_map() {
  local map_file="$1"
  local -n out_map_ref="$2"

  out_map_ref=()
  local key count
  while IFS=$'\t' read -r key count; do
    [[ -n "${key}" ]] || continue
    out_map_ref["${key}"]="${count:-0}"
  done < "${map_file}"
}

read_counts_total() {
  local baseline_path="$1"
  awk '
BEGIN {
  in_counts = 0
}
{
  if (!in_counts && $0 ~ /"counts"[[:space:]]*:[[:space:]]*\{/) {
    in_counts = 1
    next
  }
  if (!in_counts) {
    next
  }

  if (match($0, /"total"[[:space:]]*:[[:space:]]*([0-9]+)/, matches)) {
    print matches[1]
    exit
  }

  if ($0 ~ /^[[:space:]]*\}[[:space:]]*,?[[:space:]]*$/) {
    exit
  }
}
' "${baseline_path}"
}

command_scan() {
  local -a cli_source_roots=()
  local output_path=""
  local merge_manual_from=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --source-root)
        [[ $# -ge 2 ]] || { usage_scan >&2; return 2; }
        cli_source_roots+=("$2")
        shift 2
        ;;
      --output)
        [[ $# -ge 2 ]] || { usage_scan >&2; return 2; }
        output_path="$2"
        shift 2
        ;;
      --merge-manual-from)
        [[ $# -ge 2 ]] || { usage_scan >&2; return 2; }
        merge_manual_from="$2"
        shift 2
        ;;
      -h|--help)
        usage_scan
        return 0
        ;;
      *)
        echo "Unknown argument: $1" >&2
        usage_scan >&2
        return 2
        ;;
    esac
  done

  [[ -n "${output_path}" ]] || { usage_scan >&2; return 2; }

  local -a roots=()
  selected_source_roots cli_source_roots roots

  build_inventory_snapshot "${output_path}" "${merge_manual_from}" "${roots[@]}"

  echo "Suppression inventory generated: total=${LAST_TOTAL}, SuppressWarnings=${LAST_SUPPRESS_WARNINGS}, SuppressFBWarnings=${LAST_SUPPRESS_FB_WARNINGS}, NOPMD=${LAST_NOPMD}"
}

command_compare() {
  local baseline_path=""
  local output_current=""
  local fail_on_new=false
  local -a cli_source_roots=()

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --baseline)
        [[ $# -ge 2 ]] || { usage_compare >&2; return 2; }
        baseline_path="$2"
        shift 2
        ;;
      --source-root)
        [[ $# -ge 2 ]] || { usage_compare >&2; return 2; }
        cli_source_roots+=("$2")
        shift 2
        ;;
      --output-current)
        [[ $# -ge 2 ]] || { usage_compare >&2; return 2; }
        output_current="$2"
        shift 2
        ;;
      --fail-on-new)
        fail_on_new=true
        shift
        ;;
      -h|--help)
        usage_compare
        return 0
        ;;
      *)
        echo "Unknown argument: $1" >&2
        usage_compare >&2
        return 2
        ;;
    esac
  done

  [[ -n "${baseline_path}" ]] || { usage_compare >&2; return 2; }

  if [[ ! -f "${baseline_path}" ]]; then
    error "baseline not found: ${baseline_path}" 2
    return 2
  fi

  if ! grep -q '"counts"[[:space:]]*:' "${baseline_path}" || ! grep -q '"grouped"[[:space:]]*:' "${baseline_path}"; then
    error "baseline JSON is invalid: ${baseline_path}" 2
    return 2
  fi

  local baseline_total
  baseline_total="$(read_counts_total "${baseline_path}")"
  if [[ -z "${baseline_total}" ]]; then
    error "baseline JSON is invalid: ${baseline_path}" 2
    return 2
  fi

  local temp_dir
  temp_dir="$(mktemp -d)"

  local current_snapshot_path
  if [[ -n "${output_current}" ]]; then
    current_snapshot_path="${output_current}"
  else
    current_snapshot_path="${temp_dir}/current_snapshot.json"
  fi

  local -a roots=()
  selected_source_roots cli_source_roots roots

  build_inventory_snapshot "${current_snapshot_path}" "" "${roots[@]}"
  local current_total="${LAST_TOTAL}"

  local baseline_counts_file="${temp_dir}/baseline_counts.tsv"
  local current_counts_file="${temp_dir}/current_counts.tsv"

  extract_grouped_counts "${baseline_path}" "${baseline_counts_file}"
  extract_grouped_counts "${current_snapshot_path}" "${current_counts_file}"

  declare -A baseline_counts=()
  declare -A current_counts=()
  declare -A all_keys=()

  load_count_map "${baseline_counts_file}" baseline_counts
  load_count_map "${current_counts_file}" current_counts

  local key
  for key in "${!baseline_counts[@]}"; do
    all_keys["${key}"]=1
  done
  for key in "${!current_counts[@]}"; do
    all_keys["${key}"]=1
  done

  local added_file="${temp_dir}/added.tsv"
  local removed_file="${temp_dir}/removed.tsv"
  : > "${added_file}"
  : > "${removed_file}"

  local added_total=0
  local removed_total=0

  for key in "${!all_keys[@]}"; do
    local baseline_count current_count
    baseline_count="${baseline_counts["${key}"]:-0}"
    current_count="${current_counts["${key}"]:-0}"

    if (( current_count > baseline_count )); then
      local delta
      delta=$(( current_count - baseline_count ))
      added_total=$(( added_total + delta ))
      printf '%s\t%d\n' "${key}" "${delta}" >> "${added_file}"
    elif (( baseline_count > current_count )); then
      local delta
      delta=$(( baseline_count - current_count ))
      removed_total=$(( removed_total + delta ))
      printf '%s\t%d\n' "${key}" "${delta}" >> "${removed_file}"
    fi
  done

  if [[ -s "${added_file}" ]]; then
    LC_ALL=C sort -t $'\t' -k1,1 "${added_file}" -o "${added_file}"
  fi
  if [[ -s "${removed_file}" ]]; then
    LC_ALL=C sort -t $'\t' -k1,1 "${removed_file}" -o "${removed_file}"
  fi

  echo "Suppression baseline total: ${baseline_total}"
  echo "Current suppression total: ${current_total}"

  if (( added_total > 0 )); then
    echo "New suppressions: ${added_total}"
    local delta
    while IFS=$'\t' read -r key delta; do
      [[ -n "${key}" ]] || continue
      printf '  +%d %s\n' "${delta}" "$(describe_key "${key}")"
    done < "${added_file}"
  else
    echo "New suppressions: 0"
  fi

  if (( removed_total > 0 )); then
    echo "Removed suppressions: ${removed_total}"
    local delta
    while IFS=$'\t' read -r key delta; do
      [[ -n "${key}" ]] || continue
      printf '  -%d %s\n' "${delta}" "$(describe_key "${key}")"
    done < "${removed_file}"
  else
    echo "Removed suppressions: 0"
  fi

  if [[ "${fail_on_new}" == "true" && ${added_total} -gt 0 ]]; then
    echo "Suppression baseline check failed: new suppressions detected." >&2
    rm -rf "${temp_dir}"
    return 1
  fi

  rm -rf "${temp_dir}"
  return 0
}

main() {
  if [[ $# -lt 1 ]]; then
    usage_main >&2
    return 2
  fi

  local command="$1"
  shift

  case "${command}" in
    scan)
      command_scan "$@"
      ;;
    compare)
      command_compare "$@"
      ;;
    -h|--help)
      usage_main
      ;;
    *)
      echo "Unknown command: ${command}" >&2
      usage_main >&2
      return 2
      ;;
  esac
}

main "$@"
