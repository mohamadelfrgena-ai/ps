/* Skin Lab local companion UI. No network calls; preview/export only. */
(function () {
  'use strict';
  var core = window.SkinLabCore;
  var preview = window.SkinLabPreview;
  var $ = function (id) { return document.getElementById(id); };

  var state = {
    packs: [],
    images: new Map(),
    selectedId: null,
    selected: new Set(),
    favorites: new Set(),
    yaw: -25,
    spinning: false
  };
  var LS_FAV = 'skinlab.favorites.v1';
  var LS_LAST = 'skinlab.lastPreview.v1';
  var LS_NAME = 'skinlab.exportName.v1';

  function storageGet(key) { try { return localStorage.getItem(key); } catch (_e) { return null; } }
  function storageSet(key, value) { try { localStorage.setItem(key, value); } catch (_e) {} }
  function loadFavorites() {
    try {
      var arr = JSON.parse(storageGet(LS_FAV) || '[]');
      if (Array.isArray(arr)) arr.forEach(function (id) { if (typeof id === 'string') state.favorites.add(id); });
    } catch (_e) {}
  }
  function saveFavorites() { storageSet(LS_FAV, JSON.stringify(Array.from(state.favorites))); }
  function setStatus(el, msg, kind) {
    el.textContent = msg || '';
    el.className = 'status' + (kind ? ' ' + kind : '');
  }
  function allSkins() {
    var out = [];
    state.packs.forEach(function (p) { p.skins.forEach(function (s) { out.push({ pack: p, skin: s }); }); });
    return out;
  }
  function findSkin(id) {
    for (var i = 0; i < state.packs.length; i++) {
      var p = state.packs[i];
      for (var j = 0; j < p.skins.length; j++) if (p.skins[j].id === id) return { pack: p, skin: p.skins[j] };
    }
    return null;
  }
  function imageFor(skin) {
    if (state.images.has(skin.id)) return state.images.get(skin.id).promise;
    var url = URL.createObjectURL(new Blob([skin.bytes], { type: 'image/png' }));
    var img = new Image();
    var promise = new Promise(function (resolve, reject) {
      img.onload = function () { resolve(img); };
      img.onerror = function () { URL.revokeObjectURL(url); reject(new Error('Browser could not decode PNG: ' + skin.name)); };
      img.src = url;
    });
    state.images.set(skin.id, { url: url, promise: promise });
    return promise;
  }
  function releaseImages() {
    state.images.forEach(function (v) { URL.revokeObjectURL(v.url); });
    state.images.clear();
  }

  function addPack(pack, source) {
    pack.source = source || 'import';
    var idx = state.packs.findIndex(function (p) { return p.id === pack.id; });
    if (idx >= 0) state.packs[idx] = pack; else state.packs.push(pack);
    return pack;
  }
  function loosePack(name, items, model) {
    var skins = items.map(function (item, i) {
      core.assertSkinPng(item.bytes);
      return {
        id: core.hashString(name + ':' + item.name + ':' + i + ':' + core.crc32(item.bytes)),
        name: item.name, model: model, geometry: core.GEOMETRY[model],
        texturePath: item.name + '.png', bytes: item.bytes, width: 64, height: 64, type: 'free'
      };
    });
    return {
      id: 'loose-' + core.hashString(name + ':' + skins.map(function (s) { return s.id; }).join(',')),
      name: name, skins: skins, warnings: ['Loose PNG import: model was chosen in the UI, not read from pack metadata.'],
      manifest: null, skinsJson: null
    };
  }

  async function importPackFiles(files) {
    var ok = 0, errors = [];
    for (var i = 0; i < files.length; i++) {
      var file = files[i];
      try {
        if (file.size > core.LIMITS.MAX_ARCHIVE_BYTES) throw new Error('Archive exceeds 32 MiB limit');
        var filesMap = await core.readZip(await file.arrayBuffer());
        var pack = core.validatePack(filesMap);
        addPack(pack, file.name);
        ok++;
      } catch (e) { errors.push(file.name + ': ' + e.message); }
    }
    afterImport(ok, errors, 'pack archive');
  }
  async function importPngFiles(files, packName) {
    var items = [], errors = [];
    for (var i = 0; i < files.length; i++) {
      var file = files[i];
      try {
        if (!/\.png$/i.test(file.name)) throw new Error('Not a .png file');
        if (file.size > core.LIMITS.MAX_FILE_BYTES) throw new Error('PNG exceeds 8 MiB limit');
        var bytes = new Uint8Array(await file.arrayBuffer());
        core.assertSkinPng(bytes);
        items.push({ name: file.name.replace(/\.png$/i, ''), bytes: bytes });
      } catch (e) { errors.push(file.name + ': ' + e.message); }
    }
    var ok = 0;
    if (items.length) {
      try {
        var model = $('png-model').value === 'slim' ? 'slim' : 'classic';
        addPack(loosePack(packName || 'Loose PNG skins', items, model), 'png-import');
        ok = items.length;
      } catch (e) { errors.push(e.message); }
    }
    afterImport(ok, errors, 'PNG skin');
  }
  async function importFolder(files) {
    var list = Array.from(files);
    var roots = new Set();
    list.forEach(function (f) {
      var p = f.webkitRelativePath || f.name;
      if (/(^|\/)skins\.json$/i.test(p)) roots.add(p.slice(0, p.lastIndexOf('/') + 1));
    });
    if (roots.size === 0) {
      var pngs = list.filter(function (f) { /\.png$/i.test(f.name); });
      var top = (list[0] && list[0].webkitRelativePath || '').split('/')[0] || 'Folder skins';
      return importPngFiles(pngs, top);
    }
    var rootList = Array.from(roots).sort(function (a, b) { return b.length - a.length; });
    var groups = new Map(rootList.map(function (r) { return [r, []]; }));
    list.forEach(function (f) {
      var p = f.webkitRelativePath || f.name;
      for (var i = 0; i < rootList.length; i++) {
        var r = rootList[i];
        if (p.indexOf(r) === 0) { groups.get(r).push({ rel: p.slice(r.length), file: f }); break; }
      }
    });
    var ok = 0, errors = [];
    for (var [root, entries] of groups) {
      try {
        var map = new Map();
        for (var e of entries) {
          if (!e.rel || e.rel.endsWith('/')) continue;
          if (!core.safePath(e.rel)) throw new Error('Unsafe folder path: ' + e.rel);
          if (e.file.size > core.LIMITS.MAX_FILE_BYTES) throw new Error('File too large: ' + e.rel);
          map.set(e.rel, new Uint8Array(await e.file.arrayBuffer()));
        }
        var pack = core.validatePack(map);
        addPack(pack, root || 'folder');
        ok++;
      } catch (err) { errors.push((root || 'folder') + ': ' + err.message); }
    }
    afterImport(ok, errors, 'skin folder');
  }
  function afterImport(ok, errors, label) {
    render();
    var msg = ok ? 'Imported ' + ok + ' ' + label + (ok === 1 ? '' : 's') + '.' : 'No ' + label + 's imported.';
    if (errors.length) msg += '\n' + errors.slice(0, 6).join('\n') + (errors.length > 6 ? '\n…' + (errors.length - 6) + ' more' : '');
    setStatus($('import-status'), msg, errors.length ? 'err' : 'ok');
    if (!state.selectedId) {
      var first = allSkins()[0];
      if (first) selectSkin(first.skin.id);
    }
  }

  async function loadDemo() {
    try {
      if (!window.SKIN_LAB_DEMO_BASE64) throw new Error('Demo data missing');
      var bin = atob(window.SKIN_LAB_DEMO_BASE64);
      var bytes = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      var pack = core.validatePack(await core.readZip(bytes));
      addPack(pack, 'original-demo');
      render();
      setStatus($('import-status'), 'Loaded original synthetic demo (2 skins).', 'ok');
      var last = storageGet(LS_LAST);
      selectSkin(last && findSkin(last) ? last : pack.skins[0].id);
    } catch (e) { setStatus($('import-status'), 'Demo failed: ' + e.message, 'err'); }
  }

  function visibleSkins() {
    var q = $('search').value.trim().toLowerCase();
    var packFilter = $('pack-filter').value;
    var favOnly = $('favorites-only').checked;
    return allSkins().filter(function (item) {
      if (packFilter && item.pack.id !== packFilter) return false;
      if (favOnly && !state.favorites.has(item.skin.id)) return false;
      if (q && (item.skin.name + ' ' + item.pack.name).toLowerCase().indexOf(q) === -1) return false;
      return true;
    });
  }
  function render() {
    var packSelect = $('pack-filter');
    var current = packSelect.value;
    packSelect.innerHTML = '<option value="">All packs</option>';
    state.packs.forEach(function (p) {
      var opt = document.createElement('option');
      opt.value = p.id; opt.textContent = p.name + ' (' + p.skins.length + ')';
      packSelect.appendChild(opt);
    });
    packSelect.value = state.packs.some(function (p) { return p.id === current; }) ? current : '';

    var gallery = $('gallery');
    gallery.innerHTML = '';
    var items = visibleSkins();
    items.forEach(function (item) {
      var s = item.skin, p = item.pack;
      var card = document.createElement('div');
      card.className = 'card' + (s.id === state.selectedId ? ' active' : '');
      card.tabIndex = 0;
      card.setAttribute('role', 'button');
      card.setAttribute('aria-label', 'Preview ' + s.name);
      var canvas = document.createElement('canvas'); canvas.width = 96; canvas.height = 120;
      var name = document.createElement('div'); name.className = 'name'; name.textContent = s.name;
      var meta = document.createElement('div'); meta.className = 'meta'; meta.textContent = p.name + ' · ' + (s.model === 'slim' ? 'slim' : 'classic');
      var actions = document.createElement('div'); actions.className = 'actions';
      var fav = document.createElement('button'); fav.type = 'button'; fav.className = 'secondary';
      fav.textContent = state.favorites.has(s.id) ? '★' : '☆';
      fav.setAttribute('aria-label', 'Toggle favorite');
      fav.addEventListener('click', function (ev) { ev.stopPropagation(); toggleFavorite(s.id); });
      var check = document.createElement('input'); check.type = 'checkbox'; check.checked = state.selected.has(s.id);
      check.setAttribute('aria-label', 'Include in export');
      check.addEventListener('click', function (ev) { ev.stopPropagation(); });
      check.addEventListener('change', function () { setExportSelected(s.id, check.checked); });
      actions.appendChild(fav); actions.appendChild(check);
      card.appendChild(canvas); card.appendChild(name); card.appendChild(meta); card.appendChild(actions);
      card.addEventListener('click', function () { selectSkin(s.id); });
      card.addEventListener('keydown', function (ev) { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); selectSkin(s.id); } });
      gallery.appendChild(card);
      imageFor(s).then(function (img) { preview.drawModel(canvas, img, s.model, -25, { scale: 3.2, pitchDeg: -10 }); }).catch(function () {});
    });
    var total = allSkins().length;
    $('counts').textContent = items.length + ' shown / ' + total + ' total · ' + state.selected.size + ' selected';
    $('export-pack').disabled = state.selected.size === 0;
    renderPreview();
  }
  function renderPreview() {
    var found = state.selectedId && findSkin(state.selectedId);
    $('preview-empty').hidden = !!found;
    $('preview-body').hidden = !found;
    if (!found) return;
    var s = found.skin;
    $('preview-name').textContent = s.name;
    $('preview-meta').textContent = found.pack.name + ' · ' + (s.model === 'slim' ? 'slim' : 'classic') + ' · 64×64';
    $('favorite-toggle').textContent = state.favorites.has(s.id) ? '★ Favorited' : '☆ Favorite';
    $('preview-selected').checked = state.selected.has(s.id);
    imageFor(s).then(function (img) {
      if (state.selectedId !== s.id) return;
      preview.drawAtlas($('atlas-canvas'), img);
      drawCurrentModel(img);
    }).catch(function (e) { setStatus($('export-status'), e.message, 'err'); });
  }
  function drawCurrentModel(img) {
    var found = state.selectedId && findSkin(state.selectedId);
    if (!found) return;
    preview.drawModel($('model-canvas'), img, found.skin.model, state.yaw, { label: 'Preview only — player skin unchanged' });
  }
  function selectSkin(id) {
    state.selectedId = id;
    storageSet(LS_LAST, id);
    render();
  }
  function toggleFavorite(id) {
    if (state.favorites.has(id)) state.favorites.delete(id); else state.favorites.add(id);
    saveFavorites();
    render();
  }
  function setExportSelected(id, on) {
    if (on) state.selected.add(id); else state.selected.delete(id);
    render();
  }

  async function exportSelected() {
    var chosen = allSkins().filter(function (item) { return state.selected.has(item.skin.id); });
    if (!chosen.length) { setStatus($('export-status'), 'Select at least one skin.', 'err'); return; }
    try {
      var name = $('export-name').value.trim() || 'Skin Lab Export';
      storageSet(LS_NAME, name);
      var entries = core.buildPack({ name: name, skins: chosen.map(function (item) { return { name: item.skin.name, model: item.skin.model, bytes: item.skin.bytes }; }) });
      var zip = core.makeZip(entries);
      var blob = new Blob([zip], { type: 'application/octet-stream' });
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url; a.download = core.slug(name) + '.mcpack';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
      setStatus($('export-status'), 'Exported ' + chosen.length + ' skin(s) as ' + a.download + ' (' + zip.length + ' bytes). Test import on Windows/mobile; this is not a PS4 install.', 'ok');
    } catch (e) { setStatus($('export-status'), 'Export failed: ' + e.message, 'err'); }
  }

  function clearLibrary() {
    state.packs = [];
    state.selected.clear();
    state.selectedId = null;
    releaseImages();
    render();
    setStatus($('import-status'), 'Library cleared. Favorites remain saved locally.', 'ok');
  }

  function bind() {
    $('import-pack').addEventListener('change', function (e) { importPackFiles(e.target.files); e.target.value = ''; });
    $('import-folder').addEventListener('change', function (e) { importFolder(e.target.files); e.target.value = ''; });
    $('import-png').addEventListener('change', function (e) { importPngFiles(e.target.files); e.target.value = ''; });
    $('load-demo').addEventListener('click', loadDemo);
    $('clear-library').addEventListener('click', clearLibrary);
    $('search').addEventListener('input', render);
    $('pack-filter').addEventListener('change', render);
    $('favorites-only').addEventListener('change', render);
    $('select-visible').addEventListener('click', function () { visibleSkins().forEach(function (i) { state.selected.add(i.skin.id); }); render(); });
    $('clear-selection').addEventListener('click', function () { state.selected.clear(); render(); });
    $('export-pack').addEventListener('click', exportSelected);
    $('favorite-toggle').addEventListener('click', function () { if (state.selectedId) toggleFavorite(state.selectedId); });
    $('preview-selected').addEventListener('change', function (e) { if (state.selectedId) setExportSelected(state.selectedId, e.target.checked); });
    $('yaw').addEventListener('input', function (e) {
      state.yaw = Number(e.target.value);
      var found = state.selectedId && findSkin(state.selectedId);
      if (found) imageFor(found.skin).then(drawCurrentModel).catch(function () {});
    });
    $('reset-view').addEventListener('click', function () {
      state.yaw = -25; $('yaw').value = '-25';
      var found = state.selectedId && findSkin(state.selectedId);
      if (found) imageFor(found.skin).then(drawCurrentModel).catch(function () {});
    });
    $('auto-rotate').addEventListener('change', function (e) { state.spinning = e.target.checked; });
    var canvas = $('model-canvas'), dragging = false, lastX = 0;
    canvas.addEventListener('pointerdown', function (e) { dragging = true; lastX = e.clientX; canvas.setPointerCapture(e.pointerId); });
    canvas.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      state.yaw = Math.max(-180, Math.min(180, state.yaw + (e.clientX - lastX) * 0.6));
      lastX = e.clientX; $('yaw').value = String(Math.round(state.yaw));
      var found = state.selectedId && findSkin(state.selectedId);
      if (found) imageFor(found.skin).then(drawCurrentModel).catch(function () {});
    });
    canvas.addEventListener('pointerup', function () { dragging = false; });
    canvas.addEventListener('pointercancel', function () { dragging = false; });
    var savedName = storageGet(LS_NAME); if (savedName) $('export-name').value = savedName;
    function tick() {
      if (state.spinning && state.selectedId) {
        state.yaw += 0.8; if (state.yaw > 180) state.yaw -= 360;
        $('yaw').value = String(Math.round(state.yaw));
        var found = findSkin(state.selectedId);
        if (found) imageFor(found.skin).then(drawCurrentModel).catch(function () {});
      }
      requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  loadFavorites();
  bind();
  render();
  loadDemo();
})();
