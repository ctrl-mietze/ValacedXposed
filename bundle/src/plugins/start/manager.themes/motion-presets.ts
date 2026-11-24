/** Motion presets shared conceptually with the native preview and asset renderer.
 * Original Veyra designs; no remote backgrounds or media permissions. */
export type MotionPreset = {
    id:string;name:string;description:string;base:string;glowA:string;glowB:string;accent:string;
    duration:number;motion:'drift'|'pulse'|'orbit'|'sweep';dim:number;
    palette:import('./palettes').DiscordPalette
}
export const motionPresets: MotionPreset[] = [
  {
    "id": "aurora",
    "name": "Aurora",
    "description": "Violett und Mint · schwebendes Nordlicht",
    "base": "#080b17",
    "glowA": "#7453c7",
    "glowB": "#187f73",
    "accent": "#99daca",
    "duration": 7200,
    "motion": "drift",
    "palette": {
      "id": "motion-aurora",
      "name": "Veyra Aurora · Colors",
      "chat": "#080b17",
      "sidebar": "#161925",
      "header": "#1c202b",
      "input": "#232732",
      "elevated": "#313643",
      "accent": "#99daca",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#3b4b51",
      "selected": "#3d3660"
    },
    "dim": 0.38
  },
  {
    "id": "bloom",
    "name": "Bloom Motion",
    "description": "Rosé und Flieder · weiche Blütenlichter",
    "base": "#110a15",
    "glowA": "#9c416f",
    "glowB": "#54308b",
    "accent": "#f6b9d6",
    "duration": 8400,
    "motion": "pulse",
    "palette": {
      "id": "motion-bloom",
      "name": "Veyra Bloom Motion · Colors",
      "chat": "#110a15",
      "sidebar": "#1b1924",
      "header": "#1f202b",
      "input": "#232732",
      "elevated": "#323643",
      "accent": "#f6b9d6",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#4d4453",
      "selected": "#493046"
    },
    "dim": 0.38
  },
  {
    "id": "eclipse",
    "name": "Eclipse Motion",
    "description": "Mitternachtsblau und Violett · langsame Umlaufbahn",
    "base": "#080b14",
    "glowA": "#3152a0",
    "glowB": "#6042ab",
    "accent": "#b2c1ff",
    "duration": 10000,
    "motion": "orbit",
    "palette": {
      "id": "motion-eclipse",
      "name": "Veyra Eclipse Motion · Colors",
      "chat": "#080b14",
      "sidebar": "#161923",
      "header": "#1c202a",
      "input": "#232731",
      "elevated": "#313643",
      "accent": "#b2c1ff",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#40465b",
      "selected": "#293554"
    },
    "dim": 0.38
  },
  {
    "id": "crimson",
    "name": "Crimson Red",
    "description": "Tiefes Rot · wandernde Glut",
    "base": "#100608",
    "glowA": "#b82742",
    "glowB": "#641018",
    "accent": "#ff9eae",
    "duration": 6600,
    "motion": "sweep",
    "palette": {
      "id": "motion-crimson",
      "name": "Veyra Crimson Red · Colors",
      "chat": "#100608",
      "sidebar": "#1a171d",
      "header": "#1f1f27",
      "input": "#232630",
      "elevated": "#313542",
      "accent": "#ff9eae",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#4e3f4b",
      "selected": "#512838"
    },
    "dim": 0.38
  },
  {
    "id": "rose",
    "name": "Rose Silk",
    "description": "Rosa und Roségold · fließender Seidenschimmer",
    "base": "#130b13",
    "glowA": "#b74783",
    "glowB": "#7e385d",
    "accent": "#ffc0de",
    "duration": 8200,
    "motion": "drift",
    "palette": {
      "id": "motion-rose",
      "name": "Veyra Rose Silk · Colors",
      "chat": "#130b13",
      "sidebar": "#1c1923",
      "header": "#20202a",
      "input": "#242731",
      "elevated": "#323642",
      "accent": "#ffc0de",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#4e4654",
      "selected": "#51324c"
    },
    "dim": 0.38
  },
  {
    "id": "purple",
    "name": "Purple Haze",
    "description": "Violett und Amethyst · pulsierende Nebel",
    "base": "#0c0718",
    "glowA": "#753fc6",
    "glowB": "#492294",
    "accent": "#d4b9ff",
    "duration": 7600,
    "motion": "pulse",
    "palette": {
      "id": "motion-purple",
      "name": "Veyra Purple Haze · Colors",
      "chat": "#0c0718",
      "sidebar": "#181725",
      "header": "#1e1f2c",
      "input": "#232632",
      "elevated": "#313643",
      "accent": "#d4b9ff",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#46445b",
      "selected": "#3d3060"
    },
    "dim": 0.38
  },
  {
    "id": "ruby",
    "name": "Rubin Red",
    "description": "Schwarz und Rubinrot · tiefe, edle Lichtreflexe",
    "base": "#080306",
    "glowA": "#a80c35",
    "glowB": "#620b3a",
    "accent": "#ff93b3",
    "duration": 9400,
    "motion": "orbit",
    "palette": {
      "id": "motion-ruby",
      "name": "Veyra Rubin Red · Colors",
      "chat": "#080306",
      "sidebar": "#16151c",
      "header": "#1c1e26",
      "input": "#232630",
      "elevated": "#313542",
      "accent": "#ff93b3",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#4e3d4c",
      "selected": "#4c2034"
    },
    "dim": 0.38
  },
  {
    "id": "sapphire",
    "name": "Sapphire Tide",
    "description": "Kobaltblau und Eisblau · ozeanische Wellen",
    "base": "#060b16",
    "glowA": "#245dcb",
    "glowB": "#126477",
    "accent": "#a4d4ff",
    "duration": 9000,
    "motion": "sweep",
    "palette": {
      "id": "motion-sapphire",
      "name": "Veyra Sapphire Tide · Colors",
      "chat": "#060b16",
      "sidebar": "#151924",
      "header": "#1c202b",
      "input": "#232732",
      "elevated": "#313643",
      "accent": "#a4d4ff",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#3d495b",
      "selected": "#253961"
    },
    "dim": 0.38
  },
  {
    "id": "emerald",
    "name": "Emerald Forest",
    "description": "Smaragd und Türkis · wanderndes Waldlicht",
    "base": "#060e0c",
    "glowA": "#137d5b",
    "glowB": "#17685f",
    "accent": "#8eebc7",
    "duration": 8700,
    "motion": "drift",
    "palette": {
      "id": "motion-emerald",
      "name": "Veyra Emerald Forest · Colors",
      "chat": "#060e0c",
      "sidebar": "#151b1f",
      "header": "#1c2128",
      "input": "#232731",
      "elevated": "#313642",
      "accent": "#8eebc7",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#394e50",
      "selected": "#204240"
    },
    "dim": 0.38
  },
  {
    "id": "solar",
    "name": "Solar Gold",
    "description": "Gold und dunkles Bernstein · warme Lichtpulse",
    "base": "#110d07",
    "glowA": "#a56a18",
    "glowB": "#804324",
    "accent": "#ffda91",
    "duration": 9600,
    "motion": "pulse",
    "palette": {
      "id": "motion-solar",
      "name": "Veyra Solar Gold · Colors",
      "chat": "#110d07",
      "sidebar": "#1b1a1d",
      "header": "#1f2127",
      "input": "#232730",
      "elevated": "#323642",
      "accent": "#ffda91",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#4e4b46",
      "selected": "#4b3d2c"
    },
    "dim": 0.38
  },
  {
    "id": "obsidian",
    "name": "Obsidian Silver",
    "description": "AMOLED-Schwarz und Silber · dezentes Lichtband",
    "base": "#000000",
    "glowA": "#4d5261",
    "glowB": "#343842",
    "accent": "#e0e3ed",
    "duration": 11000,
    "motion": "sweep",
    "palette": {
      "id": "motion-obsidian",
      "name": "Veyra Obsidian Silver · Colors",
      "chat": "#000000",
      "sidebar": "#121419",
      "header": "#1a1d24",
      "input": "#222630",
      "elevated": "#303541",
      "accent": "#e0e3ed",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#494c57",
      "selected": "#313542"
    },
    "dim": 0.25
  },
  {
    "id": "sunset",
    "name": "Sunset Flame",
    "description": "Orange, Koralle und Magenta · Abendhimmel",
    "base": "#100911",
    "glowA": "#bb4a40",
    "glowB": "#7c2965",
    "accent": "#ffc1ac",
    "duration": 7900,
    "motion": "orbit",
    "palette": {
      "id": "motion-sunset",
      "name": "Veyra Sunset Flame · Colors",
      "chat": "#100911",
      "sidebar": "#1a1822",
      "header": "#1f1f2a",
      "input": "#232631",
      "elevated": "#313642",
      "accent": "#ffc1ac",
      "text": "#f4f6fc",
      "muted": "#a8afbf",
      "secondary": "#cbd0de",
      "border": "#4e464b",
      "selected": "#523338"
    },
    "dim": 0.38
  },
  {
    "id": "ruby-violet",
    "name": "Ruby Violet",
    "description": "Rubinrot und tiefes Lila · zwei schwebende Nebelschichten",
    "base": "#0a0610",
    "glowA": "#b3123f",
    "glowB": "#6c2bd9",
    "accent": "#d98cff",
    "duration": 9000,
    "motion": "orbit",
    "dim": 0.34,
    "palette": {
      "id": "motion-ruby-violet",
      "name": "Veyra Ruby Violet · Colors",
      "chat": "#0a0610",
      "sidebar": "#17131f",
      "header": "#1e1828",
      "input": "#14101b",
      "elevated": "#211a2c",
      "accent": "#d98cff",
      "text": "#f5f2ff",
      "muted": "#b4abc9",
      "secondary": "#d9d2ea",
      "border": "#3a2e4a",
      "selected": "#40224f"
    }
  }
]
export const motionById = new Map(motionPresets.map(p=>[p.id,p]))
export function motionIdFromUri(uri:unknown):string|undefined{
    if(typeof uri!=='string'||!/^file:\/\//.test(uri))return undefined
    const m=uri.match(/\/valenced\/manager-theme-assets\/veyra-motion-([a-z]+(?:-[a-z]+)*?)(?:-v[23])?\.png$/)
    return m?.[1]&&motionById.has(m[1])?m[1]:undefined
}

/** Version 3 provides a transparent second light texture next to its base.
 * Existing v1/v2 selections remain usable without requiring migration. */
export function ambientSource(uri:string):string{
    return motionIdFromUri(uri)&&uri.endsWith('-v3.png')
        ? uri.slice(0,-4)+'-ambient.png'
        : uri
}
export function ambientDuration(preset:MotionPreset):number{
    return preset.id==='ruby-violet'?12400:Math.max(10000,Math.round(preset.duration*1.37))
}
export function ambientOpacity(base:unknown,progress:number):number{
    const weight=typeof base==='number'&&Number.isFinite(base)?Math.max(0,Math.min(1,base)):1
    return weight*(.24+.20*Math.max(0,Math.min(1,progress)))
}

/** Original geometric motion from ValancedX 1.0.18. Retain the original
 * colors and positions for the first three themes, extend to newer palettes. */
const classicColors:Record<string,readonly [string,string]>={
    aurora:['#8d67dc','#40d8be'],
    bloom:['#e88ab6','#9c69db'],
    eclipse:['#5382de','#9272df'],
}
export const classicHalfDuration=6200
export const classicOrbs=[
    {size:380,left:-140,top:-110,dx:110,dy:70},
    {size:380,left:80,top:120,dx:-90,dy:-40},
] as const
export function classicOrbColors(preset:MotionPreset):readonly [string,string]{
    return classicColors[preset.id]??[preset.glowA,preset.glowB]
}
export function classicOpacity(base:unknown,progress:number):number{
    const weight=typeof base==='number'&&Number.isFinite(base)?Math.max(0,Math.min(1,base)):1
    return weight*(.06+.22*Math.max(0,Math.min(1,progress)))
}
