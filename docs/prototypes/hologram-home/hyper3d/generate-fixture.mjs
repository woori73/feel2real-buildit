// Authored geometric diagnostic object; not a character or a Hyper3D output.
import * as THREE from 'three';
import {GLTFExporter} from 'three/addons/exporters/GLTFExporter.js';
import {writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
globalThis.FileReader=class{async readAsArrayBuffer(blob){this.result=await blob.arrayBuffer();this.onloadend?.();}};
const scene=new THREE.Scene();
const core=new THREE.Mesh(new THREE.OctahedronGeometry(.8),new THREE.MeshStandardMaterial({color:0x9beaff,metalness:.55,roughness:.3}));core.name='DiagnosticCore';scene.add(core);
const ring=new THREE.Mesh(new THREE.TorusGeometry(1.2,.025,6,48),new THREE.MeshStandardMaterial({color:0x5dcfff,emissive:0x07394a}));ring.rotation.x=Math.PI/2.5;scene.add(ring);
const clips=[
 new THREE.AnimationClip('idle',2,[new THREE.VectorKeyframeTrack('DiagnosticCore.position',[0,1,2],[0,0,0,0,.08,0,0,0,0])]),
 new THREE.AnimationClip('greeting',1,[new THREE.QuaternionKeyframeTrack('DiagnosticCore.quaternion',[0,.5,1],[0,0,0,1,0,0,.25,.9682458,0,0,0,1])]),
 new THREE.AnimationClip('thinking',2,[new THREE.QuaternionKeyframeTrack('DiagnosticCore.quaternion',[0,1,2],[0,0,0,1,0,1,0,0,0,0,0,-1])]),
 new THREE.AnimationClip('responding',1,[new THREE.VectorKeyframeTrack('DiagnosticCore.scale',[0,.5,1],[1,1,1,1.12,1.12,1.12,1,1,1])])
];
const bytes=Buffer.from(await new GLTFExporter().parseAsync(scene,{binary:true,animations:clips}));
writeFileSync(new URL('diagnostic.glb',import.meta.url),bytes);
writeFileSync(new URL('fixture.json',import.meta.url),JSON.stringify({kind:'diagnostic-only',url:'hyper3d/diagnostic.glb',sha256:createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length,clips:{idle:'idle',greeting:'greeting',thinking:'thinking',responding:'responding'}},null,2)+'\n');
console.log('Diagnostic GLB:',bytes.length,'bytes');
