#!/usr/bin/env node
/**
 * img2png.js — Convert goldieos RGB565 .h asset files to PNG.
 *
 * Pure Node.js, no native deps. PNG encoder is a hand-rolled
 * minimal RGBA writer with zlib (Node built-in).
 *
 * Usage:
 *   node img2png.js <input.h> <output.png> <width> <height>
 *   node img2png.js --all <srcDir> <dstDir>
 */
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const HEX_RE = /0[xX][0-9A-Fa-f]{2}/g;
// Match dimensions like "_64_41" or "64x41" but require both numbers to be
// plausible image dimensions (>= 4 and <= 1024) to avoid matching prefixes
// like "rgb16_" -> (16, ?).
const SIZE_RE = /[^0-9](\d{2,4})[x_](\d{2,4})(?:[^0-9]|$)/g;

function parseBytes(text) {
  const m = text.match(HEX_RE);
  if (!m) return Buffer.alloc(0);
  return Buffer.from(m.map(s => parseInt(s, 16)));
}

function inferSize(name) {
  if (name.includes('boundary')) return [0, 0];
  // Collect all candidate pairs and pick the one with the largest area
  // (the actual image size, not a prefix token).
  const candidates = [];
  let m;
  while ((m = SIZE_RE.exec(name)) !== null) {
    const w = parseInt(m[1], 10), h = parseInt(m[2], 10);
    if (w >= 4 && w <= 1024 && h >= 4 && h <= 1024) candidates.push([w, h, w * h]);
  }
  if (candidates.length === 0) return [0, 0];
  candidates.sort((a, b) => b[2] - a[2]);
  return [candidates[0][0], candidates[0][1]];
}

function rgb565ToRgb888(lo, hi) {
  const v = lo | (hi << 8);
  const r5 = (v >> 11) & 0x1F;
  const g6 = (v >> 5) & 0x3F;
  const b5 = v & 0x1F;
  return [
    ((r5 << 3) | (r5 >> 2)) & 0xFF,
    ((g6 << 2) | (g6 >> 4)) & 0xFF,
    ((b5 << 3) | (b5 >> 2)) & 0xFF,
  ];
}

/* Minimal PNG encoder (color type 2 = RGB, 8-bit) */
function crc32(buf) {
  let c;
  const table = [];
  for (let n = 0; n < 256; n++) {
    c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    table[n] = c;
  }
  let crc = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xFF] ^ (crc >>> 8);
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const t = Buffer.from(type, 'ascii');
  const td = Buffer.concat([t, data]);
  const c = Buffer.alloc(4);
  c.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, c]);
}

function encodePng(width, height, rgb) {
  /* rgb: Buffer of length width*height*3 */
  const sig = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr.writeUInt8(8, 8);  // bit depth
  ihdr.writeUInt8(2, 9);  // color type RGB
  ihdr.writeUInt8(0, 10); // compression
  ihdr.writeUInt8(0, 11); // filter
  ihdr.writeUInt8(0, 12); // interlace
  /* Build raw scanlines with filter byte 0 */
  const stride = width * 3;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;
    rgb.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const idat = zlib.deflateSync(raw);
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

function convertOne(hPath, pngPath, w, h) {
  const text = fs.readFileSync(hPath, 'utf-8');
  const raw = parseBytes(text);
  if (raw.length < w * h * 2) {
    throw new Error(`${path.basename(hPath)}: only ${raw.length} bytes, need ${w*h*2}`);
  }
  const rgb = Buffer.alloc(w * h * 3);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 2;
      const [r, g, b] = rgb565ToRgb888(raw[i], raw[i + 1]);
      const o = (y * w + x) * 3;
      rgb[o] = r; rgb[o + 1] = g; rgb[o + 2] = b;
    }
  }
  fs.mkdirSync(path.dirname(pngPath), { recursive: true });
  fs.writeFileSync(pngPath, encodePng(w, h, rgb));
  console.log(`  ${path.basename(hPath)} -> ${path.basename(pngPath)} (${w}x${h})`);
}

function main() {
  const args = process.argv.slice(2);
  if (args[0] === '--all') {
    const src = args[1], dst = args[2];
    if (!src || !dst) { console.error('usage: --all <srcDir> <dstDir>'); process.exit(2); }
    fs.mkdirSync(dst, { recursive: true });
    let ok = 0, fail = 0, skip = 0;
    for (const fn of fs.readdirSync(src).sort()) {
      if (!fn.endsWith('.h')) continue;
      const [w, h] = inferSize(fn);
      if (!w) { console.log(`  skip (no size): ${fn}`); skip++; continue; }
      try { convertOne(path.join(src, fn), path.join(dst, path.basename(fn, '.h') + '.png'), w, h); ok++; }
      catch (e) { console.log(`  FAIL ${fn}: ${e.message}`); fail++; }
    }
    console.log(`==> ${ok} ok, ${fail} fail, ${skip} skip`);
    return;
  }
  const [inp, outp, ws, hs] = args;
  if (!inp || !outp) { console.error('usage: <in.h> <out.png> [w h]'); process.exit(2); }
  let w = parseInt(ws, 10), h = parseInt(hs, 10);
  if (!w) { [w, h] = inferSize(path.basename(inp)); }
  if (!w) { console.error('width/height required'); process.exit(2); }
  convertOne(inp, outp, w, h);
}

main();
