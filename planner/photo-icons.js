(() => {
  const F = name => 'https://commons.wikimedia.org/wiki/Special:FilePath/' + encodeURIComponent(name).replace(/%2F/g,'/');
  const photos = {
    'Midas M32': F('MIDAS M32 - Digital Consoles 1 - Live Sound (Week 10) - The Blackbird Academy (2021-12-01 12.39.41) IMG 9692.jpg'),
    'Behringer X32': F('Behringer X32 (1).jpg'),
    'Yamaha CL5': 'https://www.dcpro.es/wp-content/uploads/2025/04/Yamaha-CL5-vista-arriba.jpg',
    'Allen & Heath Avantis': F("Allen & Heath 'Avantis' console mixer.jpg"),
    'Digital Stagebox': F('Stagebox.jpg'),
    'Monitor World': F('Soundcraft Vi6 monitor world. Delta rehearsal @ Trackdown.jpg'),
    'FOH Konsol': F('FOH @ stage 1, Coconet Festival.jpg'),
    'Vokal mikrofonu': F('Sm58 microphone.jpg'),
    'Elektro gitar': F('A musician plays an electric guitar intensely on stage.jpg'),
    'Akustik gitar': 'https://carltonmusic.com/cdn/shop/files/IJP207007-2.jpg?v=1708960298&width=800',
    'Bas gitar': 'https://www.toneshopguitars.com/cdn/shop/files/FenderAmericanProfessionalClassicPrecisionBassRosewood3-ColorSunburst.png?v=1760122452',
    'Davul seti': F('Drum kit.jpg'),
    'Keman': F('Violin.jpg'),
    'Piyano': 'https://au.yamaha.com/en/files/598FB2380FCB41F285FEA6CC489A2232_12073_1bfe3a7ce65f1983196859e521672aca.jpg?imhei=735&impolicy=resize&imwid=735',
    'Line Array L': F('Line array speakers.jpg'),
    'Line Array R': F('Line array speakers.jpg'),
    '2×18” Subwoofer L': F('Line Array and Subs.jpg'),
    '2×18” Subwoofer R': F('Line Array and Subs.jpg'),
    '1×18” Monitor Sub L': F('Line Array and Subs.jpg'),
    '1×18” Monitor Sub R': F('Line Array and Subs.jpg'),
    'Sahne Monitörü': F('Monitorboxen (Live-Talente 2014) (08).jpg'),
    'Stagebox': F('Stagebox.jpg'),
    'Mikrofon Standı': F('Desktop microphone stand.jpg'),
    'Kablo Hattı': F('Audio multicore cable with XLR connectors and stage box.JPG'),
    'Truss Tower': F('Line array loudspeaker for sound reinforcement at 2009 Presidential Inauguration (clip).jpg')
  };
  const categoryPhotos = {
    instrument: photos['Elektro gitar'],
    pa: photos['Line Array L'],
    console: photos['Midas M32'],
    stage: photos['Truss Tower'],
    light: F('Yes concert 2010-12-01 (5252857366).jpg'),
    production: photos['FOH Konsol']
  };
  const clean = s => String(s || '').replace(/\s+/g, ' ').trim();
  const escape = s => clean(s).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  const nameFromItem = el => clean(el.querySelector('.meta')?.childNodes?.[0]?.textContent || '');
  const nameFromObject = el => clean(el.querySelector('.obj-icon + div')?.textContent || '');
  function urlFor(name, category) {
    if (photos[name]) return photos[name];
    const n = clean(name).toLocaleLowerCase('tr-TR');
    if (n.includes('gitar')) return n.includes('bas') ? photos['Bas gitar'] : (n.includes('akustik') ? photos['Akustik gitar'] : photos['Elektro gitar']);
    if (['keman','viyola','çello','kontrbas','kemençe'].some(x => n.includes(x))) return photos['Keman'];
    if (n.includes('piyano') || n.includes('klavye')) return photos['Piyano'];
    if (n.includes('davul') || n.includes('drum')) return photos['Davul seti'];
    if (n.includes('line array')) return photos['Line Array L'];
    if (n.includes('subwoofer')) return photos['2×18” Subwoofer L'];
    if (n.includes('stagebox')) return photos['Stagebox'];
    if (n.includes('mikrofon')) return photos['Vokal mikrofonu'];
    if (category && categoryPhotos[category]) return categoryPhotos[category];
    return null;
  }
  function apply(container) {
    container.querySelectorAll('.item').forEach(item => {
      const name = nameFromItem(item), icon = item.querySelector('.icon');
      const category = icon?.className.match(/icon-(\w+)/)?.[1], url = urlFor(name, category);
      if (!icon || !url || icon.dataset.photoUrl === url) return;
      icon.dataset.photoUrl = url;
      icon.innerHTML = `<img class="equipment-photo" src="${url}" alt="${escape(name)}" loading="lazy" referrerpolicy="no-referrer">`;
    });
    container.querySelectorAll('.obj').forEach(obj => {
      const icon = obj.querySelector('.obj-icon'), name = nameFromObject(obj), url = urlFor(name);
      if (!icon || !url || icon.dataset.photoUrl === url) return;
      icon.dataset.photoUrl = url;
      icon.innerHTML = `<img class="equipment-photo stage-photo" src="${url}" alt="${escape(name)}" loading="lazy" referrerpolicy="no-referrer">`;
    });
  }
  const style = document.createElement('style');
  style.textContent = `.equipment-photo{display:block;width:100%;height:100%;object-fit:contain;border-radius:7px;background:#fff}.icon .equipment-photo{padding:2px;box-sizing:border-box}.icon{overflow:hidden}.item .icon{width:58px;height:44px;flex:0 0 58px;background:#20262d;border:1px solid #39434d}.item .meta{font-weight:500}.obj-icon{height:52px;overflow:hidden;border-radius:7px;background:#fff}.stage-photo{padding:3px;box-sizing:border-box}header > div:first-child{display:flex;align-items:center;gap:10px}header > div:first-child:before{content:'';display:block;width:132px;height:38px;background:url('stagepulse-logo.svg') center/contain no-repeat}header > div:first-child b{display:none}header > div:first-child span{margin-left:0}`;
  document.head.appendChild(style);
  const run = () => apply(document);
  const observer = new MutationObserver(() => requestAnimationFrame(run));
  observer.observe(document.body, {childList:true, subtree:true});
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run, {once:true}); else run();
})();
