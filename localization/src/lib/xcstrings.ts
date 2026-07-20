/** Read/write Apple String Catalog (.xcstrings) — a single JSON file, all languages nested. */

export type XcState = "new" | "translated" | "needs_review" | "stale";

export interface XcStringUnit {
  state: XcState;
  value: string;
}
export interface XcVariations {
  plural?: Record<string, { stringUnit: XcStringUnit }>;
}
export interface XcLocalization {
  stringUnit?: XcStringUnit;
  variations?: XcVariations;
}
export interface XcEntry {
  comment?: string;
  extractionState?: string;
  localizations?: Record<string, XcLocalization>;
}
export interface XcCatalog {
  sourceLanguage: string;
  strings: Record<string, XcEntry>;
  version: string;
}

export function parseXcstrings(json: string): XcCatalog {
  return JSON.parse(json) as XcCatalog;
}

/** Stable, Xcode-compatible serialization: 2-space indent, keys sorted, trailing newline. */
export function renderXcstrings(cat: XcCatalog): string {
  const sortDeep = (v: unknown): unknown => {
    if (Array.isArray(v)) return v.map(sortDeep);
    if (v && typeof v === "object") {
      const out: Record<string, unknown> = {};
      for (const k of Object.keys(v as Record<string, unknown>).sort()) {
        out[k] = sortDeep((v as Record<string, unknown>)[k]);
      }
      return out;
    }
    return v;
  };
  return JSON.stringify(sortDeep(cat), null, 2) + "\n";
}
