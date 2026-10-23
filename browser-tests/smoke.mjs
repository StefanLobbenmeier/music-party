import assert from 'node:assert/strict';
import { adapterTests } from './adapters.mjs';
import { spawn } from 'node:child_process';
import { chromium } from 'playwright';

const origin = 'http://127.0.0.1:8092';
const server = spawn('python3', ['browser-tests/server.py'], { cwd: new URL('..', import.meta.url), stdio: 'ignore' });
let browser, page;
// Compose's transparent accessibility nodes describe canvas coordinates. Allow a rendered
// frame after edits/recomposition, then deliver the real pointer event to the canvas.
async function canvasVisible(page, locator) {
  for (let i = 0; i < 12; i++) {
    const box = await locator.boundingBox();
    if (box && box.height >= 8 && box.y >= 0 && box.y + box.height <= page.viewportSize().height) return;
    await page.mouse.move(page.viewportSize().width / 2, 80);
    await page.mouse.wheel(0, box?.height >= 8 ? (box.y < 0 ? -240 : 240) : (i < 6 ? 240 : -360));
    await page.waitForTimeout(120);
  }
  throw Error('Compose control could not be scrolled into view: ' + JSON.stringify(await locator.boundingBox()));
}
const click = async (page, name) => {
  await page.waitForTimeout(150);
  const locator = page.getByRole('button', { name, exact: true });
  await canvasVisible(page, locator);
  const box = await locator.boundingBox();
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
};
async function enter(page, value, index = 0) {
  const locator = page.getByRole('textbox').nth(index);
  await canvasVisible(page, locator);
  const box = await locator.boundingBox();
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A');
  await page.keyboard.insertText(value);
}
async function contains(page, text) {
  await page.waitForFunction(text => document.getElementById('compose-root')?.shadowRoot?.textContent.includes(text), text);
  console.log('Verified:', text);
}
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
  page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(origin);
  await contains(page, 'Set up Spotify');
  assert.ok(await page.locator('canvas').count());
  await click(page, 'Save setup');
  await contains(page, '32-character public client ID');
  await click(page, 'Try fake providers (no Spotify)');
  await contains(page, 'Scan a card');
  await enter(page, 'https://evil.example/de/1');
  await click(page, 'Resolve and play');
  await contains(page, 'Enter a valid www.hitstergame.com');
  await enter(page, 'https://www.hitstergame.com/de/00001');
  await click(page, 'Resolve and play');
  await contains(page, 'Playing on Spotify');
  await click(page, 'Pause');
  await page.getByRole('button', {name: 'Resume', exact:true}).waitFor();
  await enter(page, '12');
  await click(page, 'Seek');
  await contains(page, 'Position: 12 seconds');
  await click(page, 'Resume');
  await page.getByRole('button', {name: 'Pause', exact:true}).waitFor();
  await click(page, 'Next scan');
  await click(page, 'Settings');
  await contains(page, 'Playlist configuration');
  await enter(page, '[{"editionKey":"de","playlist":"bad","label":"test"}]');
  await click(page, 'Save playlists');
  await contains(page, 'Enter a Spotify playlist');
  await click(page, 'Reset to reference defaults');
  await click(page, 'Save playlists');
  await contains(page, 'Scan a card');
  // Real production QR library and worker decode a fixture through the real camera adapter.
  await page.evaluate(async () => {
    const image = new Image(); image.src = '/qr-fixture.png'; await image.decode();
    const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480;
    const ctx = canvas.getContext('2d');
    const paint = () => { ctx.fillStyle = 'white'; ctx.fillRect(0,0,640,480); ctx.drawImage(image,170,90,300,300); };
    paint(); window.fixtureTimer = setInterval(paint, 50);
    window.fixtureStream = canvas.captureStream(20);
    navigator.mediaDevices.getUserMedia = async () => window.fixtureStream;
  });
  await click(page, 'Start camera');
  await contains(page, 'Playing on Spotify');
  await page.waitForFunction(() => document.getElementById('camera').srcObject === null);
  assert.ok(await page.evaluate(() => window.fixtureStream.getTracks().every(t => t.readyState === 'ended')));
  await page.evaluate(() => clearInterval(window.fixtureTimer));
  // Responsive Compose canvas and controls at a phone viewport.
  await page.setViewportSize({ width:390, height:844 });
  await click(page, 'Next scan');
  await contains(page, 'Scan a card');
  await enter(page, 'www.hitstergame.com/de/2');
  await click(page, 'Resolve and play');
  await contains(page, 'Playing on Spotify');
  await page.screenshot({ path: '/tmp/music-party-mobile.png' });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390);
  await click(page, 'Log out');
  await contains(page, 'Sign in to Spotify');
  // Direct callback request uses production rewrite; invalid/replayed callback stays recoverable.
  await page.evaluate(origin => localStorage.setItem('music-party.setup', JSON.stringify({clientId:'a'.repeat(32), redirectUri:origin+'/oauth/spotify/callback'})), origin);
  await page.goto(origin + '/oauth/spotify/callback?code=untrusted&state=wrong');
  await contains(page, 'Invalid or expired callback');
  assert.equal(new URL(page.url()).search, '');
  await page.reload();
  await contains(page, 'Sign in to Spotify');
  // Exercise the real Wasm composition root, BrowserSession, Ktor and domain resolver
  // with fake HTTP providers and a deliberately unavailable local SDK.
  const commands = [];
  const remote = { id:'remote', name:'Mock remote device', is_active:true, is_restricted:false };
  const track = { id:'remoteTrack', uri:'spotify:track:remoteTrack', type:'track', external_urls:{spotify:'https://open.spotify.com/track/remoteTrack'} };
  let playing = false, position = 0;
  await page.route('https://sdk.scdn.co/**', route => route.abort());
  await page.route('https://api.spotify.com/v1/**', async route => {
    const request = route.request(), url = new URL(request.url());
    const headers = { 'access-control-allow-origin':'*', 'access-control-allow-headers':'*', 'access-control-allow-methods':'GET,PUT,OPTIONS' };
    if (request.method() === 'OPTIONS') return route.fulfill({status:204, headers});
    let body;
    if (url.pathname.endsWith('/devices')) body = {devices:[remote]};
    else if (url.pathname.endsWith('/items')) {
      assert.equal(url.searchParams.get('offset'), '0');
      body = {items:[{item:track}], offset:0, total:1};
    } else if (request.method() === 'GET') body = {device:remote, is_playing:playing, progress_ms:position, item:track};
    else {
      assert.equal(url.searchParams.get('device_id'), 'remote');
      commands.push(url.pathname.split('/').at(-1));
      if (url.pathname.endsWith('/play')) playing = true;
      if (url.pathname.endsWith('/pause')) playing = false;
      if (url.pathname.endsWith('/seek')) position = Number(url.searchParams.get('position_ms'));
      return route.fulfill({status:204, headers});
    }
    return route.fulfill({status:200, contentType:'application/json', headers, body:JSON.stringify(body)});
  });
  await page.evaluate(() => sessionStorage.setItem('music-party.oauth.tokens', JSON.stringify({access:'fake-test-token',refresh:null,expires:Date.now()+3600000})));
  await page.goto(origin);
  await contains(page, 'Browser player unavailable');
  await contains(page, 'Mock remote device');
  await enter(page, 'www.hitstergame.com/de/1');
  await click(page, 'Resolve and play');
  await contains(page, 'Playing on Spotify');
  await click(page, 'Pause');
  await enter(page, '8');
  await click(page, 'Seek');
  await contains(page, 'Position: 8 seconds');
  await click(page, 'Resume');
  await click(page, 'Log out');
  await contains(page, 'Sign in to Spotify');
  assert.deepEqual(commands, ['play','pause','seek','play']);
  assert.equal(await page.evaluate(() => sessionStorage.getItem('music-party.oauth.tokens')), null);
  // Existing exhaustive auth/camera/SDK bridge lifecycle checks.
  await adapterTests(page);
  assert.deepEqual(errors, []);
  console.log('PASS: production Compose setup, manual scan/play/pause/resume/seek/next, settings validation, real QR worker and camera release, mobile layout, logout, direct callback/replay, adapter lifecycle');
} catch (error) {
  if (page) { console.error(await page.locator('body').ariaSnapshot()); await page.screenshot({path:'/tmp/music-party-browser-failure.png'}); }
  throw error;
} finally { await browser?.close(); server.kill(); }
