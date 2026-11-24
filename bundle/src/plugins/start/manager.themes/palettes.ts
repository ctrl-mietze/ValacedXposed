/** Distinct Discord surface roles, independent of Manager colors. */
export type DiscordPalette = {
    id:string;name:string;chat:string;sidebar:string;header:string;input:string;elevated:string;
    accent:string;text:string;muted:string;secondary:string;border:string;selected:string
}
export const palettes: DiscordPalette[] = [
  {
    "id": "midnight",
    "name": "Veyra Midnight",
    "chat": "#0c0f14",
    "sidebar": "#131823",
    "header": "#171c28",
    "input": "#1b2130",
    "elevated": "#20283a",
    "accent": "#a990ff",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#2a3244",
    "selected": "#2b2440"
  },
  {
    "id": "lavender",
    "name": "Veyra Lavender",
    "chat": "#111116",
    "sidebar": "#18181f",
    "header": "#1e1e27",
    "input": "#242430",
    "elevated": "#2b2b38",
    "accent": "#c4a8fa",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#383846",
    "selected": "#322b44"
  },
  {
    "id": "frost",
    "name": "Veyra Frost",
    "chat": "#0c121b",
    "sidebar": "#131e2a",
    "header": "#182534",
    "input": "#1e2c3c",
    "elevated": "#253548",
    "accent": "#a4ceff",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#304154",
    "selected": "#253f58"
  },
  {
    "id": "neon",
    "name": "Veyra Neon",
    "chat": "#0a1015",
    "sidebar": "#131b22",
    "header": "#19232b",
    "input": "#202c35",
    "elevated": "#273640",
    "accent": "#55e1d9",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#34434d",
    "selected": "#233b3c"
  },
  {
    "id": "glass",
    "name": "Veyra Glass",
    "chat": "#101115",
    "sidebar": "#17191e",
    "header": "#1d2027",
    "input": "#252831",
    "elevated": "#2c303a",
    "accent": "#c2b0ff",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#3a3e49",
    "selected": "#302d40"
  },
  {
    "id": "amoled",
    "name": "Veyra AMOLED",
    "chat": "#000000",
    "sidebar": "#0b0c10",
    "header": "#111319",
    "input": "#171a20",
    "elevated": "#20232b",
    "accent": "#c9b5ff",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#30343e",
    "selected": "#29243a"
  },
  {
    "id": "bloom",
    "name": "Veyra Bloom",
    "chat": "#111014",
    "sidebar": "#19171c",
    "header": "#201d24",
    "input": "#28242d",
    "elevated": "#302b37",
    "accent": "#f1afd3",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#413946",
    "selected": "#382b3a"
  },
  {
    "id": "eclipse",
    "name": "Veyra Eclipse",
    "chat": "#080b13",
    "sidebar": "#111725",
    "header": "#171e2e",
    "input": "#1d2638",
    "elevated": "#263047",
    "accent": "#aebdff",
    "text": "#f4f6fc",
    "muted": "#9ca5b8",
    "secondary": "#c1c8d8",
    "border": "#303c54",
    "selected": "#2b3457"
  }
]

export function mix(a:string,b:string,f:number):string {
    return '#'+[1,3,5].map(i=>Math.round(parseInt(a.slice(i,i+2),16)*(1-f)+parseInt(b.slice(i,i+2),16)*f).toString(16).padStart(2,'0')).join('')
}
export function luminance(hex:string):number {
    return [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4)
        .reduce((sum,v,i)=>sum+v*[.2126,.7152,.0722][i]!,0)
}
export function contrast(a:string,b:string):number {
    const [x,y]=[luminance(a),luminance(b)].sort((a,b)=>b-a)
    return (x!+.05)/(y!+.05)
}
export function onColor(bg:string):string {
    return contrast(bg,'#10121a')>=contrast(bg,'#ffffff')?'#10121a':'#ffffff'
}
export const previewTokens=['BACKGROUND_BASE_LOWER','BACKGROUND_BASE_LOWEST','BACKGROUND_SURFACE_HIGHEST','TEXT_LINK','TEXT_STRONG','TEXT_LINK','BACKGROUND_BASE_LOW','MOBILE_FLOATINGBAR_BACKGROUND','TEXT_MUTED','BORDER_SUBTLE','BACKGROUND_MODIFIER_SELECTED']
export function previewPalette(p:DiscordPalette):string[] {
    return [p.chat,p.sidebar,p.elevated,p.accent,p.text,p.accent,p.header,p.input,p.muted,p.border,p.selected]
}

export function brandFill(p:DiscordPalette):string {
    // Discord has fixed-white labels on some older buttons; their background
    // must also remain legible where TEXT_ON_BRAND isn't consulted.
    for(let f=.30;f<=.85;f+=.025) {
        const bg=mix(p.accent,p.chat,f)
        if(contrast(bg,'#ffffff')>=4.5)return bg
    }
    return mix(p.accent,p.chat,.85)
}
export function ownTheme(p:DiscordPalette) {
    const semantic:Record<string,string>={}
    const set=(color:string,names:string)=>{for(const key of names.split(' '))semantic[key]=color}
    // Current Valenced tokens and known older Discord aliases. Do not color
    // message attachments, avatars, role colors or arbitrary images.
    set(p.chat,'BACKGROUND_PRIMARY BACKGROUND_MOBILE_PRIMARY BACKGROUND_BASE_LOWER BG_BASE_PRIMARY CHAT_BACKGROUND')
    set(p.header,'BACKGROUND_BASE_LOW HEADER_BACKGROUND MOBILE_HEADER_BACKGROUND')
    set(p.sidebar,'BACKGROUND_SECONDARY BACKGROUND_MOBILE_SECONDARY BACKGROUND_BASE_LOWEST BG_BASE_SECONDARY PANEL_BG')
    set(p.chat,'CHANNEL_BACKGROUND_DEFAULT')
    set(mix(p.sidebar,p.chat,.45),'BACKGROUND_TERTIARY BG_BASE_TERTIARY BACKGROUND_SECONDARY_ALT')
    set(p.input,'MOBILE_FLOATINGBAR_BACKGROUND MOBILE_FLOATINGBAR_BACKGROUND_HIGHER INPUT_BACKGROUND')
    set(p.elevated,'BACKGROUND_SURFACE_HIGH BACKGROUND_SURFACE_HIGHEST BACKGROUND_SURFACE_HIGHER BACKGROUND_FLOATING BACKGROUND_NESTED_FLOATING CARD_PRIMARY_BG CARD_SECONDARY_BG')
    set(p.input,'INPUT_CONTAINER_BACKGROUND')
    set(p.selected,'BACKGROUND_MODIFIER_SELECTED BACKGROUND_MODIFIER_ACTIVE BACKGROUND_MOD_SELECTED')
    set(mix(p.sidebar,p.accent,.08),'BACKGROUND_MODIFIER_HOVER BACKGROUND_MOD_SUBTLE')
    set(p.border,'BACKGROUND_MODIFIER_ACCENT BORDER_SUBTLE BORDER_DEFAULT DIVIDER_DEFAULT')
    set(p.text,'TEXT_NORMAL TEXT_STRONG TEXT_DEFAULT HEADER_PRIMARY INTERACTIVE_ACTIVE MOBILE_TEXT_HEADING_PRIMARY CHANNELS_DEFAULT_SELECTED')
    set(p.secondary,'TEXT_SUBTLE TEXT_SECONDARY HEADER_SECONDARY INTERACTIVE_NORMAL ICON_DEFAULT CHANNELS_DEFAULT')
    set(p.muted,'TEXT_MUTED TEXT_TERTIARY INTERACTIVE_MUTED ICON_MUTED TEXT_PLACEHOLDER INPUT_PLACEHOLDER_TEXT CHANNEL_TEXT_AREA_PLACEHOLDER')
    set(p.accent,'TEXT_LINK TEXT_BRAND ICON_BRAND BORDER_FOCUS')
    set(p.text,'INTERACTIVE_HOVER')
    // A subdued filled brand surface distinct from bright link/accent text.
    const brand=brandFill(p)
    set(brand,'BACKGROUND_BRAND BUTTON_PRIMARY_BACKGROUND')
    set(onColor(brand),'TEXT_ON_BRAND TEXT_ON_PRIMARY BUTTON_PRIMARY_TEXT')
    set(p.input,'BACKGROUND_ACCENT')
    // Keep semantic status colors and role/media rendering untouched. Preserve
    // original raw neutral palette (native elements may use light-base values).
    const raw:Record<string,string>={}
    return {name:p.name,description:'Veyra Studio · abgestimmte Chat-, Menü- und Textfarben',
        colors:{semantic:{dark:{...semantic},darker:{...semantic},midnight:{...semantic},light:{...semantic}},raw}}
}
