const I=(window.StagePulseEquipmentCatalog?.items||[]).map(x=>({...x,ratio:x.w/(x.h||1)})),L={instrument:'Enstrüman',pa:'PA / Ses',console:'Konsol',stage:'Sahne',light:'Işık / Video',production:'Aksesuar'},$=id=>document.getElementById(id),lib=$('library'),stage=$('stage');let cat='all',drag=null,O=[],sel=null,z=1,S=80,hist=[],hi=-1;const esc=s=>String(s??'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c])),get=()=>O.find(o=>o.id===sel),snap=v=>$('snap')?.checked?Math.round(v/.1)*.1:v;
function imgSrc(src){if(typeof src!=='string'||!src)return '';if(src.startsWith('data:image/'))return src;if(/^(file|content|https?):\/\//i.test(src))return src;const clean=src.replace(/^\.\//,'');if(location.protocol==='file:')return 'file:///android_asset/planner/'+clean;return new URL(clean,document.baseURI).href}function safeImage(src){const u=imgSrc(src);return u.replace(/"/g,'%22')}function library(){const q=($('search')?.value||'').toLocaleLowerCase('tr-TR');lib.innerHTML='';I.filter(x=>(cat==='all'||x.cat===cat)&&x.name.toLocaleLowerCase('tr-TR').includes(q)).forEach(x=>{const d=document.createElement('div');d.className='item';d.draggable=true;d.innerHTML=`<div class="icon"><img src="${safeImage(x.img)}" alt="${esc(x.name)}" loading="lazy" onerror="this.style.display='none';this.closest('.icon')?.classList.add('img-missing')"></div><div class="meta">${esc(x.name)}<small>${L[x.cat]||x.cat}</small></div>`;d.ondragstart=()=>drag=x;d.ondblclick=()=>add(x,120,120);lib.appendChild(d)})}
const transform=o=>`rotate(${o.r||0}deg) scale(${o.fx?-1:1},${o.fy?-1:1})`;
function add(x,xp=100,yp=100){const id=crypto.randomUUID?.()||`${Date.now()}-${Math.random().toString(16).slice(2)}`;const o={...x,id,x:snap(Number.isFinite(xp)?xp:100),y:snap(Number.isFinite(yp)?yp:100),r:0,z:O.length,fx:false,fy:false};O.push(o);draw(o);sel=o.id;sync();commit();save()}
let toolMode='select',activePointers=new Map(),gesture=null,mouseDrag=null;
function setToolMode(mode){toolMode=mode;document.querySelectorAll('.bottomTools .tool').forEach(b=>b.classList.toggle('active',b.dataset.tool===mode));}
function applyObjectGeometry(o){const e=stage?.querySelector(`[data-id="${o.id}"]`);if(!e)return;e.style.left=o.x+'px';e.style.top=o.y+'px';e.style.width=Math.max(30,o.w*S*z)+'px';e.style.height=Math.max(25,o.h*S*z)+'px';e.style.transform=transform(o);}
function draw(o){
  stage?.querySelector(`[data-id="${o.id}"]`)?.remove();
  if(!stage)return;
  const e=document.createElement('div');
  e.className='obj'+(sel===o.id?' selected':'');
  e.dataset.id=o.id;
  e.style.cssText=`left:${o.x}px;top:${o.y}px;width:${Math.max(30,o.w*S*z)}px;height:${Math.max(25,o.h*S*z)}px;transform:${transform(o)};z-index:${o.z}`;
  e.innerHTML=`<div class="obj-icon"><img src="${safeImage(o.img)}" alt="${esc(o.name)}" loading="lazy" onerror="this.style.display='none';this.closest('.obj-icon')?.classList.add('img-missing')"></div><span class="del">×</span><span class="rot">↻</span><span class="resize">↗</span>`;
  e.querySelector('.del').onclick=a=>{a.stopPropagation();O=O.filter(x=>x.id!==o.id);e.remove();sel=null;sync();commit();save()};
  e.querySelector('.rot').onclick=a=>{a.stopPropagation();o.r=(o.r+15)%360;draw(o);sel=o.id;sync();commit();save()};
  const rs=e.querySelector('.resize');
  rs.onpointerdown=a=>{
    a.stopPropagation();a.preventDefault();
    const sx=a.clientX,sy=a.clientY,w=o.w,h=o.h;
    const moveResize=b=>{o.w=Math.max(.1,w+(b.clientX-sx)/(S*z));o.h=Math.max(.1,h+(b.clientY-sy)/(S*z));applyObjectGeometry(o);sync()};
    const upResize=()=>{window.removeEventListener('pointermove',moveResize);window.removeEventListener('pointerup',upResize);window.removeEventListener('pointercancel',upResize);commit();save()};
    window.addEventListener('pointermove',moveResize);
    window.addEventListener('pointerup',upResize,{once:true});
    window.addEventListener('pointercancel',upResize,{once:true});
  };
  stage.appendChild(e);
}
stage?.addEventListener('pointerdown',e=>{
  const objEl=e.target.closest?.('.obj');
  if(!objEl||e.target.closest('.del,.rot,.resize'))return;
  const o=O.find(x=>x.id===objEl.dataset.id);
  if(!o)return;
  e.preventDefault();
  sel=o.id;sync();
  if(e.pointerType==='touch'){
    activePointers.set(e.pointerId,{x:e.clientX,y:e.clientY,id:o.id});
    try{stage.setPointerCapture(e.pointerId)}catch(_){}
    if(activePointers.size===2){
      const pts=[...activePointers.values()].filter(p=>p.id===o.id);
      if(pts.length===2){
        const c0={x:(pts[0].x+pts[1].x)/2,y:(pts[0].y+pts[1].y)/2};
        gesture={id:o.id,x:o.x,y:o.y,w:o.w,h:o.h,r:o.r||0,c:c0,d:Math.max(1,Math.hypot(pts[1].x-pts[0].x,pts[1].y-pts[0].y)),a:Math.atan2(pts[1].y-pts[0].y,pts[1].x-pts[0].x)};
        mouseDrag=null;
      }
    } else {
      mouseDrag={id:o.id,sx:e.clientX,sy:e.clientY,x:o.x,y:o.y,r:o.r||0,mode:toolMode};
    }
  } else {
    mouseDrag={id:o.id,sx:e.clientX,sy:e.clientY,x:o.x,y:o.y,r:o.r||0,mode:toolMode};
    try{stage.setPointerCapture(e.pointerId)}catch(_){}
  }
});
stage?.addEventListener('pointermove',e=>{
  if(activePointers.has(e.pointerId))activePointers.set(e.pointerId,{x:e.clientX,y:e.clientY,id:activePointers.get(e.pointerId).id});
  const o=get(); if(!o)return;
  if(gesture&&activePointers.size>=2){
    const pts=[...activePointers.values()].filter(p=>p.id===gesture.id).slice(0,2);
    if(pts.length<2)return;
    const c1={x:(pts[0].x+pts[1].x)/2,y:(pts[0].y+pts[1].y)/2};
    const d=Math.max(1,Math.hypot(pts[1].x-pts[0].x,pts[1].y-pts[0].y));
    const ang=Math.atan2(pts[1].y-pts[0].y,pts[1].x-pts[0].x);
    const sc=d/gesture.d;
    o.w=Math.max(.1,gesture.w*sc);o.h=Math.max(.1,gesture.h*sc);
    o.r=gesture.r+(ang-gesture.a)*180/Math.PI;
    o.x=Math.max(0,gesture.x+(c1.x-gesture.c.x)/z);o.y=Math.max(0,gesture.y+(c1.y-gesture.c.y)/z);
    applyObjectGeometry(o);sync();return;
  }
  if(!mouseDrag||mouseDrag.id!==o.id)return;
  const dx=e.clientX-mouseDrag.sx,dy=e.clientY-mouseDrag.sy;
  if(mouseDrag.mode==='rotate'){
    const r=stage.getBoundingClientRect(),cx=r.left+o.x+(o.w*S*z)/2,cy=r.top+o.y+(o.h*S*z)/2;
    o.r=mouseDrag.r+(Math.atan2(e.clientY-cy,e.clientX-cx)-Math.atan2(mouseDrag.sy-cy,mouseDrag.sx-cx))*180/Math.PI;
  }else if(mouseDrag.mode==='scale'){
    o.w=Math.max(.1,o.w+dx/(S*z));o.h=Math.max(.1,o.h+dy/(S*z));
  }else{
    o.x=Math.max(0,snap(mouseDrag.x+dx/z));o.y=Math.max(0,snap(mouseDrag.y+dy/z));
  }
  applyObjectGeometry(o);sync();
});
stage?.addEventListener('pointerup',e=>{
  activePointers.delete(e.pointerId);
  if(activePointers.size<2)gesture=null;
  if(!activePointers.size&&mouseDrag){mouseDrag=null;commit();save();}
});
stage?.addEventListener('pointercancel',e=>{activePointers.delete(e.pointerId);if(activePointers.size<2)gesture=null;if(!activePointers.size){mouseDrag=null;commit();save();}});
document.querySelectorAll('.bottomTools .tool').forEach(b=>b.addEventListener('click',()=>setToolMode(b.dataset.tool||'select')));
setToolMode('select');
function sync(){const o=get(),p=$('selectionPanel'),e=$('selectionEmpty');if(!o){if(e)e.hidden=false;if(p)p.hidden=true;return}e.hidden=true;p.hidden=false;$('propName').value=o.name;$('propX').value=(o.x/S).toFixed(2);$('propY').value=(o.y/S).toFixed(2);$('propR').value=Math.round(o.r||0);$('propW').value=o.w.toFixed(2);$('propH').value=o.h.toFixed(2)}
function apply(){const o=get();if(!o)return;const x=Number($('propX').value),y=Number($('propY').value),r=Number($('propR').value),w=Number($('propW').value),h=Number($('propH').value);if(![x,y,r,w,h].every(Number.isFinite)||w<=0||h<=0)return;o.name=String($('propName').value||'Ekipman').slice(0,200);o.x=Math.max(0,x*S);o.y=Math.max(0,y*S);o.r=r;o.w=Math.max(.1,w);o.h=Math.max(.1,h);draw(o);commit();save()}
function state(){return JSON.stringify({O,n:String($('projectName')?.value||'').slice(0,200),note:String($('projectNote')?.value||'').slice(0,5000)})}window.stagepulsePlotObjects=()=>O.map(o=>({...o}));function commit(){const s=state();if(hist[hi]===s)return;hist=hist.slice(0,hi+1);hist.push(s);hi=hist.length-1}function restore(s){const d=typeof s==='string'?JSON.parse(s):s;if(!d||!Array.isArray(d.O))throw new Error('Geçersiz StagePulse Plot dosyası');O=d.O.filter(o=>o&&typeof o==='object').map(o=>({...o,id:String(o.id||crypto.randomUUID?.()||Date.now()),name:String(o.name||'Ekipman').slice(0,200),x:Number.isFinite(Number(o.x))?Number(o.x):0,y:Number.isFinite(Number(o.y))?Number(o.y):0,w:Number.isFinite(Number(o.w))&&Number(o.w)>0?Number(o.w):.5,h:Number.isFinite(Number(o.h))&&Number(o.h)>0?Number(o.h):.5,r:Number.isFinite(Number(o.r))?Number(o.r):0,z:Number.isFinite(Number(o.z))?Number(o.z):0,fx:Boolean(o.fx),fy:Boolean(o.fy),img:typeof o.img==='string'?o.img:''}));if($('projectName'))$('projectName').value=String(d.n||'').slice(0,200);if($('projectNote'))$('projectNote').value=String(d.note||'').slice(0,5000);sel=null;stage.querySelectorAll('.obj').forEach(e=>e.remove());O.forEach(o=>draw(o));sync()}function save(){try{localStorage.setItem('stagepulsePlot',state())}catch(_){}}function load(){try{const s=localStorage.getItem('stagepulsePlot');if(s)restore(s);hist=[state()];hi=0}catch(_){}}
function setZoom(v){z=Math.max(.35,Math.min(1.8,v));$('zoomValue').textContent=Math.round(z*100)+'%';O.forEach(draw)}function front(){const o=get();if(o){o.z=Math.max(...O.map(x=>x.z),0)+1;draw(o);commit();save()}}function back(){const o=get();if(o){o.z=0;O.filter(x=>x!==o).forEach(x=>x.z++);O.forEach(draw);commit();save()}}function exportJ(){const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([state()],{type:'application/json'}));a.download='stagepulse-plot.json';a.click()}function importJ(f){if(!f)return;const r=new FileReader();r.onload=()=>{try{restore(r.result);commit();save()}catch(_){alert('Geçersiz StagePulse Plot dosyası.');}};r.onerror=()=>alert('Dosya okunamadı.');r.readAsText(f)}
stage?.addEventListener('dragover',e=>e.preventDefault());stage?.addEventListener('drop',e=>{e.preventDefault();if(!drag)return;const r=stage.getBoundingClientRect();add(drag,(e.clientX-r.left)/z,(e.clientY-r.top)/z);drag=null});$('clearBtn')?.addEventListener('click',()=>{O=[];sel=null;stage.querySelectorAll('.obj').forEach(e=>e.remove());sync();commit();save()});$('newBtn')?.addEventListener('click',()=>{O=[];sel=null;stage.querySelectorAll('.obj').forEach(e=>e.remove());sync();commit();save()});$('saveBtn')?.addEventListener('click',save);function printPlot(){if(window.AndroidPrint?.print){window.AndroidPrint.print();}else{window.print();}}$('printBtn')?.addEventListener('click',printPlot);$('exportBtn')?.addEventListener('click',exportJ);$('loadBtn')?.addEventListener('click',()=>$('fileLoad').click());$('fileLoad')?.addEventListener('change',e=>importJ(e.target.files[0]));$('undoBtn')?.addEventListener('click',()=>{if(hi>0){hi--;restore(hist[hi]);save()}});$('redoBtn')?.addEventListener('click',()=>{if(hi<hist.length-1){hi++;restore(hist[hi]);save()}});$('zoomOut')?.addEventListener('click',()=>setZoom(z-.1));$('zoomIn')?.addEventListener('click',()=>setZoom(z+.1));$('fitBtn')?.addEventListener('click',()=>setZoom(1));$('grid')?.addEventListener('change',e=>stage.classList.toggle('grid',e.target.checked));$('noBackdrop')?.addEventListener('change',e=>stage.querySelector('.stage-back').style.display=e.target.checked?'none':'');$('stageW')?.addEventListener('input',()=>stage.style.width=Math.max(320,+$('stageW').value*S)+'px');$('stageD')?.addEventListener('input',()=>stage.style.height=Math.max(240,+$('stageD').value*S)+'px');$('duplicateBtn')?.addEventListener('click',()=>{const o=get();if(o)add(o,o.x+30,o.y+30)});$('frontBtn')?.addEventListener('click',front);$('backBtn')?.addEventListener('click',back);$('deleteBtn')?.addEventListener('click',()=>get()&&document.querySelector(`[data-id="${sel}"] .del`).click());$('flipH')?.addEventListener('click',()=>{const o=get();if(o){o.fx=!o.fx;draw(o);commit();save()}});$('flipV')?.addEventListener('click',()=>{const o=get();if(o){o.fy=!o.fy;draw(o);commit();save()}});$('resetR')?.addEventListener('click',()=>{const o=get();if(o){o.r=0;draw(o);commit();save()}});['propName','propX','propY','propR','propW','propH'].forEach(id=>$(id)?.addEventListener('input',apply));$('search')?.addEventListener('input',library);document.querySelectorAll('.tab').forEach(b=>b.onclick=()=>{document.querySelectorAll('.tab').forEach(x=>x.classList.remove('active'));b.classList.add('active');cat=b.dataset.cat;library()});library();stage.style.width=10*S+'px';stage.style.height=8*S+'px';load();
