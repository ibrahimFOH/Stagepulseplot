(() => {
  const disabledCategories = new Set(['instrument', 'stage']);
  const clean = s => String(s || '').replace(/\s+/g, ' ').trim();
  const escape = s => clean(s).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  const nameFromItem = el => clean(el.querySelector('.meta')?.childNodes?.[0]?.textContent || '');
  const nameFromObject = el => clean(el.querySelector('.obj-icon + div')?.textContent || '');

  function apply(container) {
    container.querySelectorAll('.item').forEach(item => {
      const icon = item.querySelector('.icon');
      const category = icon?.className.match(/icon-(\w+)/)?.[1];
      if (!icon || !disabledCategories.has(category)) return;
      const name = nameFromItem(item);
      icon.dataset.photoMode = 'disabled';
      icon.innerHTML = icon.querySelector('svg') ? icon.innerHTML : '';
      if (!icon.querySelector('svg')) {
        icon.innerHTML = `<span class="equipment-photo-fallback" aria-hidden="true">${escape(name.slice(0, 1))}</span>`;
      }
    });

    container.querySelectorAll('.obj').forEach(obj => {
      const icon = obj.querySelector('.obj-icon');
      if (!icon || icon.dataset.photoMode === 'disabled') return;
      const name = nameFromObject(obj);
      const item = [...document.querySelectorAll('#library .item')].find(el => nameFromItem(el) === name);
      const category = item?.querySelector('.icon')?.className.match(/icon-(\w+)/)?.[1];
      if (!category || !disabledCategories.has(category)) return;
      icon.dataset.photoMode = 'disabled';
      const svg = icon.querySelector('svg');
      if (!svg) icon.innerHTML = `<span class="equipment-photo-fallback" aria-hidden="true">${escape(name.slice(0, 1))}</span>`;
    });
  }

  const style = document.createElement('style');
  style.textContent = `
    .equipment-photo-fallback{display:flex;align-items:center;justify-content:center;width:100%;height:100%;font-size:22px;font-weight:800;opacity:.85}
    .item .icon{overflow:hidden}
    .obj-icon{overflow:hidden}
  `;
  document.head.appendChild(style);

  const run = () => apply(document);
  const observer = new MutationObserver(() => requestAnimationFrame(run));
  observer.observe(document.body, {childList:true, subtree:true});
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run, {once:true}); else run();
})();
