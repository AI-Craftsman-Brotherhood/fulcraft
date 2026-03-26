# Prompt Customization

This document explains how to override the prompts used by `ful generate` and JUnit-oriented `ful run` on a per-project basis.

## Scope

- In scope: JUnit test generation prompts
- Out of scope: `ful document` prompts, which are currently classpath-fixed

## Lookup Order

When loading a template, FUL searches in this order:

1. `{projectRoot}/.ful/prompts/{fileName}`
2. An explicit path, either absolute or relative to the current working directory
3. The default classpath template under `app/src/main/resources/prompts/`

`{projectRoot}` is usually the target project passed through `-p`.

## Recommended Directory Layout

```text
your-project/
└── .ful/
    └── prompts/
        ├── default_generation.txt
        ├── default_fix.txt
        ├── default_runtime_fix.txt
        ├── fallback_stub.txt
        ├── high_complexity_generation.txt
        ├── split_phase1_validation.txt
        ├── split_phase2_happy_path.txt
        ├── split_phase3_edge_cases.txt
        └── few_shot/
            ├── builder.txt
            ├── data_class.txt
            ├── exception.txt
            ├── general.txt
            ├── inner_class.txt
            ├── service.txt
            └── utility.txt
```

## Templates You Can Override

| File name | Purpose |
|---|---|
| `default_generation.txt` | Standard generation prompt |
| `high_complexity_generation.txt` | Used for high-complexity methods under the `specialized_prompt` strategy |
| `split_phase1_validation.txt` | Split generation phase 1 |
| `split_phase2_happy_path.txt` | Split generation phase 2 |
| `split_phase3_edge_cases.txt` | Split generation phase 3 |
| `default_fix.txt` | Compile-fix prompt |
| `default_runtime_fix.txt` | Runtime-fix prompt |
| `fallback_stub.txt` | Final fallback stub |

## Few-Shot Overrides

You can override few-shot examples with files under `.ful/prompts/few_shot/`.

- `builder.txt`
- `data_class.txt`
- `exception.txt`
- `general.txt`
- `inner_class.txt`
- `service.txt`
- `utility.txt`

FUL chooses among them automatically based on class type. Nested types and anonymous classes prefer `inner_class.txt`.

## Minimal Workflow

1. Copy a default template.
   Copy `app/src/main/resources/prompts/default_generation.txt` to `{projectRoot}/.ful/prompts/default_generation.txt`.
2. Make one focused change first.
3. Run FUL and verify the behavior.
   Use `ful generate -p {projectRoot} ...` or `ful run -p {projectRoot} ...`.

## Editing Notes

- If you remove existing placeholders such as `{{source_code}}`, required input information is lost.
- Unknown placeholders are replaced with empty strings.
- Override resolution is based on file name, not full path.
  For example, even if you specify `prompts/default_generation.txt`, the effective override path is `.ful/prompts/default_generation.txt`.

## Current Limitations

As of 2026-02-11:

- `generation.prompt_template_path` is not wired into the standard JUnit generation path.
- `generation.few_shot.enabled`, `examples_dir`, `max_examples`, and `use_class_type_detection` are also not wired in.
- For actual customization, override files under `.ful/prompts/` instead of relying on `config` fields.

## References

- Default templates: `app/src/main/resources/prompts/`
- Overall design: [architecture.md](architecture.md)
