(()=>{
const disabled=new Set(['instrument','stage']);
const clean=s=>String(s||'').replace(/\s+/g,' ').trim();
const nameOfItem=e=>clean(e.querySelector('.meta')?.childNodes?.[0]?.textContent||'');
const nameOfObj=e=>clean(e.querySelector('.obj-icon + div')?.textContent||'');
function apply(root){root.querySelectorAll('.item').forEach(item=>{const icon=item.querySelector('.icon');const cat=icon?.className.match(/icon-(\w+)/)?.[1];if(!icon||!disabled.has(cat))return;icon.dataset.photoMode='disabled';});root.querySelectorAll('.obj').forEach(obj=>{const icon=obj.querySelector('.obj-icon');if(!icon)return;const name=nameOfObj(obj);const item=[...document.querySelectorAll('#library .item')].find(e=>nameOfItem(e)===name);const cat=item?.querySelector('.icon')?.className.match(/icon-(\w+)/)?.[1];if(disabled.has(cat))icon.dataset.photoMode='disabled';});}
const style=document.createElement('style');style.textContent='.item .icon,.obj-icon{overflow:hidden}.equipment-photo{display:none!important}';document.head.appendChild(style);
const run=()=>apply(document);const observer=new MutationObserver(run);observer.observe(document.body,{childList:true,subtree:true});if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',run,{once:true});else run();
})();
