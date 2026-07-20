/**
 * Generate platform files from strings.json:
 *   - Apple: one Localizable.xcstrings (all languages nested).
 *   - KMP:   one values[-lang]/strings.xml per supported language.
 */
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import {
  APPLE_XCSTRINGS,
  kmpValuesDir,
  KMP_RESOURCES,
  STRINGS_JSON,
} from "../config";
import { renderAndroid, type ParsedAndroid } from "../lib/android-xml";
import { fromCanonical, type Flavor } from "../lib/placeholders";
import { renderXcstrings, type XcCatalog, type XcEntry } from "../lib/xcstrings";
import { targetsOf, type StringEntry, type StringsFile } from "../types";

function render(entry: StringEntry, text: string, flavor: Flavor): string {
  return fromCanonical(text, entry.args ?? [], flavor);
}

// ---- Apple ------------------------------------------------------------------

function buildXcstrings(doc: StringsFile): XcCatalog {
  const strings: Record<string, XcEntry> = {};

  for (const [key, entry] of Object.entries(doc.strings)) {
    if (!targetsOf(entry).includes("apple")) continue;
    const xc: XcEntry = { extractionState: "manual" };
    if (entry.context) xc.comment = entry.context;
    const localizations: XcEntry["localizations"] = {};

    for (const lang of doc.supportedLanguages) {
      const state = lang === doc.sourceLanguage ? "translated" : "needs_review";
      if (entry.plural) {
        const forms = entry.plural[lang];
        if (!forms) continue;
        const plural: Record<string, { stringUnit: { state: any; value: string } }> = {};
        for (const [cat, value] of Object.entries(forms)) {
          plural[cat] = { stringUnit: { state, value: render(entry, value, "apple") } };
        }
        localizations[lang] = { variations: { plural } };
      } else {
        const value = entry.translations?.[lang];
        if (value === undefined) continue;
        localizations[lang] = { stringUnit: { state, value: render(entry, value, "apple") } };
      }
    }

    if (Object.keys(localizations).length) xc.localizations = localizations;
    strings[key] = xc;
  }

  return { sourceLanguage: doc.sourceLanguage, strings, version: "1.0" };
}

// ---- KMP --------------------------------------------------------------------

function buildAndroid(doc: StringsFile, lang: string): ParsedAndroid {
  const out: ParsedAndroid = { strings: [], plurals: [] };

  for (const [key, entry] of Object.entries(doc.strings)) {
    if (!targetsOf(entry).includes("kmp")) continue;
    if (entry.plural) {
      const forms = entry.plural[lang];
      if (!forms) continue;
      const items: Record<string, string> = {};
      for (const [cat, value] of Object.entries(forms)) {
        items[cat] = render(entry, value, "android");
      }
      out.plurals.push({ name: key, items });
    } else {
      const value = entry.translations?.[lang];
      if (value === undefined) continue;
      out.strings.push({ name: key, value: render(entry, value, "android") });
    }
  }
  return out;
}

// ---- entry ------------------------------------------------------------------

export async function generate() {
  const doc = JSON.parse(await Bun.file(STRINGS_JSON).text()) as StringsFile;

  await Bun.write(APPLE_XCSTRINGS, renderXcstrings(buildXcstrings(doc)));
  console.log(`Wrote ${APPLE_XCSTRINGS}`);

  for (const lang of doc.supportedLanguages) {
    const dir = join(KMP_RESOURCES, kmpValuesDir(lang, doc.sourceLanguage));
    mkdirSync(dir, { recursive: true });
    const file = join(dir, "strings.xml");
    await Bun.write(file, renderAndroid(buildAndroid(doc, lang)));
    console.log(`Wrote ${file}`);
  }
}
