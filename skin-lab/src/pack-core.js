/* Skin Lab pack core. Dependency-free UMD: browser global + Node CommonJS.
 * Preview/export tooling only. This is not a Minecraft script and does not equip skins. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SkinLabCore = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  var LIMITS = {
    MAX_ARCHIVE_BYTES: 32 * 1024 * 1024,
    MAX_ENTRIES: 512,
    MAX_FILE_BYTES: 8 * 1024 * 1024,
    MAX_TOTAL_UNCOMPRESSED: 64 * 1024 * 1024,
    MAX_SKINS: 256,
    MAX_PATH: 180
  };
  var GEOMETRY = {
    classic: 'geometry.humanoid.custom',
    slim: 'geometry.humanoid.customSlim'
  };
  var enc = new TextEncoder();
  var dec = new TextDecoder('utf-8');

  function fail(message) { throw new Error(message); }
  function isObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
  function asBytes(v) {
    if (typeof v === 'string') return enc.encode(v);
    if (v instanceof Uint8Array) return v;
    if (v instanceof ArrayBuffer) return new Uint8Array(v);
    if (ArrayBuffer.isView(v)) return new Uint8Array(v.buffer, v.byteOffset, v.byteLength);
    fail('Expected string or byte array');
  }
  function concat(chunks, total) {
    var out = new Uint8Array(total), off = 0;
    for (var i = 0; i < chunks.length; i++) { out.set(chunks[i], off); off += chunks[i].length; }
    return out;
  }

  var CRC_TABLE = (function () {
    var t = new Uint32Array(256);
    for (var n = 0; n < 256; n++) {
      var c = n;
      for (var k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      t[n] = c >>> 0;
    }
    return t;
  })();
  function crc32(bytes) {
    var c = 0xFFFFFFFF;
    for (var i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
  }
  function hashString(s) {
    var h = 0x811C9DC5;
    for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193); }
    return (h >>> 0).toString(16).padStart(8, '0');
  }
  function uuid4() {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
    var b = new Uint8Array(16);
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) crypto.getRandomValues(b);
    else for (var i = 0; i < 16; i++) b[i] = Math.floor(Math.random() * 256);
    b[6] = (b[6] & 0x0F) | 0x40; b[8] = (b[8] & 0x3F) | 0x80;
    var h = Array.from(b, function (x) { return x.toString(16).padStart(2, '0'); }).join('');
    return h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' + h.slice(16, 20) + '-' + h.slice(20);
  }
  function isUuid(s) {
    return typeof s === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(s);
  }
  function slug(s) {
    return String(s || '').toLowerCase().replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 40) || 'skin_lab';
  }
  function safePath(p) {
    if (typeof p !== 'string' || !p || p.length > LIMITS.MAX_PATH) return false;
    if (p.indexOf('\\') !== -1 || p.indexOf(':') !== -1 || p.charCodeAt(0) === 47) return false;
    if (/[\u0000-\u001f\u007f]/.test(p)) return false;
    var parts = p.split('/');
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (!part || part === '.' || part === '..' || part.length > 80) return false;
    }
    return true;
  }
  function assertSafePath(p) { if (!safePath(p)) fail('Unsafe archive path: ' + JSON.stringify(p)); }
  function isVersion(v) {
    return Array.isArray(v) && v.length === 3 && v.every(function (n) { return Number.isInteger(n) && n >= 0 && n <= 9999; });
  }
  function jsonBytes(v) { return enc.encode(JSON.stringify(v, null, 2) + '\n'); }
  function parseJson(bytes, label) {
    try { return JSON.parse(dec.decode(bytes)); }
    catch (e) { fail('Invalid JSON in ' + label + ': ' + e.message); }
  }
  function langValue(s) { return String(s).replace(/[\r\n]+/g, ' ').trim().slice(0, 120); }

  /* Stored ZIP writer. Small packs do not need compression; stored entries are easy to audit. */
  function makeZip(entries) {
    if (!Array.isArray(entries) || entries.length === 0) fail('ZIP requires at least one entry');
    if (entries.length > LIMITS.MAX_ENTRIES) fail('Too many ZIP entries');
    var seen = Object.create(null), locals = [], centrals = [], offset = 0, total = 0;
    var dosDate = ((2026 - 1980) << 9) | (11 << 5) | 12;
    var dosTime = (12 << 11);
    for (var i = 0; i < entries.length; i++) {
      var e = entries[i], p = e.path;
      assertSafePath(p);
      var key = p.toLowerCase();
      if (seen[key]) fail('Duplicate ZIP path: ' + p);
      seen[key] = true;
      var data = asBytes(e.data);
      if (data.length > LIMITS.MAX_FILE_BYTES) fail('ZIP entry too large: ' + p);
      total += data.length;
      if (total > LIMITS.MAX_TOTAL_UNCOMPRESSED) fail('ZIP uncompressed total too large');
      var name = enc.encode(p), crc = crc32(data);
      var lh = new Uint8Array(30 + name.length), lv = new DataView(lh.buffer);
      lv.setUint32(0, 0x04034b50, true); lv.setUint16(4, 20, true); lv.setUint16(6, 0x0800, true);
      lv.setUint16(8, 0, true); lv.setUint16(10, dosTime, true); lv.setUint16(12, dosDate, true);
      lv.setUint32(14, crc, true); lv.setUint32(18, data.length, true); lv.setUint32(22, data.length, true);
      lv.setUint16(26, name.length, true); lv.setUint16(28, 0, true); lh.set(name, 30);
      locals.push(lh, data);
      var ch = new Uint8Array(46 + name.length), cv = new DataView(ch.buffer);
      cv.setUint32(0, 0x02014b50, true); cv.setUint16(4, 20, true); cv.setUint16(6, 20, true);
      cv.setUint16(8, 0x0800, true); cv.setUint16(10, 0, true); cv.setUint16(12, dosTime, true); cv.setUint16(14, dosDate, true);
      cv.setUint32(16, crc, true); cv.setUint32(20, data.length, true); cv.setUint32(24, data.length, true);
      cv.setUint16(28, name.length, true); cv.setUint16(30, 0, true); cv.setUint16(32, 0, true);
      cv.setUint16(34, 0, true); cv.setUint16(36, 0, true); cv.setUint32(38, 0, true); cv.setUint32(42, offset, true);
      ch.set(name, 46); centrals.push(ch);
      offset += lh.length + data.length;
    }
    var centralSize = centrals.reduce(function (n, b) { return n + b.length; }, 0);
    var eocd = new Uint8Array(22), ev = new DataView(eocd.buffer);
    ev.setUint32(0, 0x06054b50, true); ev.setUint16(4, 0, true); ev.setUint16(6, 0, true);
    ev.setUint16(8, entries.length, true); ev.setUint16(10, entries.length, true);
    ev.setUint32(12, centralSize, true); ev.setUint32(16, offset, true); ev.setUint16(20, 0, true);
    var out = concat(locals.concat(centrals, [eocd]), offset + centralSize + 22);
    if (out.length > LIMITS.MAX_ARCHIVE_BYTES) fail('Generated archive too large');
    return out;
  }

  async function inflateRaw(data, expectedSize) {
    if (typeof DecompressionStream === 'undefined') fail('This browser cannot read compressed ZIP entries; use a current browser or a stored ZIP');
    var ds = new DecompressionStream('deflate-raw');
    var writer = ds.writable.getWriter();
    writer.write(data).catch(function () {});
    writer.close().catch(function () {});
    var reader = ds.readable.getReader(), chunks = [], total = 0;
    while (true) {
      var r = await reader.read();
      if (r.done) break;
      total += r.value.length;
      if (total > expectedSize || total > LIMITS.MAX_FILE_BYTES) { reader.cancel().catch(function () {}); fail('Decompressed ZIP entry exceeds its declared size'); }
      chunks.push(r.value);
    }
    if (total !== expectedSize) fail('Decompressed ZIP size mismatch');
    return concat(chunks, total);
  }

  async function readZip(buffer) {
    var bytes = asBytes(buffer);
    if (bytes.length < 22 || bytes.length > LIMITS.MAX_ARCHIVE_BYTES) fail('ZIP size outside allowed range');
    var view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    var eocd = -1, min = Math.max(0, bytes.length - 22 - 65535);
    for (var i = bytes.length - 22; i >= min; i--) {
      if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) fail('ZIP end-of-central-directory not found');
    var commentLen = view.getUint16(eocd + 20, true);
    if (eocd + 22 + commentLen !== bytes.length) fail('ZIP comment length mismatch');
    if (view.getUint16(eocd + 4, true) !== 0 || view.getUint16(eocd + 6, true) !== 0) fail('Multi-disk ZIP is not supported');
    var count = view.getUint16(eocd + 10, true);
    if (count !== view.getUint16(eocd + 8, true) || count > LIMITS.MAX_ENTRIES) fail('ZIP entry count mismatch or too large');
    var centralSize = view.getUint32(eocd + 12, true), centralOffset = view.getUint32(eocd + 16, true);
    if (centralOffset === 0xFFFFFFFF || centralSize === 0xFFFFFFFF) fail('ZIP64 is not supported');
    if (centralOffset + centralSize > eocd) fail('ZIP central directory out of bounds');
    var pos = centralOffset, files = new Map(), seen = Object.create(null), total = 0;
    for (var n = 0; n < count; n++) {
      if (pos + 46 > bytes.length || view.getUint32(pos, true) !== 0x02014b50) fail('Invalid ZIP central directory header');
      var flags = view.getUint16(pos + 8, true), method = view.getUint16(pos + 10, true);
      var crc = view.getUint32(pos + 16, true), csize = view.getUint32(pos + 20, true), usize = view.getUint32(pos + 24, true);
      var nameLen = view.getUint16(pos + 28, true), extraLen = view.getUint16(pos + 30, true), comment = view.getUint16(pos + 32, true);
      var extAttr = view.getUint32(pos + 38, true), localOffset = view.getUint32(pos + 42, true);
      if (pos + 46 + nameLen + extraLen + comment > bytes.length) fail('ZIP central directory entry out of bounds');
      var name = dec.decode(bytes.subarray(pos + 46, pos + 46 + nameLen));
      pos += 46 + nameLen + extraLen + comment;
      if (flags & 0x0041) fail('Encrypted ZIP entries are not supported');
      if (csize === 0xFFFFFFFF || usize === 0xFFFFFFFF || localOffset === 0xFFFFFFFF) fail('ZIP64 is not supported');
      var unixMode = extAttr >>> 16;
      if ((unixMode & 0o170000) === 0o120000) fail('Symlink ZIP entries are not supported: ' + name);
      var isDir = /\/$/.test(name) || !!(extAttr & 0x10);
      if (isDir) {
        if (usize !== 0) fail('Directory entry has data: ' + name);
        assertSafePath(name.replace(/\/$/, ''));
        continue;
      }
      assertSafePath(name);
      var lower = name.toLowerCase();
      if (seen[lower]) fail('Duplicate ZIP path: ' + name);
      seen[lower] = true;
      if (usize > LIMITS.MAX_FILE_BYTES || csize > LIMITS.MAX_ARCHIVE_BYTES) fail('ZIP entry too large: ' + name);
      total += usize;
      if (total > LIMITS.MAX_TOTAL_UNCOMPRESSED) fail('ZIP uncompressed total too large');
      if (localOffset + 30 > bytes.length || view.getUint32(localOffset, true) !== 0x04034b50) fail('Invalid ZIP local header: ' + name);
      var ln = view.getUint16(localOffset + 26, true), le = view.getUint16(localOffset + 28, true);
      var dataStart = localOffset + 30 + ln + le;
      if (dataStart + csize > bytes.length) fail('ZIP entry data out of bounds: ' + name);
      var localName = dec.decode(bytes.subarray(localOffset + 30, localOffset + 30 + ln));
      if (localName !== name) fail('ZIP local/central name mismatch');
      var raw = bytes.subarray(dataStart, dataStart + csize), data;
      if (method === 0) {
        if (csize !== usize) fail('Stored ZIP size mismatch: ' + name);
        data = raw;
      } else if (method === 8) {
        data = await inflateRaw(raw, usize);
      } else fail('Unsupported ZIP compression method ' + method + ' for ' + name);
      if (crc32(data) !== crc) fail('ZIP CRC mismatch: ' + name);
      files.set(name, data);
    }
    if (pos !== centralOffset + centralSize) fail('ZIP central directory size mismatch');
    return files;
  }

  function inspectPng(buffer) {
    var b = asBytes(buffer);
    if (b.length < 33) fail('PNG too short');
    var sig = [137, 80, 78, 71, 13, 10, 26, 10];
    for (var i = 0; i < 8; i++) if (b[i] !== sig[i]) fail('Invalid PNG signature');
    var v = new DataView(b.buffer, b.byteOffset, b.byteLength), pos = 8;
    var width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
    var sawIhdr = false, sawIdat = false, sawIend = false, chunks = 0;
    while (pos < b.length) {
      if (pos + 12 > b.length) fail('Truncated PNG chunk');
      if (++chunks > 1024) fail('Too many PNG chunks');
      var len = v.getUint32(pos, false); // PNG chunk lengths are big-endian
      var type = String.fromCharCode(b[pos + 4], b[pos + 5], b[pos + 6], b[pos + 7]);
      if (pos + 12 + len > b.length) fail('PNG chunk out of bounds: ' + type);
      var expected = v.getUint32(pos + 8 + len, false);
      if (crc32(b.subarray(pos + 4, pos + 8 + len)) !== expected) fail('PNG CRC mismatch: ' + type);
      if (type === 'IHDR') {
        if (sawIhdr || len !== 13 || pos !== 8) fail('Invalid PNG IHDR');
        sawIhdr = true;
        width = v.getUint32(pos + 8, false); height = v.getUint32(pos + 12, false);
        bitDepth = b[pos + 16]; colorType = b[pos + 17]; interlace = b[pos + 19];
        if (!width || !height || width > 4096 || height > 4096) fail('PNG dimensions out of range');
        var depths = { 0: [1, 2, 4, 8, 16], 2: [8, 16], 3: [1, 2, 4, 8], 4: [8, 16], 6: [8, 16] };
        if (!depths[colorType] || depths[colorType].indexOf(bitDepth) === -1 || interlace > 1) fail('Unsupported PNG format');
      } else if (type === 'IDAT') sawIdat = true;
      else if (type === 'IEND') {
        if (len !== 0 || pos + 12 !== b.length) fail('Invalid PNG IEND');
        sawIend = true;
      } else if (type === 'acTL') fail('Animated PNG is not a skin texture');
      else if (/^[A-Z]/.test(type) && ['PLTE', 'tRNS', 'sBIT', 'sRGB', 'gAMA', 'cHRM', 'iCCP', 'sTER', 'tEXt', 'zTXt', 'iTXt', 'bKGD', 'pHYs', 'tIME', 'hIST', 'sPLT'].indexOf(type) === -1) {
        fail('Unsupported critical PNG chunk: ' + type);
      }
      pos += 12 + len;
      if (sawIend) break;
    }
    if (!sawIhdr || !sawIdat || !sawIend || pos !== b.length) fail('PNG missing IHDR/IDAT/IEND');
    return { width: width, height: height, bitDepth: bitDepth, colorType: colorType, interlace: interlace };
  }
  function assertSkinPng(bytes) {
    var info = inspectPng(bytes);
    if (info.width !== 64 || info.height !== 64) fail('Skin texture must be 64x64; got ' + info.width + 'x' + info.height);
    return info;
  }

  function fileMap(files) {
    if (files instanceof Map) return files;
    if (isObject(files)) {
      var m = new Map();
      Object.keys(files).forEach(function (k) { assertSafePath(k); m.set(k, asBytes(files[k])); });
      return m;
    }
    fail('Expected file map');
  }
  function parseLang(bytes) {
    var out = Object.create(null);
    dec.decode(bytes).split(/\r?\n/).forEach(function (line) {
      line = line.trim();
      if (!line || line[0] === '#') return;
      var eq = line.indexOf('=');
      if (eq > 0) out[line.slice(0, eq).trim()] = line.slice(eq + 1).trim();
    });
    return out;
  }

  function validatePack(input) {
    var files = fileMap(input), warnings = [];
    files.forEach(function (_v, k) { assertSafePath(k); });
    if (!files.has('manifest.json')) fail('Missing root manifest.json');
    if (!files.has('skins.json')) fail('Missing root skins.json');
    var manifest = parseJson(files.get('manifest.json'), 'manifest.json');
    var skinsJson = parseJson(files.get('skins.json'), 'skins.json');
    if (!isObject(manifest) || (manifest.format_version !== 1 && manifest.format_version !== 2)) fail('manifest.format_version must be 1 or 2');
    if (!isObject(manifest.header) || typeof manifest.header.name !== 'string' || !manifest.header.name.trim()) fail('manifest.header.name is required');
    if (!isUuid(manifest.header.uuid)) fail('manifest.header.uuid must be a UUID');
    if (!isVersion(manifest.header.version)) fail('manifest.header.version must be [major, minor, patch]');
    if (!Array.isArray(manifest.modules) || manifest.modules.length !== 1) fail('Skin pack must have exactly one module');
    var mod = manifest.modules[0];
    if (!isObject(mod) || mod.type !== 'skin_pack') fail('Module type must be skin_pack');
    if (!isUuid(mod.uuid) || mod.uuid.toLowerCase() === manifest.header.uuid.toLowerCase()) fail('Module UUID must be a distinct UUID');
    if (!isVersion(mod.version)) fail('Module version must be [major, minor, patch]');
    if (!isObject(skinsJson) || typeof skinsJson.serialize_name !== 'string' || !skinsJson.serialize_name.trim()) fail('skins.json serialize_name is required');
    if (typeof skinsJson.localization_name !== 'string' || !skinsJson.localization_name.trim()) fail('skins.json localization_name is required');
    if (!Array.isArray(skinsJson.skins) || skinsJson.skins.length < 1 || skinsJson.skins.length > LIMITS.MAX_SKINS) fail('skins.json must contain 1..' + LIMITS.MAX_SKINS + ' skins');
    if (files.has('geometry.json')) warnings.push('geometry.json is present but skin-pack JSON does not support custom player models; it is ignored.');
    var lang = Object.create(null), langPath = '';
    if (files.has('texts/en_US.lang')) langPath = 'texts/en_US.lang';
    else {
      files.forEach(function (_v, k) { if (!langPath && /^texts\/[A-Za-z_]+\.lang$/.test(k)) langPath = k; });
    }
    if (langPath) lang = parseLang(files.get(langPath));
    if (!files.has('texts/languages.json')) warnings.push('texts/languages.json is missing; the prototype exporter includes it.');
    else {
      var langs = parseJson(files.get('texts/languages.json'), 'texts/languages.json');
      if (!Array.isArray(langs) || !langs.length || !langs.every(function (x) { return typeof x === 'string'; })) fail('texts/languages.json must be a non-empty string array');
    }
    var packKey = 'skinpack.' + skinsJson.localization_name;
    var packName = lang[packKey] || (manifest.header.name !== 'pack.name' ? manifest.header.name : skinsJson.serialize_name);
    if (!lang[packKey]) warnings.push('Missing pack localization key ' + packKey + '; using a fallback name.');
    var skins = [], seenTextures = Object.create(null);
    for (var i = 0; i < skinsJson.skins.length; i++) {
      var s = skinsJson.skins[i];
      if (!isObject(s)) fail('Skin entry ' + i + ' must be an object');
      if (typeof s.localization_name !== 'string' || !s.localization_name.trim()) fail('Skin entry ' + i + ' needs localization_name');
      var model = s.geometry === GEOMETRY.classic ? 'classic' : s.geometry === GEOMETRY.slim ? 'slim' : '';
      if (!model) fail('Unsupported skin geometry: ' + String(s.geometry));
      if (typeof s.texture !== 'string' || s.texture.indexOf('/') !== -1 || !safePath(s.texture)) fail('Skin texture must be a safe root-level filename');
      if (!/\.png$/i.test(s.texture)) fail('Skin texture must be a PNG');
      if (s.type !== 'free' && s.type !== 'paid') fail('Skin type must be free or paid');
      if (!files.has(s.texture)) fail('Missing skin texture: ' + s.texture);
      var bytes = asBytes(files.get(s.texture));
      var info = assertSkinPng(bytes);
      var texKey = s.texture.toLowerCase();
      if (seenTextures[texKey]) warnings.push('Texture reused by multiple entries: ' + s.texture);
      seenTextures[texKey] = true;
      if (s.cape) warnings.push('Cape field ignored for ' + s.localization_name + '; this prototype exports player skin textures only.');
      if (s.animations) warnings.push('Animations field ignored for ' + s.localization_name + '.');
      var skinKey = 'skin.' + skinsJson.localization_name + '.' + s.localization_name;
      var fallbackKey = 'skin.' + skinsJson.localization_name + '.' + s.texture.replace(/\.png$/i, '');
      var name = lang[skinKey] || lang[fallbackKey] || s.localization_name;
      if (!lang[skinKey]) warnings.push('Missing skin localization key ' + skinKey + '; using a fallback name.');
      skins.push({
        id: hashString(manifest.header.uuid.toLowerCase() + ':' + s.texture + ':' + s.localization_name),
        name: name, model: model, geometry: s.geometry, texturePath: s.texture,
        bytes: bytes, width: info.width, height: info.height, type: s.type
      });
    }
    return {
      id: manifest.header.uuid.toLowerCase(), name: packName, manifest: manifest, skinsJson: skinsJson,
      skins: skins, warnings: warnings
    };
  }

  function buildPack(options) {
    options = options || {};
    var name = langValue(options.name || 'Skin Lab Export') || 'Skin Lab Export';
    var input = options.skins;
    if (!Array.isArray(input) || input.length < 1 || input.length > LIMITS.MAX_SKINS) fail('Select 1..' + LIMITS.MAX_SKINS + ' skins to export');
    var packLoc = slug(name), headerUuid = uuid4(), moduleUuid = uuid4();
    var manifest = {
      format_version: 2,
      header: { name: 'pack.name', description: 'Exported by Skin Lab. Preview/export only; platform import rules apply.', uuid: headerUuid, version: [1, 0, 0] },
      modules: [{ type: 'skin_pack', uuid: moduleUuid, version: [1, 0, 0] }]
    };
    var skins = [], lang = ['skinpack.' + packLoc + '=' + name], seen = Object.create(null);
    for (var i = 0; i < input.length; i++) {
      var s = input[i];
      if (!isObject(s)) fail('Export skin ' + i + ' must be an object');
      if (s.model !== 'classic' && s.model !== 'slim') fail('Export model must be classic or slim');
      var bytes = asBytes(s.bytes);
      assertSkinPng(bytes);
      var skinName = langValue(s.name || 'Skin ' + (i + 1)) || 'Skin ' + (i + 1);
      var loc = 'Skin' + String(i + 1).padStart(3, '0'), texture = 'skin_' + String(i + 1).padStart(3, '0') + '.png';
      if (seen[texture.toLowerCase()]) fail('Duplicate generated texture path');
      seen[texture.toLowerCase()] = true;
      skins.push({ localization_name: loc, geometry: GEOMETRY[s.model], texture: texture, type: 'free' });
      lang.push('skin.' + packLoc + '.' + loc + '=' + skinName);
      // Entries are added below after metadata so the texture filename is explicit.
      skins[skins.length - 1]._bytes = bytes;
    }
    var entries = [
      { path: 'manifest.json', data: jsonBytes(manifest) },
      { path: 'skins.json', data: jsonBytes({ serialize_name: packLoc, localization_name: packLoc, skins: skins.map(function (s) { return { localization_name: s.localization_name, geometry: s.geometry, texture: s.texture, type: s.type }; }) }) },
      { path: 'texts/languages.json', data: jsonBytes(['en_US']) },
      { path: 'texts/en_US.lang', data: enc.encode(lang.join('\n') + '\n') }
    ];
    skins.forEach(function (s) { entries.push({ path: s.texture, data: s._bytes }); });
    var map = new Map(entries.map(function (e) { return [e.path, asBytes(e.data)]; }));
    var validated = validatePack(map);
    if (validated.warnings.length) fail('Generated pack unexpectedly produced warnings: ' + validated.warnings.join(' '));
    return entries;
  }

  return {
    LIMITS: LIMITS, GEOMETRY: GEOMETRY, crc32: crc32, hashString: hashString, uuid4: uuid4, isUuid: isUuid,
    slug: slug, safePath: safePath, makeZip: makeZip, readZip: readZip, inspectPng: inspectPng,
    assertSkinPng: assertSkinPng, validatePack: validatePack, buildPack: buildPack
  };
});
