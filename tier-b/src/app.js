(function () {
  'use strict';
  var catalog = window.TIER_B_CATALOG;
  var renderer = window.SkinLabPreview;
  var model = document.getElementById('model');
  var atlas = document.getElementById('atlas');
  var yaw = document.getElementById('yaw');
  var grid = document.getElementById('skins');
  var selected = 0;
  var image = new Image();
  if (!catalog || catalog.skins.length !== 4 || !renderer) throw new Error('Tier B assets missing; run npm run build.');
  function draw() { renderer.drawModel(model, image, 'classic', Number(yaw.value), { label: 'BROWSER PREVIEW ONLY — PLAYER SKIN UNCHANGED' }); renderer.drawAtlas(atlas, image); }
  function select(index) {
    selected = index;
    var s = catalog.skins[index];
    document.getElementById('selected-name').textContent = s.name;
    document.getElementById('selected-id').textContent = 'variant ' + index + ' / ' + s.id + '.png';
    document.getElementById('model-caption').textContent = 'PREVIEW ONLY / ' + s.name.toUpperCase();
    Array.prototype.forEach.call(grid.children, function (button, i) { button.setAttribute('aria-pressed', String(i === index)); });
    image.onload = draw;
    image.src = 'assets/skins/' + s.id + '.png';
    if (image.complete) draw();
  }
  catalog.skins.forEach(function (s, i) {
    var button = document.createElement('button');
    button.type = 'button'; button.className = 'skin-card'; button.setAttribute('aria-pressed', 'false');
    var img = document.createElement('img'); img.src = 'assets/icons/' + s.id + '.png'; img.alt = '';
    var text = document.createElement('span'); var name = document.createElement('strong'); name.textContent = s.name;
    var meta = document.createElement('small'); meta.textContent = 'Classic · variant ' + i;
    text.appendChild(name); text.appendChild(meta); button.appendChild(img); button.appendChild(text);
    button.addEventListener('click', function () { select(i); }); grid.appendChild(button);
  });
  yaw.addEventListener('input', draw);
  document.getElementById('left').addEventListener('click', function () { yaw.value = String(Math.max(-180, Number(yaw.value) - 30)); draw(); });
  document.getElementById('right').addEventListener('click', function () { yaw.value = String(Math.min(180, Number(yaw.value) + 30)); draw(); });
  select(selected);
})();
