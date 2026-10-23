import assert from 'node:assert/strict';
import { adapterTests } from './adapters.mjs';
import { spawn } from 'node:child_process';
import { chromium } from 'playwright';

const origin = 'http://127.0.0.1:8091';
const server = spawn('python3', ['-m', 'http.server', '8091', '--bind', '127.0.0.1',
  '--directory', 'composeApp/build/dist/wasmJs/productionExecutable'], {
  cwd: new URL('..', import.meta.url), stdio: 'ignore',
});
let browser;
try {
  for (let attempt = 0; ; attempt++) {
    try { await fetch(origin); break; }
    catch (error) { if (attempt >= 30) throw error; await new Promise(r => setTimeout(r, 100)); }
  }
  browser = await chromium.launch({
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
    args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'],
  });
  const context = await browser.newContext({ permissions: ['camera'] });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(origin);
  await page.waitForFunction(() => document.documentElement.dataset.ktorProbe?.startsWith('Ktor browser GET: 200'), null, { timeout: 60000 });
  assert.equal(await page.locator('canvas').count() > 0, true);
  await page.locator('#qr-fixture').click();
  await page.waitForFunction(() => document.documentElement.dataset.kotlinBridgeEvent === 'QR worker fixture decoded: https://www.hitstergame.com/de/00001');
  await page.locator('#fake').click();
  await page.waitForFunction(() => document.documentElement.dataset.kotlinBridgeEvent?.startsWith('Fake SDK ready callback:'));
  await page.locator('#camera-start').click();
  await page.waitForFunction(() => document.documentElement.dataset.kotlinBridgeEvent === 'Camera started');
  assert.equal(await page.locator('#camera').evaluate(v => v.srcObject.getVideoTracks().some(t => t.readyState === 'live')), true);
  await page.locator('#camera-stop').click();
  await page.waitForFunction(() => document.getElementById('camera').srcObject === null);
  if (process.env.LIVE_SDK_PROBE === '1') {
    await page.locator('#sdk-load').click();
    await page.waitForFunction(() => document.documentElement.dataset.kotlinBridgeEvent === 'Spotify SDK ready callback; Player constructor: function');
  }
  await page.locator('#fake').click(); // Establish user activation for adapter lifecycle checks.
  await adapterTests(page);
  assert.deepEqual(errors, []);
  console.log('PASS: production Compose/Wasm, Ktor 200, QR worker fixture, Kotlin callback, fake camera start/release' +
    (process.env.LIVE_SDK_PROBE === '1' ? ', live SDK script readiness (no account/player)' : ''));
} finally {
  await browser?.close();
  server.kill();
}
