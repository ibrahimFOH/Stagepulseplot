(()=>{
const $=s=>document.querySelector(s);
const groups={
 instrument:['Klavye','Gitar','Bas gitar','Davul','Keman','Saksafon','Trompet','Trombon','Klarnet','Flüt','Akordeon','Bongo','Cajon','Piyano'],
 pa:['Mikrofon','Wedge','Monitor','Line Array','Hoparlör','Subwoofer','DI Box','Stagebox'],
 console:['Midas M32','Behringer X32','Yamaha CL5','Avantis','Mixer','Stagebox'],
 stage:['Riser','Truss','Sandalye','Müzik Sehpası','Masa'],
 light:['Moving Head','LED Par','Followspot','Strobe','Blinder','Işık'],
 production:['FOH','Kamera','Video','Kablo','Power']
};
function apply(){
 document.querySelectorAll('.item').forEach(item=>{
  const icon=item.querySelector('.icon');
  if(!icon)return;
  icon.querySelectorAll('img.equipment-photo').forEach(i=>i.remove());
  icon.classList.remove('photo-icon');
 });
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',apply,{once:true});else apply();
})();
