export type FinalMaterialKind = 'school-print' | 'external-passage' | 'wrong-note'
export type FinalMaterial = { id:string; kind:FinalMaterialKind; title:string; school?:string; grade?:string; exam?:string; html:string; createdAt:string }
const KEY='buildit_final_materials'
export const loadFinalMaterials=():FinalMaterial[]=>{try{const value=JSON.parse(localStorage.getItem(KEY)||'[]');return Array.isArray(value)?value:[]}catch{return[]}}
export const addFinalMaterial=(item:Omit<FinalMaterial,'id'|'createdAt'>)=>{const next=[...loadFinalMaterials(),{...item,id:crypto.randomUUID(),createdAt:new Date().toISOString()}];localStorage.setItem(KEY,JSON.stringify(next));window.dispatchEvent(new Event('buildit-final-materials'));return next}
export const saveFinalMaterials=(items:FinalMaterial[])=>{localStorage.setItem(KEY,JSON.stringify(items));window.dispatchEvent(new Event('buildit-final-materials'))}
export const escapeHtml=(value:string)=>value.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]||c))

