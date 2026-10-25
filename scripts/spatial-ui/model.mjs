// Original, texture-free F2R environment. No character geometry or likeness.
import * as T from 'three';
import {mergeGeometries} from 'three/addons/utils/BufferGeometryUtils.js';
const cyan = 0x54d9f8;
export function createEnvironment() {
  const root = new T.Group(); root.name = 'F2R_SPATIAL_ENVIRONMENT';
  const metal = new T.MeshStandardMaterial({ color: 0x162b3a, metalness: .58, roughness: .42 });
  const dark = new T.MeshStandardMaterial({ color: 0x080f1a, metalness: .55, roughness: .5 });
  const light = new T.MeshBasicMaterial({ color: cyan, toneMapped: false });light.name='f2r_state_light';
  const rail = new T.MeshBasicMaterial({color:0x35758e,transparent:true,opacity:.38,depthWrite:false,toneMapped:false});rail.name='f2r_structure_glow';
  const violet = new T.MeshBasicMaterial({ color: 0x8e79cf, transparent:true,opacity:.6,depthWrite:false,toneMapped:false });
  function mesh(g, m, name, x=0,y=0,z=0, parent=root) {
    const o=new T.Mesh(g,m);o.name=name;o.position.set(x,y,z);parent.add(o);return o;
  }
  const box = new T.BoxGeometry(1,1,1);
  function block(name,x,y,z,sx,sy,sz,m=metal,parent=root) {
    const o=mesh(box,m,name,x,y,z,parent);o.scale.set(sx,sy,sz);return o;
  }
  const stage = new T.Group();stage.name='01_CENTRAL_STAGE';root.add(stage);
  mesh(new T.CylinderGeometry(2.15,2.4,.22,64),metal,'podium',0,-.12,0,stage);
  mesh(new T.CylinderGeometry(1.95,1.95,.04,64),dark,'projection_surface',0,.01,0,stage);
  for(const [i,r] of [1.55,1.96,2.35].entries()) {
    const o=mesh(new T.TorusGeometry(r,.018,5,80),i===0?violet:light,'base_light_'+i,0,.04-i*.06,0,stage);
    o.rotation.x=Math.PI/2;
  }
  const tickGeo=new T.BoxGeometry(.025,.012,.13);
  for(let i=0;i<48;i++) {const a=i/48*Math.PI*2,o=mesh(tickGeo,light,'base_tick_'+i,Math.sin(a)*2.13,.045,Math.cos(a)*2.13,stage);o.rotation.y=a;}

  const rings=new T.Group();rings.name='02_HOLOGRAM_RINGS';rings.position.set(0,2.1,-.85);root.add(rings);
  for(let i=0;i<3;i++) {
    const o=mesh(new T.TorusGeometry(1.95+i*.23,.014+i*.004,5,112,Math.PI*(1.62+i*.09)),i===1?violet:light,'orbit_'+i,0,0,-i*.16,rings);
    o.rotation.set(.08*i,.2*(i-1),i*1.8);o.userData.orbit=i;
  }
  const lab=new T.Group();lab.name='03_RESEARCH_FACILITY';root.add(lab);
  const floor=mesh(new T.PlaneGeometry(18,16,18,16),dark,'floor',0,-.26,-1,lab);floor.rotation.x=-Math.PI/2;
  for(let i=0;i<3;i++) {
    const z=-2.6-i*2.4;
    for(const s of [-1,1]) {
      block('portal_column',s*(5.2+i*.25),2.3,z,.14,5.2,.25,metal,lab);
      block('portal_strip',s*(5.05+i*.25),2.4,z+.14,.025,4.9,.025,rail,lab);
    }
    block('portal_ceiling',0,4.9,z,10.5+i*.5,.12,.25,metal,lab);
    block('ceiling_light',0,4.82,z+.14,9.8+i*.5,.025,.025,rail,lab);
  }
  for(let i=-7;i<=7;i++) {
    block('floor_grid_x',i,-.25,-2,.008,.008,15,metal,lab);
    block('floor_grid_z',0,-.25,i,17,.008,.008,metal,lab);
  }
  for(const s of [-1,1]) {
    block('console_plinth',s*4.5,.1,-1,.6,.7,1,metal,lab);
    block('console_edge',s*4.5,.46,-1,.5,.025,.9,rail,lab);
  }
  const panels=new T.Group();panels.name='04_SERVICE_PANELS';root.add(panels);
  const glass=new T.MeshStandardMaterial({color:0x092b3c,emissive:0x063048,emissiveIntensity:.28,metalness:.1,roughness:.45,transparent:true,opacity:.65});
  for(let i=0;i<6;i++) {
    const s=i<3?-1:1,row=i%3,g=new T.Group();g.name='service_'+i;g.position.set(s*3.45,3.1-row*.93,.45);g.rotation.y=-s*.18;panels.add(g);g.userData.panel=i;
    block('glass_panel',0,0,0,2.1,.7,.065,glass,g);
    block('top_edge',0,.35,.04,2.1,.014,.025,light,g);
    block('bottom_edge',0,-.35,.04,2.1,.014,.025,light,g);
    block('status_strip',-.99,0,.045,.025,.61,.025,light,g);
    block('corner',.95,.27,.05,.12,.026,.03,violet,g);
  }
  // Batch static structures by material; rings and six independently interactive panels stay separate.
  for(const group of [stage,lab,...panels.children]) {
    group.updateMatrixWorld(true);const batches=new Map();
    for(const o of group.children)if(o.isMesh){
      const g=o.geometry.clone();o.updateMatrix();g.applyMatrix4(o.matrix);
      if(!batches.has(o.material))batches.set(o.material,[]);batches.get(o.material).push(g);
    }
    group.clear();
    for(const [m,geometries] of batches){const merged=mergeGeometries(geometries,false);group.add(new T.Mesh(merged,m));geometries.forEach(g=>g.dispose());}
  }
  return root;
}
