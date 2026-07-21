/**
 * Read/write Compose Multiplatform (Android-style) strings.xml.
 * The existing file is flat and machine-friendly, so we use a targeted parser
 * rather than a full DOM. Handles <string> and <plurals> with XML entities and
 * Android backslash escapes.
 */

export interface AndroidString {
  name: string;
  value: string;
}
export interface AndroidPlural {
  name: string;
  items: Record<string, string>; // quantity -> value
}

export interface ParsedAndroid {
  strings: AndroidString[];
  plurals: AndroidPlural[];
}

function decodeEntities(s: string): string {
  return s
    .replace(/&#x([0-9a-fA-F]+);/g, (_m, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_m, d) => String.fromCodePoint(parseInt(d, 10)))
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", '"')
    .replaceAll("&apos;", "'")
    .replaceAll("&amp;", "&");
}

/** Reverse Android's leading/trailing-quote + backslash escapes into plain text. */
function unescapeAndroid(raw: string): string {
  let s = raw.trim();
  if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) {
    s = s.slice(1, -1);
  }
  s = s
    .replace(/\\n/g, "\n")
    .replace(/\\t/g, "\t")
    .replace(/\\'/g, "'")
    .replace(/\\"/g, '"')
    .replace(/\\@/g, "@")
    .replace(/\\\\/g, "\\");
  return decodeEntities(s);
}

export function parseAndroid(xml: string): ParsedAndroid {
  const strings: AndroidString[] = [];
  const plurals: AndroidPlural[] = [];

  const stringRe = /<string\s+name="([^"]+)"\s*>([\s\S]*?)<\/string>/g;
  for (const m of xml.matchAll(stringRe)) {
    strings.push({ name: m[1]!, value: unescapeAndroid(m[2]!) });
  }

  const pluralRe = /<plurals\s+name="([^"]+)"\s*>([\s\S]*?)<\/plurals>/g;
  const itemRe = /<item\s+quantity="([^"]+)"\s*>([\s\S]*?)<\/item>/g;
  for (const m of xml.matchAll(pluralRe)) {
    const items: Record<string, string> = {};
    for (const it of m[2]!.matchAll(itemRe)) {
      items[it[1]!] = unescapeAndroid(it[2]!);
    }
    plurals.push({ name: m[1]!, items });
  }

  return { strings, plurals };
}

function escapeXmlText(s: string): string {
  return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

/** Android needs apostrophes and quotes escaped, and quotes on leading/trailing whitespace. */
function escapeAndroidValue(s: string): string {
  let out = escapeXmlText(s).replace(/'/g, "\\'").replace(/\n/g, "\\n");
  if (out !== out.trim()) out = `"${out}"`;
  return out;
}

export function renderAndroid(parsed: ParsedAndroid): string {
  const lines: string[] = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"];
  for (const s of parsed.strings) {
    lines.push(`    <string name="${s.name}">${escapeAndroidValue(s.value)}</string>`);
  }
  for (const p of parsed.plurals) {
    lines.push(`    <plurals name="${p.name}">`);
    for (const [q, v] of Object.entries(p.items)) {
      lines.push(`        <item quantity="${q}">${escapeAndroidValue(v)}</item>`);
    }
    lines.push("    </plurals>");
  }
  lines.push("</resources>", "");
  return lines.join("\n");
}
