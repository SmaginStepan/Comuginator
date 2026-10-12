import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { COLORS, HOME, CORE, FOLDERS } from "./vocab.mjs";

const OUT = process.argv[2] ?? ".";
const CACHE_FILE = `${OUT}/arasaac-cache.json`;
const cache = existsSync(CACHE_FILE) ? JSON.parse(readFileSync(CACHE_FILE, "utf8")) : {};

const norm = (s) => s.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function lookup(query) {
  if (cache[query]) return cache[query];
  const url = `https://api.arasaac.org/v1/pictograms/es/bestsearch/${encodeURIComponent(query)}`;
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      const res = await fetch(url);
      if (res.status === 404) { cache[query] = { id: null, matched: null, exact: false }; return cache[query]; }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const list = await res.json();
      const q = norm(query);
      const keywordsOf = (p) => (p.keywords ?? []).map((k) => norm(k.keyword ?? ""));
      const exact = list.find((p) => keywordsOf(p).includes(q));
      const pick = exact ?? list[0];
      cache[query] = pick
        ? { id: pick._id, matched: (pick.keywords?.[0]?.keyword ?? ""), exact: !!exact,
            kw: (pick.keywords ?? []).slice(0, 3).map((k) => k.keyword).join("/"),
            cats: (pick.categories ?? []).slice(0, 3).join(",") }
        : { id: null, matched: null, exact: false };
      await sleep(120);
      return cache[query];
    } catch (e) {
      await sleep(800 * (attempt + 1));
      if (attempt === 3) { console.error("FAILED", query, e.message); cache[query] = { id: null, matched: null, exact: false }; return cache[query]; }
    }
  }
}

// ── parse the vocabulary source ────────────────────────────────────────────
function parseWord(line, defaultColor) {
  let rest = line.trim();
  let color = defaultColor;
  const m = rest.match(/^\[([A-Z])\]\s*/);
  if (m) { color = m[1]; rest = rest.slice(m[0].length); }
  const parts = rest.split("|").map((s) => s.trim());
  let query = null;
  const labels = [];
  for (const p of parts) {
    if (p.startsWith("q=")) query = p.slice(2).trim(); else labels.push(p);
  }
  const [es, en, ru] = labels;
  return { es, en, ru, color: COLORS[color], query: query ?? es };
}

const rows = (text) => text.split("\n").map((l) => l.trim()).filter(Boolean);

// Manual fixes: words with no good pictogram are dropped; ambiguous ones are pinned
// to a reviewed ARASAAC id (scope = "home" or the folder key; plain "es" matches anywhere).
const DROP = new Set(["levantarse", "de nada", "es tu turno", "déjame", "frustrado", "que", "todavía", "solo@palabritas", "casi", "para qué", "cambiarse", "pronto", "a@palabritas", "si@palabritas"]);
const PIN = {
  "naranja@comida": 2483, "naranja@colores": 2888, "rosa@colores": 2807,
  sentarse: 6611, "vestirse": 2781, callado: 5936, "no sé": 7180, "me gusta": 2418,
  "tengo sueño": 8513, "sueño@estados": 8513, "celoso": 36911, "parque de atracciones": 4704,
  "mañana@tiempo": 38278, "por la mañana": 25704, "qué es esto": 22620,
  "pasta de dientes": 2858, "te quiero": 8020, "quiero ir al baño": 15810,
  // second audit pass: best match had the wrong sense
  qué: 22620, cómo: 22619, papá: 31146, tableta: 9165, examen: 38462, letra: 34361,
  música: 24791, triángulo: 4763, "pollo@comida": 4952, poner: 32757, saber: 6993,
};
const QUERY = {
  mira: "mirar", escucha: "escuchar", "ven aquí": "venir", "no me gusta": "no gustar", quiero: "querer",
  "más despacio": "despacio", repite: "repetir", enhorabuena: "felicidades", "qué bien": "genial",
  "dolor de barriga": "dolor de estómago", "tarde@tiempo": "tarde",
};
const OVERRIDE_LABEL ={ celoso: { es: "envidia", en: "envy", ru: "зависть" } };
const isDropped = (w, scope) => DROP.has(w.es) || DROP.has(`${w.es}@${scope}`);
const applyPin = (w, scope) => {
  const id = PIN[`${w.es}@${scope}`] ?? PIN[w.es];
  if (id) w.pin = id;
  const q = QUERY[`${w.es}@${scope}`] ?? QUERY[w.es];
  if (q) w.query = q;
  if (OVERRIDE_LABEL[w.es]) Object.assign(w, OVERRIDE_LABEL[w.es]);
  return w;
};

const home = [];
for (const line of rows(CORE)) {
  const m = line.match(/^(\d+),(\d+)\s+(.*)$/);
  const w = applyPin(parseWord(m[3], null), "home");
  home.push({ ...w, row: +m[1], col: +m[2], type: "WORD" });
}

const FOLDER_COLS = 8;
const folders = FOLDERS.map((f) => {
  const [r, c] = f.at.split(",").map(Number);
  const words = rows(f.words).map((l) => applyPin(parseWord(l, f.color), f.key)).filter((w) => !isDropped(w, f.key));
  words.forEach((w, i) => { w.row = Math.floor(i / FOLDER_COLS); w.col = i % FOLDER_COLS; w.type = "WORD"; });
  return { key: f.key, row: r, col: c, label: { es: f.es, en: f.en, ru: f.ru }, words, query: f.es };
});

// ── resolve pictograms ─────────────────────────────────────────────────────
const FOLDER_QUERY = {
  gente: "personas", cosas: "objetos", comida: "comida", sitios: "lugares", acciones: "acción",
  describir: "describir", hablar: "hablar", ayuda: "ayuda", colegio: "colegio", actividades: "actividades",
  estados: "emociones", diversion: "fiesta", tiempo: "tiempo", palabritas: "palabras", preguntas: "pregunta",
  cuerpo: "cuerpo", ropa: "ropa", animales: "animales", casa: "casa", colores: "colores",
};

const all = [...home, ...folders.flatMap((f) => f.words)];
let done = 0;
for (const w of all) {
  w.pic = w.pin ? { id: w.pin, matched: "(pinned)", exact: true, kw: "(pinned)", cats: "" } : await lookup(w.query);
  if (++done % 50 === 0) { console.log(`looked up ${done}/${all.length + folders.length}`); writeFileSync(CACHE_FILE, JSON.stringify(cache)); }
}
// Folder pictograms the search could not find (or finds badly), pinned by ARASAAC id.
const FOLDER_PIN = { describir: 32584, estados: 11476, tiempo: 3071 };
for (const f of folders) {
  f.pic = FOLDER_PIN[f.key]
    ? { id: FOLDER_PIN[f.key], matched: "(pinned)", exact: true, kw: "(pinned)", cats: "" }
    : await lookup(FOLDER_QUERY[f.key] ?? f.query);
  done++;
}
writeFileSync(CACHE_FILE, JSON.stringify(cache));

// ── write the preset JSON ──────────────────────────────────────────────────
const cell = (w) => ({
  type: w.type, row: w.row, col: w.col, color: w.color ?? "FOLDER",
  arasaacId: w.pic?.id ?? null,
  label: { es: w.es, en: w.en, ru: w.ru },
});
const preset = {
  id: "starter-core", version: 1,
  name: { es: "Vocabulario básico", en: "Starter vocabulary", ru: "Базовый словарь" },
  languages: ["es", "en", "ru"],
  license: "Pictograms: ARASAAC (Gobierno de Aragón), CC BY-NC-SA 4.0, https://arasaac.org. Word lists and layout: original work.",
  colors: Object.values(COLORS).concat("FOLDER"),
  root: {
    cols: HOME.cols, rows: HOME.rows,
    cells: [
      ...home.map(cell),
      ...folders.map((f) => ({
        type: "MENU", row: f.row, col: f.col, color: "FOLDER", arasaacId: f.pic?.id ?? null,
        label: f.label,
        page: { cols: FOLDER_COLS, rows: Math.ceil(f.words.length / FOLDER_COLS), cells: f.words.map(cell) },
      })),
    ],
  },
};
writeFileSync(`${OUT}/conversation-starter.json`, JSON.stringify(preset, null, 1));

// ── review reports ─────────────────────────────────────────────────────────
const missing = [...all, ...folders].filter((w) => !w.pic?.id);
if (missing.length) {
  console.error("ERROR: cards without a pictogram:", missing.map((w) => w.es ?? w.label?.es).join(", "));
  process.exitCode = 1;
}
const weak = all.filter((w) => w.pic?.id && !w.pic.exact);
const stats = `cards: ${all.length + folders.length}  (core ${home.length}, folders ${folders.length}, in folders ${folders.reduce((n, f) => n + f.words.length, 0)})\nno pictogram: ${missing.length}\nnot an exact keyword match: ${weak.length}`;
console.log(stats);
const audit = [...home.map((w) => ["home", w]), ...folders.flatMap((f) => f.words.map((w) => [f.key, w]))]
  .map(([s, w]) => `${s}|${w.es}|${w.pic?.id ?? "-"}|${w.pic?.kw ?? ""}|${w.pic?.cats ?? ""}`).join("\n");
writeFileSync(`${OUT}/audit.txt`, audit);
writeFileSync(`${OUT}/review.txt`,
  stats + "\n\n== NO PICTOGRAM ==\n" + missing.map((w) => `${w.es} (q=${w.query})`).join("\n") +
  "\n\n== NOT EXACT (check the picture) ==\n" + weak.map((w) => `${w.es} (q=${w.query}) -> ${w.pic.id} "${w.pic.matched}"`).join("\n"));

const TINT = { PRONOUN: "#FFF2A8", VERB: "#BFE8B0", NOUN: "#FFD9A8", DESCRIPTOR: "#BCDDFB", ADVERB: "#E2CBB3", PREPOSITION: "#F6C6E2", QUESTION: "#D9C7F2", NEGATION: "#F7B7B7", SOCIAL: "#FBC1D8", MISC: "#F0F0F0", FOLDER: "#FFF3D6" };
const img = (id) => (id ? `https://static.arasaac.org/pictograms/${id}/${id}_300.png` : "");
const card = (w, size = 92) => `<div class="card" style="background:${TINT[w.color ?? "FOLDER"]};width:${size}px"><img loading="lazy" src="${img(w.pic?.id)}" alt=""><b>${w.es ?? w.label?.es}</b><i>${w.en ?? w.label?.en}</i><u>${w.ru ?? w.label?.ru}</u>${w.pic?.id && !w.pic.exact ? '<span class="warn">?</span>' : ""}${!w.pic?.id ? '<span class="warn">✗</span>' : ""}</div>`;
const grid = (items, cols, rowsN) => {
  const map = new Map(items.map((w) => [`${w.row},${w.col}`, w]));
  let html = `<div class="grid" style="grid-template-columns:repeat(${cols},92px)">`;
  for (let r = 0; r < rowsN; r++) for (let c = 0; c < cols; c++) { const w = map.get(`${r},${c}`); html += w ? card(w) : `<div class="empty"></div>`; }
  return html + "</div>";
};
const homeItems = [...home, ...folders.map((f) => ({ ...f, es: f.label.es, en: f.label.en, ru: f.label.ru, color: "FOLDER" }))];
let html = `<!doctype html><meta charset="utf-8"><title>Starter vocabulary preview</title><style>
body{font:13px system-ui;margin:16px;background:#fafafa;color:#222}h2{margin:28px 0 8px}
.grid{display:grid;gap:5px}.card{border:1px solid #999;border-radius:7px;padding:3px;text-align:center;position:relative;min-height:112px}
.card img{width:64px;height:64px;object-fit:contain;background:#fff;border-radius:4px}.card b{display:block;font-size:12px}.card i{display:block;font-size:10px;color:#444}.card u{display:block;font-size:10px;text-decoration:none;color:#666}
.empty{min-height:112px}.warn{position:absolute;top:1px;right:4px;color:#c00;font-weight:700}.legend span{display:inline-block;padding:2px 8px;margin:2px;border-radius:5px;border:1px solid #999}
</style><h1>Starter vocabulary: ${stats.replace(/\n/g, " · ")}</h1>
<p>Red ? = pictogram is not an exact keyword match, check it. Red ✗ = none found.</p>
<div class="legend">${Object.entries(TINT).map(([k, v]) => `<span style="background:${v}">${k}</span>`).join("")}</div>
<h2>Home page (${HOME.cols} × ${HOME.rows}, fixed positions)</h2>${grid(homeItems, HOME.cols, HOME.rows)}`;
for (const f of folders) html += `<h2>${f.label.es} / ${f.label.en} / ${f.label.ru} (${f.words.length})</h2>${grid(f.words, FOLDER_COLS, Math.ceil(f.words.length / FOLDER_COLS))}`;
writeFileSync(`${OUT}/preview.html`, html);
console.log("written");
