#!/usr/bin/env python3
"""Conservative source call-graph screening. This is evidence, not a runtime safety proof.
Requires tree-sitter and tree-sitter-java. Does not call any service or institution.
"""
import collections,json,re,sys
from pathlib import Path
import tree_sitter_java
from tree_sitter import Language,Parser
ROOT=Path(__file__).resolve().parents[2]
parser=Parser(Language(tree_sitter_java.language()))
trees=[]
classes={}; methods=collections.defaultdict(list); fields={}; parents=collections.defaultdict(set)
def walk(n):
 yield n
 for c in n.named_children:yield from walk(c)
def text(n,b):return b[n.start_byte:n.end_byte].decode() if n else ''
def typename(s):return re.sub(r'<.*','',s).split('.')[-1].strip()
for path in (ROOT/'src/main/java').rglob('*.java'):
 b=path.read_bytes();tree=parser.parse(b);trees.append(tree)
 for cls in (n for n in walk(tree.root_node) if n.type in ('class_declaration','interface_declaration','record_declaration','enum_declaration')):
  name=text(cls.child_by_field_name('name'),b);body=cls.child_by_field_name('body')
  if body is None:continue
  classes[name]=(str(path.relative_to(ROOT)),b); fm={}
  for child in body.named_children:
   if child.type=='field_declaration':
    typ=typename(text(child.child_by_field_name('type'),b))
    for dec in child.named_children:
     if dec.type=='variable_declarator':fm[text(dec.child_by_field_name('name'),b)]=typ
   if child.type=='method_declaration':
    m=text(child.child_by_field_name('name'),b);methods[(name,m)].append(child)
  fields[name]=fm
  for child in cls.named_children:
   if child.type in ('super_interfaces','superclass','extends_interfaces'):
    for ident in walk(child):
     if ident.type=='type_identifier':parents[text(ident,b)].add(name)

def inspect(key,seen=None):
 seen=set() if seen is None else seen
 if key in seen:return {},set()
 seen.add(key); owner,method=key; evidence={};unresolved=set()
 candidates=methods.get(key,[])
 for impl in parents.get(owner,[]):
  e,u=inspect((impl,method),seen);evidence.update(e);unresolved.update(u)
 if not candidates:return evidence,unresolved
 path,b=classes[owner]
 for node in candidates:
  loc=f'{path}:{node.start_point.row+1} {owner}.{method}'
  src=text(node,b)
  if '@Modifying' in src:evidence['DB_WRITE:'+loc]=loc
  for call in (n for n in walk(node) if n.type=='method_invocation'):
   name=text(call.child_by_field_name('name'),b);obj=text(call.child_by_field_name('object'),b)
   typ=fields.get(owner,{}).get(obj.replace('this.',''),obj if obj in classes else None)
   if not obj or obj=='this':typ=owner
   if name in ('save','saveAll','saveAndFlush','delete','deleteAll','deleteById','deleteAllById','deleteAllInBatch','flush','persist','merge','remove','executeUpdate','batchUpdate') or name.startswith('deleteBy'):
    if obj and (typ or 'repository' in obj.lower() or 'entityManager' in obj):evidence['DB_WRITE:'+loc]=loc
   if name in ('update','execute') and typ in ('JdbcTemplate','NamedParameterJdbcTemplate','JdbcOperations'):evidence['DB_WRITE:'+loc]=loc
   if (typ in ('Files','FileOutputStream') and name in ('write','writeString','createFile','createDirectories','createDirectory','copy','move','deleteIfExists','newOutputStream','createTempFile')) or name in ('saveRaw','saveImage','saveGenerated','putObject','deleteObject'):
    evidence['FILE_WRITE:'+loc]=loc
   if name in ('send','sendAsync','sendEmail','sendSms','chatCompletion','embedBatch','embedDocument','submit','schedule','executeAsync') or typ in ('RestTemplate','WebClient'):
    evidence['REMOTE_OR_ASYNC:'+loc]=loc
   if typ and (typ,name) in methods:
    e,u=inspect((typ,name),seen);evidence.update(e);unresolved.update(u)
   elif typ and typ in parents:
    for impl in parents[typ]:
     e,u=inspect((impl,name),seen);evidence.update(e);unresolved.update(u)
   elif typ and typ!='ObjectProvider' and typ not in classes and re.search('Client|Provider|Transport|Gateway',typ):unresolved.add(f'{typ}.{name}')
 return evidence,unresolved
source=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'docs/ai-agent-studio/service-inventory.json'
data=json.loads(source.read_text());routes=data.get('routes',[]) if isinstance(data,dict) else data
policy=json.loads((ROOT/'src/main/resources/mcp/read-policy.json').read_text());allowed={p['path'] for p in policy}
# Include source-declared GETs that are absent because a profile/feature switch is OFF.
seen_paths={r['path'] for r in routes if 'GET' in r.get('methods',[])}
for owner,(path,b) in classes.items():
 tree=parser.parse(b);trees.append(tree)
 declarations=[n for n in walk(tree.root_node) if n.type=='class_declaration' and text(n.child_by_field_name('name'),b)==owner]
 if not declarations:continue
 modifiers=next((n for n in declarations[0].named_children if n.type=='modifiers'),None)
 annotation=text(modifiers,b);prefix_match=re.search(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"\n]*)"',annotation)
 prefix=prefix_match.group(1) if prefix_match else ''
 for (cls,name),nodes in list(methods.items()):
  if cls!=owner:continue
  for node in nodes:
   mods=next((n for n in node.named_children if n.type=='modifiers'),None)
   a=text(mods,b)
   match=re.search(r'@GetMapping(?:\s*\(([^)]*)\))?',a)
   if not match:continue
   literals=re.findall(r'"([^"\n]*)"',match.group(1) or '') or ['']
   for suffix in literals:
    value=prefix+suffix
    if value and value.startswith('/') and value not in seen_paths:
     routes.append({'path':value,'methods':['GET'],'controller':owner,'handler':name,'source':path,'sourceOnly':True});seen_paths.add(value)
rows=[]
for r in routes:
 if 'GET' not in r.get('methods',[]):continue
 key=(r['controller'],r.get('handler',''))
 flags,unresolved=inspect(key)
 rows.append({'path':r['path'],'controller':r['controller'],'handler':r.get('handler'),'source':r.get('source'),'allowed':r['path'] in allowed,'flags':sorted(flags),'unresolvedExternalTypes':sorted(unresolved),'sourceOnly':r.get('sourceOnly',False),'assessment':('SOURCE_FLAGS' if flags else 'KNOWN_STORAGE_IO' if unresolved and all(x.startswith('S3Client.') for x in unresolved) else 'SSR_VIEW_REGISTRATION' if r['controller']=='WebMvcConfig' else 'WIKI_SIGNED_READONLY_PROXY' if r['controller']=='BuildItWikiProxyController' and not(key in methods) else 'ACADEMY_SIGNED_PROXY' if r['controller']=='AcademyCraftGateway' and not(key in methods) else 'EXTERNAL_OR_DYNAMIC' if unresolved or not (key in methods) else 'NO_STATIC_FLAG'),'limitation':'Static screening cannot prove dynamic dispatch, external API or filesystem absence. Existing API tenant checks and runtime guards remain mandatory.'})
report={'mainBase':__import__('subprocess').check_output(['git','merge-base','HEAD','origin/main'],cwd=ROOT,text=True).strip(),'method':'Java AST method-call traversal, repository/JDBC writes, file writes, remote/async calls; no live requests','getRoutes':len(rows),'rows':rows}
(ROOT/'docs/ai-agent-studio/get-safety-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
for r in rows:
 if r['allowed'] and (r['flags'] or r['unresolvedExternalTypes']):print(r['path'],r['flags'],r['unresolvedExternalTypes'])
print('GET routes',len(rows),'allowed',sum(r['allowed'] for r in rows),'flagged allowed',sum(r['allowed'] and bool(r['flags'] or r['unresolvedExternalTypes']) for r in rows))
