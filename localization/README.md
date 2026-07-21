# Localization

Single source of truth for every user-facing string, in [`strings.json`](strings.json).
A Bun CLI generates the platform-native files from it:

| Target | Output | Notes |
| --- | --- | --- |
| `apple` | `apple/VniDrop/Resources/Localizable.xcstrings` | one catalog, all languages nested |
| `kmp` | `shared/src/commonMain/composeResources/values[-lang]/strings.xml` | one file per language |

## Workflow

```bash
# From the repository root:
make check-localization      # structural checks (run before committing)
make localization            # regenerate .xcstrings + strings.xml from strings.json
make localization-migrate    # one-time: rebuild strings.json from platform files
```

**Never edit the generated `.xcstrings` / `strings.xml` by hand** — edit `strings.json` and
regenerate. Regenerated output is deterministic (sorted keys), so diffs stay small.

## `strings.json` format

```jsonc
{
  "sourceLanguage": "en",
  "supportedLanguages": ["en", "fr"],
  "strings": {
    "send_title": {
      "context": "Send tab — screen title.",
      "translations": { "en": "Send", "fr": "Envoyer" }
    },

    "send_selected_files_count": {
      "context": "Send flow — number of files chosen before creating a transfer.",
      "targets": ["kmp", "apple"],
      "args": [{ "name": "count", "type": "int" }],
      "plural": {
        "en": { "one": "{count} file selected", "other": "{count} files selected" },
        "fr": { "one": "{count} fichier sélectionné", "other": "{count} fichiers sélectionnés" }
      }
    }
  }
}
```

### Fields

- **`context`** *(required)* — where the string appears and its purpose. Emitted as the
  `.xcstrings` comment and an XML comment; also the note translators see.
- **`targets`** *(optional)* — `["kmp", "apple"]`. Omit to mean **all** targets.
- **`args`** *(optional)* — ordered list of `{ name, type }`, `type` ∈ `string | int | double`.
  Referenced in text as `{name}`.
- **`translations`** — flat text per language. **Mutually exclusive with `plural`.**
- **`plural`** — per language, per [CLDR category](https://cldr.unicode.org/index/cldr-spec/plural-rules)
  (`zero`, `one`, `two`, `few`, `many`, `other`). `other` is always required.

### Placeholders

Write named tokens `{count}`, `{name}` in text. The generator converts them to the right
positional token per platform, using the declared `type`:

| type | Apple | Android/KMP |
| --- | --- | --- |
| `string` | `%N$@` | `%N$s` |
| `int` | `%N$d` | `%N$d` |
| `double` | `%N$f` | `%N$f` |

A literal `%` in text is emitted as `%%` whenever the string has args.

## Adding a language

Add its code to `supportedLanguages`, fill in `translations` / `plural` for each key, then
`generate`. KMP gets a new `values-<lang>/strings.xml`; Apple gets the language inside the
single catalog. `validate` warns about any key still missing that language.

## Migration notes (from the initial import)

- Apple keys that were literal English strings (`"%@ · %@"`) were imported verbatim — rename
  them to semantic keys and update the Swift call sites.
- Arg names default to `arg1`, `arg2`… (a lone int arg becomes `count`). Rename for clarity;
  keep the `{token}` in text in sync.
- Folding `transfer_file_count_one` / `_other` into the plural key `transfer_file_count`
  requires switching the KMP call site from `Res.string.transfer_file_count_one` to the
  Compose plural API (`pluralStringResource(Res.plurals.transfer_file_count, count, count)`),
  and the Apple side to automatic plural inflection.
