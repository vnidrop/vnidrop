/**
 * Canonical placeholder handling.
 *
 * Canonical form (in strings.json) uses named tokens: `{count}`, `{name}`.
 * Platform output uses positional printf tokens, resolved from the entry's
 * ordered `args`:
 *   - Apple (.xcstrings):  string -> %N$@   int -> %N$d   double -> %N$f
 *   - Android (.xml):      string -> %N$s   int -> %N$d   double -> %N$f
 * A literal percent is written `%%` on both platforms whenever the string
 * carries args (printf-active); canonical text stores it as a bare `%`.
 */
import type { Arg, ArgType } from "../types";

export type Flavor = "apple" | "android";

function stringToken(flavor: Flavor): string {
  return flavor === "apple" ? "@" : "s";
}

function tokenForType(type: ArgType, flavor: Flavor): string {
  switch (type) {
    case "string":
      return stringToken(flavor);
    case "int":
      return "d";
    case "double":
      return "f";
  }
}

/** Render canonical `{name}` text into a platform printf string. */
export function fromCanonical(
  text: string,
  args: Arg[],
  flavor: Flavor,
): string {
  const hasArgs = args.length > 0;
  const index = new Map(args.map((a, i) => [a.name, { i: i + 1, type: a.type }]));

  // Escape literal % first (only meaningful when printf-active).
  let out = hasArgs ? text.replaceAll("%", "%%") : text;

  out = out.replace(/\{([A-Za-z_][A-Za-z0-9_]*)\}/g, (_whole, name: string) => {
    const found = index.get(name);
    if (!found) {
      throw new Error(
        `placeholder {${name}} has no matching arg (${args.map((a) => a.name).join(", ") || "none"})`,
      );
    }
    return `%${found.i}$${tokenForType(found.type, flavor)}`;
  });

  return out;
}

export interface ParsedString {
  /** Canonical text with `{argN}` tokens. */
  text: string;
  /** Args discovered, ordered by first positional index. */
  args: Arg[];
}

// Matches an escaped literal `%%` or a printf specifier (positional or bare).
const CONV = /%%|%(?:(\d+)\$)?([@sdif])/g;

function typeForConv(conv: string): ArgType {
  if (conv === "@" || conv === "s") return "string";
  if (conv === "d" || conv === "i") return "int";
  return "double"; // f
}

/**
 * Parse a platform printf string back into canonical `{argN}` form, inferring
 * arg names (`arg1`, `arg2`, ...) and types from the conversion specifiers.
 * Handles positional (`%1$@`), bare (`%@`), and escaped literals (`%%`).
 */
export function toCanonical(raw: string): ParsedString {
  const byIndex = new Map<number, ArgType>();
  let auto = 0;

  const text = raw.replace(CONV, (whole, pos: string | undefined, conv: string) => {
    if (whole === "%%") return "%"; // literal percent, not a specifier
    const idx = pos ? Number(pos) : ++auto;
    const type = typeForConv(conv);
    const existing = byIndex.get(idx);
    if (existing && existing !== type) {
      throw new Error(`arg ${idx} used with conflicting types in "${raw}"`);
    }
    byIndex.set(idx, type);
    return `{arg${idx}}`;
  });

  const args: Arg[] = [...byIndex.keys()]
    .sort((a, b) => a - b)
    .map((idx) => ({ name: `arg${idx}`, type: byIndex.get(idx)! }));

  return { text, args };
}
