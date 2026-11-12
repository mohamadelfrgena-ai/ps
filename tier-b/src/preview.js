/* Tiny software humanoid preview. Preview only; not Minecraft rendering. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SkinLabPreview = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function uvRect(u, v, w, h, d) {
    return {
      top: [u + d, v, w, d],
      bottom: [u + d + w, v, w, d],
      right: [u, v + d, d, h],
      front: [u + d, v + d, w, h],
      left: [u + d + w, v + d, d, h],
      back: [u + d + w + d, v + d, w, h]
    };
  }
  function part(name, center, size, uv, inflate) {
    return { name: name, center: center, size: size, uv: uv, inflate: inflate || 0 };
  }
  function parts(model) {
    var slim = model === 'slim';
    var aw = slim ? 3 : 4;
    var ax = slim ? 5.5 : 6;
    return [
      part('head', [0, 28, 0], [8, 8, 8], uvRect(0, 0, 8, 8, 8)),
      part('hat', [0, 28, 0], [8, 8, 8], uvRect(32, 0, 8, 8, 8), 0.5),
      part('body', [0, 18, 0], [8, 12, 4], uvRect(16, 16, 8, 12, 4)),
      part('jacket', [0, 18, 0], [8, 12, 4], uvRect(16, 32, 8, 12, 4), 0.25),
      part('rightArm', [-ax, 18, 0], [aw, 12, 4], uvRect(40, 16, aw, 12, 4)),
      part('rightSleeve', [-ax, 18, 0], [aw, 12, 4], uvRect(40, 32, aw, 12, 4), 0.25),
      part('leftArm', [ax, 18, 0], [aw, 12, 4], uvRect(32, 48, aw, 12, 4)),
      part('leftSleeve', [ax, 18, 0], [aw, 12, 4], uvRect(48, 48, aw, 12, 4), 0.25),
      part('rightLeg', [-2, 6, 0], [4, 12, 4], uvRect(0, 16, 4, 12, 4)),
      part('rightPants', [-2, 6, 0], [4, 12, 4], uvRect(0, 32, 4, 12, 4), 0.25),
      part('leftLeg', [2, 6, 0], [4, 12, 4], uvRect(16, 48, 4, 12, 4)),
      part('leftPants', [2, 6, 0], [4, 12, 4], uvRect(0, 48, 4, 12, 4), 0.25)
    ];
  }
  function rotate(p, yaw, pitch) {
    var cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
    var x = p[0] * cy + p[2] * sy;
    var z = -p[0] * sy + p[2] * cy;
    var y = p[1] * cp - z * sp;
    z = p[1] * sp + z * cp;
    return [x, y, z];
  }
  function facesFor(part) {
    var c = part.center, s = part.size, inf = part.inflate;
    var hx = s[0] / 2 + inf, hy = s[1] / 2 + inf, hz = s[2] / 2 + inf;
    var x0 = c[0] - hx, x1 = c[0] + hx, y0 = c[1] - hy, y1 = c[1] + hy, z0 = c[2] - hz, z1 = c[2] + hz;
    // corners: TL, TR, BL, BR; normal points outward
    return [
      { key: 'front', n: [0, 0, 1], p: [[x0, y1, z1], [x1, y1, z1], [x0, y0, z1], [x1, y0, z1]] },
      { key: 'back', n: [0, 0, -1], p: [[x1, y1, z0], [x0, y1, z0], [x1, y0, z0], [x0, y0, z0]] },
      { key: 'right', n: [1, 0, 0], p: [[x1, y1, z1], [x1, y1, z0], [x1, y0, z1], [x1, y0, z0]] },
      { key: 'left', n: [-1, 0, 0], p: [[x0, y1, z0], [x0, y1, z1], [x0, y0, z0], [x0, y0, z1]] },
      { key: 'top', n: [0, 1, 0], p: [[x0, y1, z0], [x1, y1, z0], [x0, y1, z1], [x1, y1, z1]] },
      { key: 'bottom', n: [0, -1, 0], p: [[x0, y0, z1], [x1, y0, z1], [x0, y0, z0], [x1, y0, z0]] }
    ].map(function (f) { f.uv = part.uv[f.key]; f.part = part.name; return f; });
  }
  function drawModel(canvas, image, model, yawDeg, options) {
    options = options || {};
    var ctx = canvas.getContext('2d');
    var W = canvas.width, H = canvas.height;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = options.background || '#0d1117';
    ctx.fillRect(0, 0, W, H);
    if (!image) return;
    var yaw = (yawDeg || 0) * Math.PI / 180;
    var pitch = (options.pitchDeg == null ? -12 : options.pitchDeg) * Math.PI / 180;
    var scale = options.scale || Math.min(W / 24, H / 40);
    var cx = W / 2, cy = H / 2 + 14 * scale;
    var faces = [];
    parts(model).forEach(function (p) {
      facesFor(p).forEach(function (f) {
        var n = rotate(f.n, yaw, pitch);
        if (n[2] <= 0.0001) return; // back-face cull
        var pts = f.p.map(function (p3) { return rotate(p3, yaw, pitch); });
        var depth = pts.reduce(function (s, p) { return s + p[2]; }, 0) / 4;
        var screen = pts.map(function (p) { return [cx + p[0] * scale, cy - p[1] * scale]; });
        faces.push({ depth: depth, screen: screen, uv: f.uv, part: f.part });
      });
    });
    faces.sort(function (a, b) { return a.depth - b.depth; });
    ctx.imageSmoothingEnabled = false;
    faces.forEach(function (f) {
      var p0 = f.screen[0], p1 = f.screen[1], p2 = f.screen[2];
      var u = f.uv[0], v = f.uv[1], w = f.uv[2], h = f.uv[3];
      if (w <= 0 || h <= 0) return;
      ctx.save();
      ctx.setTransform((p1[0] - p0[0]) / w, (p1[1] - p0[1]) / w, (p2[0] - p0[0]) / h, (p2[1] - p0[1]) / h, p0[0], p0[1]);
      ctx.drawImage(image, u, v, w, h, 0, 0, w, h);
      ctx.restore();
    });
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    if (options.label) {
      ctx.fillStyle = '#9fb0c0'; ctx.font = '12px system-ui, sans-serif';
      ctx.fillText(options.label, 10, H - 10);
    }
  }
  function drawAtlas(canvas, image) {
    var ctx = canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#0d1117'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    if (!image) return;
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(image, 0, 0, 64, 64, 0, 0, canvas.width, canvas.height);
  }
  return { drawModel: drawModel, drawAtlas: drawAtlas, parts: parts };
});
