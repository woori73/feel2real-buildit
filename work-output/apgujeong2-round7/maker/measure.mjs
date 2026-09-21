// measure.mjs — inject probe, run headless Chrome --dump-dom, print per-col overflow
import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
const file=resolve(process.argv[2]);
let html=readFileSync(file,'utf8');
const probe=`<script>
(function(){
 const out=[];
 document.querySelectorAll('.page').forEach((p,pi)=>{
  const pr=p.getBoundingClientRect();
  const cols=[...p.querySelectorAll(':scope > .cols > .col')].map((c,ci)=>{
   const r=c.getBoundingClientRect();
   let maxB=0,lastQ='';
   c.querySelectorAll('.qblock,.passage,.grp-label').forEach(el=>{
    const b=el.getBoundingClientRect().bottom;
    if(b>maxB){maxB=b;lastQ=(el.querySelector&&el.querySelector('.qnum')||{textContent:''}).textContent||el.className;}
   });
   return {ci,bottom:maxB-pr.top,boxH:r.height,overflow:maxB-(pr.top+r.height),last:lastQ};
  });
  out.push({page:pi+1,pageH:pr.height,cols});
 });
 document.title='MEASURE::'+JSON.stringify(out);
})();
</script>`;
html=html.replace('</body>',probe+'</body>');
const tmp=file.replace(/\.html?$/,'')+'.probe.html';
writeFileSync(tmp,html);
const url='file:///'+tmp.replace(/\\/g,'/');
const dom=execFileSync('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
 ['--headless=new','--disable-gpu','--no-sandbox','--user-data-dir='+resolve('maker','_crprofile'),'--virtual-time-budget=8000','--window-size=794,1123','--dump-dom',url],
 {maxBuffer:1024*1024*64}).toString();
const m=dom.match(/<title>MEASURE::(\[.*?\])<\/title>/s);
if(!m){console.log('NO MEASURE DATA');process.exit(1);}
for(const p of JSON.parse(m[1])){
 console.log(`page ${p.page} h=${p.pageH.toFixed(0)} `+p.cols.map(c=>`col${c.ci}: fill=${c.bottom.toFixed(0)}/${c.boxH.toFixed(0)} over=${c.overflow.toFixed(1)} last=${c.last}`).join(' | '));
}
