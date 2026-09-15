(() => {
  const photos = {
    'Midas M32': 'https://cdn-media.empowertribe.com/edab5c2e995a4db1bedea365f1303ab3/Image_MI_0603-ADX_M32_Catalog_B.png',
    'Behringer X32': 'https://audioprime.cdn.magazord.com.br/img/2025/03/produto/27959/04-mesa-de-som-x32-mixer-digital-32-canais-behringer.png?ims=fit-in%2F600x600%2Ffilters%3Afill%28white%29',
    'Yamaha CL5': 'https://www.dcpro.es/wp-content/uploads/2025/04/Yamaha-CL5-vista-arriba.jpg',
    'Allen & Heath Avantis': 'https://static.sonovente.com/img/library/zoom/95/optim/95793_5.jpg',
    'Vokal mikrofonu': 'https://eventelectricalhire.com/cdn/shop/files/Image_Editor_71.png?v=1759926923',
    'Elektro gitar': 'https://media.musiciansfriend.com/is/image/MMGS7/American-Standard-Rosewood-Fingerboard-HH-Stratocaster-Electric-Guitar-3-Color-Sunburst/J14003000001000-00-1600x1600.jpg',
    'Akustik gitar': 'https://carltonmusic.com/cdn/shop/files/IJP207007-2.jpg?v=1708960298&width=1445',
    'Bas gitar': 'https://www.toneshopguitars.com/cdn/shop/files/FenderAmericanProfessionalClassicPrecisionBassRosewood3-ColorSunburst.png?v=1760122452',
    'Davul seti': 'https://www.lojamusica.com/25695-large_default/bateria-yamaha-stage-custom-birch-sbp-0f5-ha-hw-680.jpg',
    'Keman': 'https://shop.sg.yamaha.com/media/catalog/product/y/v/yvn200s_91925_2400_front.jpg',
    'Piyano': 'https://au.yamaha.com/en/files/598FB2380FCB41F285FEA6CC489A2232_12073_1bfe3a7ce65f1983196859e521672aca.jpg?imhei=735&impolicy=resize&imwid=735',
    'Line Array L': 'https://soundmagcdn.fra1.cdn.digitaloceanspaces.com/product/160434/r/slidere35XDP___desktop_580_580.webp',
    'Line Array R': 'https://soundmagcdn.fra1.cdn.digitaloceanspaces.com/product/160434/r/slidere35XDP___desktop_580_580.webp'
  };

  const categoryPhotos = {
    instrument: photos['Vokal mikrofonu'],
    pa: photos['Line Array L'],
    console: photos['Midas M32'],
    stage: photos['Line Array L'],
    light: photos['Midas M32'],
    production: photos['Vokal mikrofonu']
  };

  const clean = s => String(s || '').replace(/\s+/g, ' ').trim();
  const nameFromItem = el => clean(el.querySelector('.meta')?.childNodes?.[0]?.textContent || '');
  const nameFromObject = el => clean(el.querySelector('.obj-icon + div')?.textContent || '');

  function urlFor(name, category) {
    if (photos[name]) return photos[name];
    const n = name.toLocaleLowerCase('tr-TR');
    if (n.includes('mikrofon')) return photos['Vokal mikrofonu'];
    if (n.includes('gitar')) return n.includes('bas') ? photos['Bas gitar'] : (n.includes('akustik') ? photos['Akustik gitar'] : photos['Elektro gitar']);
    if (['keman','viyola','çello','kontrbas','kemençe'].some(x => n.includes(x))) return photos['Keman'];
    if (n.includes('piyano') || n.includes('klavye')) return photos['Piyano'];
    if (n.includes('davul') || n.includes('drum')) return photos['Davul seti'];
    if (n.includes('line array')) return photos['Line Array L'];
    if (category && categoryPhotos[category]) return categoryPhotos[category];
    return null;
  }

  function apply(container) {
    container.querySelectorAll('.item').forEach(item => {
      const name = nameFromItem(item);
      const icon = item.querySelector('.icon');
      const url = urlFor(name, icon?.className.match(/icon-(\w+)/)?.[1]);
      if (!icon || !url || icon.dataset.photoUrl === url) return;
      icon.dataset.photoUrl = url;
      icon.innerHTML = `<img class="equipment-photo" src="${url}" alt="${name.replace(/&/g,'&amp;').replace(/"/g,'&quot;')}" loading="lazy" referrerpolicy="no-referrer">`;
    });

    container.querySelectorAll('.obj').forEach(obj => {
      const icon = obj.querySelector('.obj-icon');
      const name = nameFromObject(obj);
      if (!icon || !name) return;
      const url = urlFor(name);
      if (!url || icon.dataset.photoUrl === url) return;
      icon.dataset.photoUrl = url;
      icon.innerHTML = `<img class="equipment-photo stage-photo" src="${url}" alt="${name.replace(/&/g,'&amp;').replace(/"/g,'&quot;')}" loading="lazy" referrerpolicy="no-referrer">`;
    });
  }

  const style = document.createElement('style');
  style.textContent = `
    .equipment-photo{display:block;width:100%;height:100%;object-fit:contain;border-radius:8px;background:#fff}
    .icon .equipment-photo{padding:3px;box-sizing:border-box}
    .icon{overflow:hidden}
    .obj-icon{overflow:hidden;border-radius:7px}
    .stage-photo{padding:4px;box-sizing:border-box;background:#fff}
    .item .icon{background:#20262d}
  `;
  document.head.appendChild(style);

  const run = () => apply(document);
  const observer = new MutationObserver(() => requestAnimationFrame(run));
  observer.observe(document.body, {childList:true, subtree:true});
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run, {once:true});
  else run();
})();
