const {test}=require('node:test');const assert=require('node:assert/strict'),fs=require('node:fs');
function glb(doc){let json=Buffer.from(JSON.stringify(doc));json=Buffer.concat([json,Buffer.alloc((4-json.length%4)%4,32)]);const b=Buffer.alloc(20+json.length);b.writeUInt32LE(0x46546c67,0);b.writeUInt32LE(2,4);b.writeUInt32LE(b.length,8);b.writeUInt32LE(json.length,12);b.writeUInt32LE(0x4e4f534a,16);json.copy(b,20);return b.buffer.slice(b.byteOffset,b.byteOffset+b.byteLength);}
(async()=>{const {inspectGLB}=await import('../hyper3d/viewer.bundle.js');
 test('given self-contained GLB, inspection accepts its JSON',()=>assert.equal(inspectGLB(glb({asset:{version:'2.0'},buffers:[{byteLength:0}]})).asset.version,'2.0'));
 test('given external buffer URI, reject before any loader request',()=>assert.throws(()=>inspectGLB(glb({buffers:[{uri:'https://example.com/a.bin'}]})),/embedded/));
 test('given external texture URI, reject before image decoding',()=>assert.throws(()=>inspectGLB(glb({images:[{uri:'data:image/png;base64,AA=='}]})),/embedded/));
 test('given excessive texture count, reject allocation',()=>assert.throws(()=>inspectGLB(glb({textures:Array(9).fill({})})),/count/));
 test('given required compression extension, require separate validation',()=>assert.throws(()=>inspectGLB(glb({extensionsRequired:['KHR_draco_mesh_compression']})),/extensions/));
 test('given >8MiB model, reject byte budget',()=>assert.throws(()=>inspectGLB(new ArrayBuffer(8*1024*1024+1)),/8 MiB/));
 test('given corrupt GLB length, reject structure',()=>{const b=glb({asset:{version:'2.0'}});new DataView(b).setUint32(8,10,true);assert.throws(()=>inspectGLB(b),/Invalid/);});
 test('given authored fixture, hash matches recorded source',()=>{const bytes=fs.readFileSync(require.resolve('../hyper3d/diagnostic.glb')),manifest=require('../hyper3d/fixture.json');assert.equal(require('node:crypto').createHash('sha256').update(bytes).digest('hex'),manifest.sha256);});
})();
