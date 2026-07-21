/**
 * One-time migration: fold the existing .xcstrings (Apple) and Compose
 * strings.xml (KMP) files into a single strings.json.
 *
 * - Placeholders are normalized to canonical `{argN}` tokens.
 * - KMP `<base>_one` / `<base>_other` key pairs are folded into one plural entry.
 * - `targets` is set from which platform(s) a key was found in.
 * Arg names (`arg1`, `count`, …) and `context` are best-effort; curate by hand after.
 */
import { readdirSync } from "node:fs";
import { join } from "node:path";
import { APPLE_XCSTRINGS, KMP_RESOURCES, STRINGS_JSON } from "../config";
import { parseAndroid } from "../lib/android-xml";
import { parseXcstrings, type XcLocalization } from "../lib/xcstrings";
import { toCanonical, type ParsedString } from "../lib/placeholders";
import type { Arg, PluralForms, StringEntry, StringsFile, Target } from "../types";

const PLURAL_SUFFIXES = ["zero", "one", "two", "few", "many", "other"] as const;

interface Draft {
  targets: Set<Target>;
  args?: Arg[];
  translations?: Record<string, string>;
  plural?: Record<string, PluralForms>;
  comment?: string;
}

/** Give a friendlier name to a lone integer arg (common for counts). */
function prettify(parsed: ParsedString): ParsedString {
  if (parsed.args.length === 1 && parsed.args[0]!.type === "int") {
    return {
      text: parsed.text.replaceAll("{arg1}", "{count}"),
      args: [{ name: "count", type: "int" }],
    };
  }
  return parsed;
}

function ingest(draft: Draft, lang: string, raw: string) {
  const p = prettify(toCanonical(raw));
  if (p.args.length) draft.args = p.args;
  (draft.translations ??= {})[lang] = p.text;
}

// ---- Apple (.xcstrings) -----------------------------------------------------

function unitValue(loc: XcLocalization | undefined): string | undefined {
  return loc?.stringUnit?.value;
}

async function readApple(drafts: Map<string, Draft>) {
  const cat = parseXcstrings(await Bun.file(APPLE_XCSTRINGS).text());
  for (const [key, entry] of Object.entries(cat.strings)) {
    const draft = get(drafts, key);
    draft.targets.add("apple");
    if (entry.comment) draft.comment = entry.comment;
    for (const [lang, loc] of Object.entries(entry.localizations ?? {})) {
      const plural = loc.variations?.plural;
      if (plural) {
        for (const [cat, unit] of Object.entries(plural)) {
          const p = prettify(toCanonical(unit.stringUnit.value));
          if (p.args.length) draft.args = p.args;
          ((draft.plural ??= {})[lang] ??= { other: "" } as PluralForms)[
            cat as keyof PluralForms
          ] = p.text;
        }
      } else {
        const v = unitValue(loc);
        if (v !== undefined) ingest(draft, lang, v);
      }
    }
  }
}

// ---- KMP (compose strings.xml) ---------------------------------------------

function pluralBaseOf(name: string): { base: string; cat: string } | null {
  for (const suf of PLURAL_SUFFIXES) {
    if (name.endsWith(`_${suf}`)) return { base: name.slice(0, -(suf.length + 1)), cat: suf };
  }
  return null;
}

async function readKmp(drafts: Map<string, Draft>, sourceLanguage: string) {
  const dirs = readdirSync(KMP_RESOURCES, { withFileTypes: true })
    .filter((d) => d.isDirectory() && (d.name === "values" || d.name.startsWith("values-")))
    .map((d) => d.name);

  for (const dir of dirs) {
    const lang = dir === "values" ? sourceLanguage : dir.slice("values-".length);
    const file = join(KMP_RESOURCES, dir, "strings.xml");
    if (!(await Bun.file(file).exists())) continue;

    const { strings, plurals } = parseAndroid(await Bun.file(file).text());
    for (const s of strings) {
      const draft = get(drafts, s.name);
      draft.targets.add("kmp");
      ingest(draft, lang, s.value);
    }
    // Real <plurals> blocks (if any) fold directly into the base key.
    for (const p of plurals) {
      const draft = get(drafts, p.name);
      draft.targets.add("kmp");
      for (const [cat, raw] of Object.entries(p.items)) {
        const parsed = prettify(toCanonical(raw));
        if (parsed.args.length) draft.args = parsed.args;
        ((draft.plural ??= {})[lang] ??= { other: "" } as PluralForms)[
          cat as keyof PluralForms
        ] = parsed.text;
      }
    }
  }
}

/**
 * Global fold: collapse flat `<base>_<cat>` sibling keys (e.g. `_one`/`_other`)
 * into one plural entry on `<base>`, regardless of which platform they came from.
 * Requires `other` plus at least one more category to avoid folding non-plurals.
 */
function foldPlurals(drafts: Map<string, Draft>) {
  const groups = new Map<string, string[]>();
  for (const key of drafts.keys()) {
    const pl = pluralBaseOf(key);
    if (pl && drafts.get(key)!.translations) {
      (groups.get(pl.base) ?? groups.set(pl.base, []).get(pl.base)!).push(key);
    }
  }

  for (const [base, keys] of groups) {
    const cats = new Set(keys.map((k) => pluralBaseOf(k)!.cat));
    if (!cats.has("other") || cats.size < 2) continue;

    const target = get(drafts, base);
    for (const key of keys) {
      const cat = pluralBaseOf(key)!.cat as keyof PluralForms;
      const src = drafts.get(key)!;
      src.targets.forEach((t) => target.targets.add(t));
      if (src.args?.length) target.args = src.args;
      target.comment ??= src.comment;
      for (const [lang, text] of Object.entries(src.translations ?? {})) {
        ((target.plural ??= {})[lang] ??= { other: "" } as PluralForms)[cat] = text;
      }
      drafts.delete(key);
    }
  }
}

// ---- assembly ---------------------------------------------------------------

function get(drafts: Map<string, Draft>, key: string): Draft {
  let d = drafts.get(key);
  if (!d) drafts.set(key, (d = { targets: new Set() }));
  return d;
}

export async function migrate(sourceLanguage = "en") {
  const drafts = new Map<string, Draft>();
  await readApple(drafts);
  await readKmp(drafts, sourceLanguage);
  foldPlurals(drafts);

  const langs = new Set<string>([sourceLanguage]);
  const strings: Record<string, StringEntry> = {};

  for (const key of [...drafts.keys()].sort()) {
    const d = drafts.get(key)!;
    const entry: StringEntry = { context: d.comment ?? "TODO: describe where this appears." };

    const targets = [...d.targets].sort() as Target[];
    if (targets.length && targets.length < 2) entry.targets = targets;
    if (d.args?.length) entry.args = d.args;

    if (d.plural) {
      entry.plural = d.plural;
      Object.keys(d.plural).forEach((l) => langs.add(l));
    } else if (d.translations) {
      entry.translations = d.translations;
      Object.keys(d.translations).forEach((l) => langs.add(l));
    }
    strings[key] = entry;
  }

  const out: StringsFile = {
    sourceLanguage,
    supportedLanguages: [...langs].sort(),
    strings,
  };
  await Bun.write(
    STRINGS_JSON,
    JSON.stringify({ $schema: "./schema.json", ...out }, null, 2) + "\n",
  );
  console.log(
    `Wrote ${STRINGS_JSON}\n  ${Object.keys(strings).length} keys, languages: ${[...langs].sort().join(", ")}`,
  );
}
