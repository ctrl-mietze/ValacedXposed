import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
const require=createRequire(import.meta.url);
const ts=require(process.env.VX_TYPESCRIPT || 'typescript');
const root=path.resolve(process.argv[2]||'');
const project=path.resolve(process.argv[3]||'.');
const mobile=JSON.parse(fs.readFileSync(process.argv[4],'utf8'));
const revision=execFileSync('git',['-C',root,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
const files=execFileSync('git',['-C',root,'ls-files','-z','src/plugins'],{encoding:'utf8'}).split('\0').filter(Boolean).sort();
const digest=b=>crypto.createHash('sha256').update(b).digest('hex');
const read=f=>fs.readFileSync(path.join(root,f));
const script=f=>/\.(?:tsx?|jsx?)$/.test(f);
const uniq=a=>[...new Set(a)].sort();
const string=node=>node&&(ts.isStringLiteral(node)||ts.isNoSubstitutionTemplateLiteral(node))?node.text:undefined;
const key=node=>node?.name&&(ts.isIdentifier(node.name)||ts.isStringLiteral(node.name))?node.name.text:undefined;
const prop=(o,k)=>o?.properties?.find(n=>key(n)===k);
const value=(o,k)=>prop(o,k)?.initializer;
const strings=n=>n&&ts.isArrayLiteralExpression(n)?n.elements.map(string).filter(Boolean):[];
const walk=(node,fn)=>{fn(node);ts.forEachChild(node,c=>walk(c,fn));};
const sfMap=new Map(), fileInfo=new Map();
let parseErrors=0;
for(const f of files){
 const bytes=read(f), info={path:f,size:bytes.length,sha256:digest(bytes)};
 fileInfo.set(f,info);
 if(script(f)){
  const sf=ts.createSourceFile(f,bytes.toString('utf8'),ts.ScriptTarget.Latest,true,f.endsWith('.tsx')?ts.ScriptKind.TSX:undefined);
  sfMap.set(f,sf);parseErrors+=sf.parseDiagnostics.length;
 }
}
const definitions=[];
for(const [f,sf] of sfMap){
 for(const n of sf.statements){
  if(!ts.isExportAssignment(n)||!ts.isCallExpression(n.expression)||n.expression.expression.getText(sf)!=='definePlugin')continue;
  const o=n.expression.arguments[0]; if(!o||!ts.isObjectLiteralExpression(o))continue;
  definitions.push({f,sf,o,name:string(value(o,'name'))??f,description:string(value(o,'description'))??'',fields:o.properties.map(key).filter(Boolean)});
 }
}
const prefix='src/plugins/';
const groups=new Map();
for(const f of files){const dir=f.slice(prefix.length).split('/')[0];if(!groups.has(dir))groups.set(dir,[]);groups.get(dir).push(f);}
const entryFor=id=>definitions.find(d=>d.f===prefix+id+'/index.ts'||d.f===prefix+id+'/index.tsx');
function analyze(list,entry){
 const imports=[],calls=[],features=new Set(),settings=[],finds=[],events=[],exports=[],local=[],patchMethods=[];
 for(const f of list){
  const sf=sfMap.get(f); if(!sf)continue;
  if(f.endsWith('/native.ts')||/\.desktop\.|\.discordDesktop\.|\.vesktop\./.test(f))features.add('Desktop-Native');
  walk(sf,n=>{
   if(ts.isImportDeclaration(n)||ts.isExportDeclaration(n)){
    const src=string(n.moduleSpecifier);
    if(src){imports.push(src);if(src.startsWith('.'))local.push(src);if(src.startsWith('node:')||['electron','fs','path','child_process'].includes(src))features.add('Node/Electron');if(src.endsWith('.css'))features.add('CSS');if(src.startsWith('@webpack'))features.add('Webpack');if(src.startsWith('@api/'))features.add('Vencord-API');}
   }
   if(ts.isJsxElement(n)||ts.isJsxSelfClosingElement(n))features.add('JSX/React');
   if(ts.isPropertyAccessExpression(n)){
    const t=n.expression.getText(sf);
    if(['window','document','navigator','HTMLElement','localStorage','sessionStorage'].includes(t))features.add('DOM/Browser');
    if(['VencordNative','DiscordNative','process'].includes(t))features.add('Desktop-Native');
    if(t==='settings.store')features.add('Settings');
   }
   if(ts.isCallExpression(n)){
    const callee=n.expression.getText(sf);
    if(/^(?:fetch|WebSocket|XMLHttpRequest)$|(?:^|\.)RestAPI\.|(?:^|\.)request$/.test(callee))features.add('Netzwerk');
    if(/(?:FluxDispatcher|Dispatcher)\.(?:dispatch|subscribe|unsubscribe)/.test(callee)){features.add('Flux');const e=string(n.arguments[0]);if(e)events.push(e);}
    if(/(?:findByProps|findByCode|findByStoreName|findByName|findLazy|findComponent)/.test(callee))features.add('Modulsuche');
    if(/^(?:eval|Function)$/.test(callee))features.add('Dynamischer Code');
    if(/(?:sendMessage|editMessage|deleteMessage|uploadFiles|sendTyping)/.test(callee))features.add('Nachrichtenaktionen');
    if(/(?:getToken|AuthenticationStore)/.test(callee))features.add('Auth-API');
    if(callee==='definePluginSettings'&&ts.isObjectLiteralExpression(n.arguments[0])){
     features.add('Settings');
     for(const x of n.arguments[0].properties){
      if(!key(x)||!x.initializer||!ts.isObjectLiteralExpression(x.initializer))continue;
      const o=x.initializer,def=value(o,'default');
      const defaultValue=string(def)??(def&&ts.isNumericLiteral(def)?Number(def.text):def?.kind===ts.SyntaxKind.TrueKeyword?true:def?.kind===ts.SyntaxKind.FalseKeyword?false:undefined);
      settings.push({key:key(x),type:value(o,'type')?.getText(sf)?.slice(0,60)??'',description:(string(value(o,'description'))??'').slice(0,160),...(defaultValue!==undefined?{default:defaultValue}:{})});
     }
    }
    if(calls.length<1800&&callee.length<100&&/^[\w.]+$/.test(callee))calls.push(callee);
   }
  });
 }
 if(entry){
  const o=entry.o; const patches=value(o,'patches');
  if(patches&&ts.isArrayLiteralExpression(patches)){
   features.add('Webpack-Patches');
   for(const p of patches.elements)if(ts.isObjectLiteralExpression(p)){
    const f=value(p,'find'); if(f)finds.push((string(f)??f.getText(entry.sf)).slice(0,140));
    const replacement=value(p,'replacement');
    if(replacement)patchMethods.push(ts.isArrayLiteralExpression(replacement)?replacement.elements.length:1);
   }
  }
  if(entry.fields.includes('flux'))features.add('Flux');
  if(entry.fields.some(k=>/^onBeforeMessage|^onMessage/.test(k)))features.add('MessageEvents');
  if(entry.fields.includes('contextMenus'))features.add('ContextMenu');
  if(entry.fields.includes('commands'))features.add('Commands');
  if(entry.fields.some(k=>/render|Component|Button|Accessories/i.test(k)))features.add('UI-Hooks');
 }
 if(list.some(f=>f.endsWith('.css')))features.add('CSS');
 const refs=uniq(imports.filter(i=>i.startsWith('@plugins/')).map(i=>i.slice(9).split('/')[0]));
 const sharedApis=uniq(imports.filter(i=>i.startsWith('@api/')).map(i=>i.slice(5).split('/')[0]));
 const tags=entry?strings(value(entry.o,'tags')):[];
 const deps=entry?strings(value(entry.o,'dependencies')):[];
 const treeHash=digest(list.map(f=>f+'\0'+fileInfo.get(f).sha256+'\n').join(''));
 return {treeHash,files:list.map(f=>fileInfo.get(f)),codeFiles:list.filter(script).length,totalBytes:list.reduce((n,f)=>n+fileInfo.get(f).size,0),
   features:[...features].sort(),imports:uniq(imports),localImports:uniq(local),pluginImports:refs,sharedApis,dependencies:deps,
   fields:entry?.fields??[],settings:settings.slice(0,80),patchAnchors:finds,patchCount:finds.length,replacements:patchMethods.reduce((a,b)=>a+b,0),events:uniq(events),calls:uniq(calls).slice(0,100),tags};
}
function lifecycleSetter(entry){
 // A reusable, strict lowering for reversible one-call lifecycle setters.
 // No arbitrary JS is copied. The receiver and both callback values are
 // recognized from AST, with all other executable top-level code rejected.
 const {sf,o,fields}=entry;
 const allowed=new Set(['name','description','authors','tags','searchTerms','start','stop']);
 if(fields.some(f=>!allowed.has(f)))return;
 if(sf.statements.some(s=>!ts.isImportDeclaration(s)&&!ts.isExportAssignment(s)))return;
 const parse=k=>{
  const m=prop(o,k);if(!m||!ts.isMethodDeclaration(m)||m.parameters.length||m.body?.statements.length!==1)return;
  const n=m.body.statements[0];if(!ts.isExpressionStatement(n)||!ts.isCallExpression(n.expression))return;
  const c=n.expression;if(c.arguments.length!==1||!ts.isPropertyAccessExpression(c.expression)||!ts.isIdentifier(c.expression.expression))return;
  const target=c.expression.expression.text,method=c.expression.name.text;
  const arg=c.arguments[0];if(!ts.isPropertyAccessExpression(arg)||arg.expression.getText(sf)!=='Math')return;
  return {target,method,fn:arg.name.text};
 };
 const start=parse('start'),stop=parse('stop');
 if(!start||!stop||start.target!==stop.target||start.method!==stop.method)return;
 const imported=sf.statements.some(n=>ts.isImportDeclaration(n)&&string(n.moduleSpecifier)==='@webpack/common'&&n.importClause?.namedBindings&&ts.isNamedImports(n.importClause.namedBindings)&&n.importClause.namedBindings.elements.some(e=>e.name.text===start.target&&(e.propertyName?.text??e.name.text)==='moment'));
 if(imported&&start.method==='relativeTimeRounding'&&['floor','ceil','round','trunc'].includes(start.fn)&&['floor','ceil','round','trunc'].includes(stop.fn))
  return {op:'moment.rounding',rounding:start.fn,desktopStop:stop.fn};
}
function codeblockTransform(entry){
 // Recognize the real Vencord source shape rather than plugin-name matching:
 // explicit codeblock render patch and matching send/edit callbacks, tab
 // expansion + min indentation + regex-based removal.
 const {sf,o,fields}=entry;
 if(!fields.includes('onBeforeMessageSend')||!fields.includes('onBeforeMessageEdit'))return;
 const method=prop(o,'unindent'),wrapper=prop(o,'unindentMsg');
 if(!method||!wrapper||!ts.isMethodDeclaration(method)||!ts.isMethodDeclaration(wrapper))return;
 const render=value(o,'patches')?.getText(sf)??'';
 const code=method.getText(sf),send=prop(o,'onBeforeMessageSend')?.getText(sf)??'',edit=prop(o,'onBeforeMessageEdit')?.getText(sf)??'';
 const w=wrapper.getText(sf);
 if(!render.includes('inQuote:')||!render.includes('$self.unindent')||!code.includes('Math.min')||!code.includes('Infinity')||!code.includes('new RegExp')||!code.includes('/\\t/g')||!w.includes('msg.content.replace')||!w.includes('this.unindent')||!send.includes('this.unindentMsg')||!edit.includes('this.unindentMsg'))return;
 const tabMatch=code.match(/\.replace\(\/\\t\/g,\s*["']( +)["']\)/);
 if(!tabMatch||tabMatch[1].length>8)return;
 return {op:'text.codeblockIndent',tabWidth:tabMatch[1].length,render:true,send:true,edit:true};
}
const existing={
 silentTyping:['veyra.port.no-typing','Flux: lokale Tippanzeige','Keine Desktop-Schaltfläche.'],
 characterCounter:['veyra.port.char-counter','Metro ChatInputCharCounter + React Native','Mobile Darstellung; Neustart eventuell nötig.'],
 clearURLs:['veyra.port.clean-urls','Mobile Nachrichten- und Clipboard-Hooks','Begrenzte Tracking-Regeln; signierte URLs bleiben unverändert.'],
 imageLink:['veyra.port.image-links','Metro RowManager.generate','Reine Bild-/GIF-Embeds ohne Text.'],
 alwaysAnimate:['veyra.port.animate','Mobile Avatar-/Icon-Auflösung','Nur vorhandene animierte Avatare und Icons.'],
};
const equivalents=JSON.parse(fs.readFileSync(path.join(project,'tools/vencord-compiler/mobile-equivalents.json'),'utf8'));
const eqMap=new Map(equivalents.map(e=>[e.id,e]));
const rows=[],internal=[],rules=[];
const outputRoot=path.join(project,'bundle/src/plugins/start');
const names=new Set();
function writeGenerated(entry,program){
 const id=entry.f.slice(prefix.length).split('/')[0];
 const slug=id.toLowerCase().replace(/[^a-z0-9]+/g,'-');
 const target='veyra.vencord.generated.'+slug;
 const dir=path.join(outputRoot,'vencord-generated-'+slug);
 fs.mkdirSync(dir,{recursive:true});
 const source='https://github.com/Vendicated/Vencord/tree/'+revision+'/'+entry.f.slice(0,entry.f.lastIndexOf('/'));
 const manifest={id:target,name:'Vencord · '+entry.name,description:entry.description+' (generated mobile adapter)',author:{name:'Veyra · Vencord contributors',links:[{url:source,title:'Original source and license'}]},icon:'PuzzlePieceIcon',enabledByDefault:false,dependencies:{}};
 fs.writeFileSync(path.join(dir,'manifest.json'),JSON.stringify(manifest,null,4)+'\n');
 fs.writeFileSync(path.join(dir,'index.ts'),"// Generated by tools/vencord-compiler/compile.mjs; source revision "+revision+"\nimport { registerInternalPlugin } from '@valenced/plugins/_'\nimport { createCompiledOptions } from '../vencord-compiler/runtime'\nimport manifest from './manifest.json'\nregisterInternalPlugin(manifest,createCompiledOptions("+JSON.stringify(program)+"))\n");
 return target;
}
for(const [id,list] of groups){
 if(id.startsWith('_')){
  for(const d of definitions.filter(x=>list.includes(x.f))){
   const sub=d.f.includes('/index.')?d.f.slice(0,d.f.lastIndexOf('/')):d.f.replace(/\.[^.]+$/,'');
   const owned=list.filter(f=>f===d.f||f.startsWith(sub+'/'));
   internal.push({id:sub.slice(prefix.length),name:d.name,description:d.description,...analyze(owned,d),kind:'internal'});
  }
  continue;
 }
 const entry=entryFor(id); if(!entry)continue;
 const info=analyze(list,entry);
 const row={id,name:entry.name,description:entry.description,path:entry.f,sha256:fileInfo.get(entry.f).sha256,size:fileInfo.get(entry.f).size,
   files:list.length,sourceFiles:info.files,treeHash:info.treeHash,native:info.features.includes('Node/Electron')||info.features.includes('Desktop-Native')||list.some(f=>f.endsWith('/native.ts')),
   css:info.features.includes('CSS'),patches:info.patchCount>0,analysis:{...info,files:undefined}};
 const program=lifecycleSetter(entry)??codeblockTransform(entry);
 let rule;
 if(program){
  const target=writeGenerated(entry,program);
  rule={id,port:target,kind:'compiled',adaptation:program.op==='moment.rounding'?'AST-Lifecycle → Metro Moment-API, mit Wiederherstellung des Originalzustands.':'AST-Texttransformation → mobile Chat-Render- und Send/Edit-Hooks.',limitation:program.op==='moment.rounding'?'Wirkt nur, wenn der Mobile-Client Moment mit dieser API bereitstellt.':'Codeblöcke statt Desktop-Renderer; mobile Hook-Ziele werden zur Laufzeit geprüft.',program};
 }else if(existing[id]){
  const [port,adaptation,limitation]=existing[id];rule={id,port,adaptation,limitation,kind:'bundled'};
 }else if(eqMap.has(id)){
  const eq=eqMap.get(id);
  const candidates=mobile.available.filter(p=>p.id===eq.target&&p.artifactSha256&&p.artifactUrl?.startsWith('https://'));
  const m=candidates.find(p=>p.repo==='https://community.valenced.app/index.json')??candidates[0];
  if(m)rule={id,port:eq.target,kind:'repository',adaptation:eq.adaptation,limitation:eq.limitation,repo:m.repo,version:m.version,artifactHash:m.artifactSha256};
 }
 if(rule){
  rule.sourceHash=row.sha256;rule.treeHash=info.treeHash;rules.push(rule);
  row.translation={kind:rule.kind,target:rule.port,adaptation:rule.adaptation,limitation:rule.limitation,repo:rule.repo,version:rule.version,artifactHash:rule.artifactHash,program:rule.program};
 }else{
  const blockers=[];
  if(row.native)blockers.push('Desktop-Native/Node/Electron benötigt Android-Adapter');
  if(info.features.includes('DOM/Browser')||row.css)blockers.push('DOM/CSS muss in React Native nachgebaut werden');
  if(row.patches)blockers.push('Webpack-Patchanker sind keine Metro-Hook-Ziele');
  if(info.sharedApis.length)blockers.push('Vencord-APIs: '+info.sharedApis.join(', '));
  if(info.dependencies.length||info.pluginImports.length)blockers.push('Plugin-Abhängigkeiten: '+uniq([...info.dependencies,...info.pluginImports]).join(', '));
  if(!blockers.length)blockers.push('Noch keine geprüfte mobile Semantik/ABI');
  row.translation={kind:'manual',reason:blockers.join(' · ')};
 }
 rows.push(row);
}
const summary={userPlugins:rows.length,internalDefinitions:internal.length,files:files.length,codeFiles:sfMap.size,parseErrors,bytes:files.reduce((n,f)=>n+fileInfo.get(f).size,0),modes:Object.fromEntries(['compiled','bundled','repository','manual'].map(k=>[k,rows.filter(x=>x.translation.kind===k).length])),features:Object.fromEntries(uniq(rows.flatMap(r=>r.analysis.features)).map(f=>[f,rows.filter(r=>r.analysis.features.includes(f)).length]))};
if(parseErrors)throw Error('AST parse diagnostics: '+parseErrors);
const catalog={format:2,revision,repo:'Vendicated/Vencord',summary,plugins:rows,internal};
const asset=path.join(project,'android/src/main/assets/vencord-catalog.json');
fs.writeFileSync(asset,JSON.stringify(catalog)+'\n');
// The native worker and manager both use this pinned rule table. No
// third-party input can introduce new target IDs or arbitrary executable code.
const q=s=>JSON.stringify(s??'').replaceAll('$','\\$');
const kt='package app.valenced.xposed.manager.analysis\n\n// Generated from pinned AST inventory and reviewed mobile equivalents.\ninternal object VencordCompiledRules {\n    val all=mapOf(\n'+rules.map(r=>'        '+q(r.id)+' to VencordTranslator.Rule('+[r.port,r.adaptation,r.limitation,r.sourceHash,r.treeHash,r.kind,r.repo,r.version,r.artifactHash].map(q).join(', ')+')').join(',\n')+'\n    )\n    data class DependencyPin(val repo:String,val version:String,val hash:String)\n    val dependencyPins=mapOf(\n'+['dev.tralwdwd.action-sheet-patcher','dev.cocobo1.command-lib'].map(id=>{const p=mobile.available.find(x=>x.id===id);if(!p)throw Error('Missing dependency pin '+id);return '        '+q(id)+' to DependencyPin('+[p.repo,p.version,p.artifactSha256].map(q).join(', ')+')'}).join(',\n')+'\n    )\n}\n';
fs.writeFileSync(path.join(project,'android/src/main/kotlin/app/valenced/xposed/manager/analysis/VencordCompiledRules.kt'),kt);
const doc=['# Vencord → ValancedX: vollständiger AST-Inventarbericht','', 'Revision: '+revision,'', '## Umfang','',...Object.entries(summary).map(([k,v])=>'- '+k+': '+(typeof v==='object'?JSON.stringify(v):v)),'','## Plugin-Matrix','','| Plugin | Dateien | Hooks | Ergebnis |','|---|---:|---:|---|',...rows.map(r=>'| '+r.name.replaceAll('|','\\|')+' | '+r.files+' | '+r.analysis.patchCount+' | '+r.translation.kind+' · '+(r.translation.target??r.translation.reason).replaceAll('|','\\|')+' |'),'','## Interne Vencord-APIs','','Diese Komponenten werden als Abhängigkeiten inventarisiert, nicht als Android-Plugins aktiviert.',...internal.map(r=>'- '+r.name+' — '+r.features.join(', ')),'','Statische Analyse und Compiler-Ableitung sind keine bestätigten Gerätelaufzeittests.'].join('\n');
fs.writeFileSync(path.join(project,'docs/VENCORD_TRANSLATION_MATRIX.md'),doc+'\n');
console.log(JSON.stringify(summary,null,2));console.log('RULES',rules.map(r=>r.id+':'+r.kind).join(', '));
