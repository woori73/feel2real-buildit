import {writeFile, mkdir, readFile} from 'node:fs/promises';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {gzipSync} from 'node:zlib';
import {build} from 'esbuild';
import {GLTFExporter} from 'three/addons/exporters/GLTFExporter.js';
import {createEnvironment} from './model.mjs';
const dir=dirname(fileURLToPath(import.meta.url)),repo=resolve(dir,'../..');
// GLTFExporter uses browser FileReader for buffer assembly; no textures are exported.
globalThis.FileReader=class {
  readAsArrayBuffer(blob){blob.arrayBuffer().then(r=>{this.result=r;this.onloadend?.();});}
  readAsDataURL(blob){blob.arrayBuffer().then(r=>{this.result='data:application/octet-stream;base64,'+Buffer.from(r).toString('base64');this.onloadend?.();});}
};
const env=createEnvironment(),exporter=new GLTFExporter();
const output=resolve(repo,'src/main/resources/static/models/f2r-space');await mkdir(output,{recursive:true});
let counts=[];
for(const group of env.children) {
  const data=await exporter.parseAsync(group,{binary:true});
  const name=group.name.toLowerCase()+'.glb';await writeFile(resolve(output,name),Buffer.from(data));counts.push({name,bytes:data.byteLength});
}
const data=await exporter.parseAsync(env,{binary:true});await writeFile(resolve(output,'f2r-environment.glb'),Buffer.from(data));
await build({entryPoints:[resolve(dir,'scene.mjs')],bundle:true,format:'esm',minify:true,target:'es2020',outfile:resolve(repo,'src/main/resources/static/js/f2r_spatial_scene.js'),legalComments:'linked'});
const bundlePath=resolve(repo,'src/main/resources/static/js/f2r_spatial_scene.js');
const bundle=Buffer.from((await readFile(bundlePath,'utf8')).replace(/[ \t]+$/gm,''));await writeFile(bundlePath,bundle);
const report={generator:'F2R procedural original / Three.js 0.180.0 (MIT)',models:counts,combinedBytes:data.byteLength,bundleBytes:bundle.length,bundleGzipBytes:gzipSync(bundle).length,characterGeometry:false,textures:0};
await writeFile(resolve(output,'manifest.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
