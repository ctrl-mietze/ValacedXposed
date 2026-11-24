/** Distinct Discord surface roles, independent of Manager colors. */
export type DiscordPalette = {
    id:string;name:string;chat:string;sidebar:string;header:string;input:string;elevated:string;
    accent:string;text:string;muted:string;secondary:string;border:string;selected:string
}
export const palettes: DiscordPalette[] = [
  {
    "id": "midnight",
    "name": "Veyra Midnight",
    "chat": "#0e1016",
    "sidebar": "#141824",
    "header": "#181c28",
    "input": "#1b2030",
    "elevated": "#1e2435",
    "accent": "#a990ff",
    "text": "#f3f5fd",
    "muted": "#8991a8",
    "secondary": "#bcc3d9",
    "border": "#262d40",
    "selected": "#30264c"
  },
  {
    "id": "lavender",
    "name": "Veyra Lavender",
    "chat": "#111017",
    "sidebar": "#191623",
    "header": "#1e192a",
    "input": "#242034",
    "elevated": "#2a253c",
    "accent": "#c4a8fa",
    "text": "#f5f0fc",
    "muted": "#a39ab5",
    "secondary": "#c8c0d5",
    "border": "#373046",
    "selected": "#3b2c56"
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
    "text": "#eff6ff",
    "muted": "#91a6bd",
    "secondary": "#bfd0e3",
    "border": "#304154",
    "selected": "#253f58"
  },
  {
    "id": "neon",
    "name": "Veyra Neon",
    "chat": "#090d14",
    "sidebar": "#111923",
    "header": "#15202c",
    "input": "#1a2634",
    "elevated": "#20303f",
    "accent": "#55e1d9",
    "text": "#edf7fa",
    "muted": "#8ca7b4",
    "secondary": "#b7cbd3",
    "border": "#2e404d",
    "selected": "#1a413f"
  },
  {
    "id": "glass",
    "name": "Veyra Glass",
    "chat": "#11121a",
    "sidebar": "#191b26",
    "header": "#1e202e",
    "input": "#242636",
    "elevated": "#2b2e40",
    "accent": "#c2b0ff",
    "text": "#f5f4fc",
    "muted": "#9da0b5",
    "secondary": "#c4c7d8",
    "border": "#37394d",
    "selected": "#35304d"
  },
  {
    "id": "amoled",
    "name": "Veyra AMOLED",
    "chat": "#000000",
    "sidebar": "#0b0d12",
    "header": "#111319",
    "input": "#161922",
    "elevated": "#1d202a",
    "accent": "#c9b5ff",
    "text": "#f6f5fc",
    "muted": "#9295aa",
    "secondary": "#c3c6d5",
    "border": "#292d39",
    "selected": "#2f2446"
  },
  {
    "id": "bloom",
    "name": "Veyra Bloom",
    "chat": "#120e14",
    "sidebar": "#1d1720",
    "header": "#241b28",
    "input": "#2b2131",
    "elevated": "#34283b",
    "accent": "#f1afd3",
    "text": "#fcf2fa",
    "muted": "#af99ad",
    "secondary": "#ddc8d8",
    "border": "#473548",
    "selected": "#4b2e46"
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
    "text": "#f0f3ff",
    "muted": "#939fbb",
    "secondary": "#c2cbe0",
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
export const previewTokens=['BACKGROUND_BASE_LOWER','CHANNEL_BACKGROUND_DEFAULT','BACKGROUND_SURFACE_HIGHEST','TEXT_LINK','TEXT_STRONG','TEXT_LINK','BACKGROUND_BASE_LOW','MOBILE_FLOATINGBAR_BACKGROUND','TEXT_MUTED','BORDER_SUBTLE','BACKGROUND_MODIFIER_SELECTED']
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
    set(p.sidebar,'BACKGROUND_SECONDARY BACKGROUND_MOBILE_SECONDARY BACKGROUND_BASE_LOWEST CHANNEL_BACKGROUND_DEFAULT BG_BASE_SECONDARY PANEL_BG')
    set(mix(p.sidebar,p.chat,.45),'BACKGROUND_TERTIARY BG_BASE_TERTIARY BACKGROUND_SECONDARY_ALT')
    set(p.input,'MOBILE_FLOATINGBAR_BACKGROUND MOBILE_FLOATINGBAR_BACKGROUND_HIGHER INPUT_BACKGROUND')
    set(p.elevated,'BACKGROUND_SURFACE_HIGH BACKGROUND_SURFACE_HIGHEST BACKGROUND_SURFACE_HIGHER BACKGROUND_FLOATING BACKGROUND_NESTED_FLOATING CARD_PRIMARY_BG CARD_SECONDARY_BG INPUT_CONTAINER_BACKGROUND')
    set(p.selected,'BACKGROUND_MODIFIER_SELECTED BACKGROUND_MODIFIER_ACTIVE BACKGROUND_MOD_SELECTED')
    set(mix(p.sidebar,p.accent,.08),'BACKGROUND_MODIFIER_HOVER BACKGROUND_MOD_SUBTLE')
    set(p.border,'BACKGROUND_MODIFIER_ACCENT BORDER_SUBTLE BORDER_DEFAULT DIVIDER_DEFAULT')
    set(p.text,'TEXT_NORMAL TEXT_STRONG TEXT_DEFAULT HEADER_PRIMARY INTERACTIVE_ACTIVE MOBILE_TEXT_HEADING_PRIMARY CHANNELS_DEFAULT_SELECTED')
    set(p.secondary,'TEXT_SUBTLE TEXT_SECONDARY HEADER_SECONDARY INTERACTIVE_NORMAL ICON_DEFAULT CHANNELS_DEFAULT')
    set(p.muted,'TEXT_MUTED TEXT_TERTIARY INTERACTIVE_MUTED ICON_MUTED TEXT_PLACEHOLDER INPUT_PLACEHOLDER_TEXT CHANNEL_TEXT_AREA_PLACEHOLDER')
    set(p.accent,'TEXT_LINK TEXT_BRAND ICON_BRAND INTERACTIVE_HOVER BORDER_FOCUS')
    // A subdued filled brand surface distinct from bright link/accent text.
    const brand=brandFill(p)
    set(brand,'BACKGROUND_BRAND BUTTON_PRIMARY_BACKGROUND')
    set(onColor(brand),'TEXT_ON_BRAND TEXT_ON_PRIMARY BUTTON_PRIMARY_TEXT')
    set(p.selected,'BACKGROUND_ACCENT')
    // Keep semantic status colors and role/media rendering untouched. Preserve
    // original raw neutral palette (native elements may use light-base values).
    const raw:Record<string,string>={}
    const brandStops:Record<number,string>={
        260:mix(p.accent,'#ffffff',.45),300:mix(p.accent,'#ffffff',.28),360:mix(p.accent,'#ffffff',.10),
        400:p.accent,430:mix(p.accent,brand,.25),460:mix(p.accent,brand,.65),500:brand,
        530:mix(brand,p.chat,.10),560:mix(brand,p.chat,.20),600:mix(brand,p.chat,.32),
        630:mix(brand,p.chat,.43),660:mix(brand,p.chat,.55),700:mix(brand,p.chat,.66),730:mix(brand,p.chat,.76)
    }
    for(const [k,v] of Object.entries(brandStops))raw['BRAND_'+k]=v
    return {name:p.name,description:'Veyra Studio · abgestimmte Chat-, Menü- und Textfarben',
        colors:{semantic:{dark:{...semantic},darker:{...semantic},midnight:{...semantic},light:{...semantic}},raw}}
}
