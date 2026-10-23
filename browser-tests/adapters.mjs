import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

// Exercise the production bridge in a real secure-context browser; only external camera/SDK are fakes.
export async function adapterTests(page) {
  const source = (await readFile(new URL('../composeApp/src/wasmJsMain/resources/browser-adapters.js', import.meta.url), 'utf8'))
    .replace("import QrScanner from 'qr-scanner';", 'const QrScanner = null;');
  await page.route('**/adapter-under-test.js', route => route.fulfill({ contentType: 'text/javascript', body: source }));
  const result = await page.evaluate(async () => {
    const { BrowserAuth, CameraAdapter, PlayerAdapter, sessionStore, loadSdk } = await import('/adapter-under-test.js');
    const check = (value, label) => { if (!value) throw Error(label); };
    const redirect = location.origin + '/oauth/spotify/callback';
    let href = redirect, assigned;
    const env = { crypto, sessionStorage, location: { get href() { return href; }, assign(value) { assigned = value; } },
      history: { replaceState(_, __, value) { href = location.origin + value; } } };
    const auth = new BrowserAuth('public-client', redirect, env);
    check(JSON.parse(auth.callback()).kind === 'none', 'clean callback reload is ordinary navigation');
    await auth.begin();
    let pending = JSON.parse(sessionStorage.getItem('music-party.oauth.pending'));
    const request = new URL(assigned);
    check(pending.verifier.length === 43 && request.searchParams.get('code_challenge_method') === 'S256', 'PKCE randomness');
    const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(pending.verifier)));
    const expected = btoa(String.fromCharCode(...digest)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    check(request.searchParams.get('code_challenge') === expected, 'S256 challenge');
    href = redirect + '?code=first&state=' + pending.state;
    // Recreate adapter to represent a callback reload with persisted pending state.
    const reloaded = new BrowserAuth('public-client', redirect, env);
    check(JSON.parse(reloaded.callback()).code === 'first', 'reload callback');
    check(href === redirect, 'callback URL cleanup');
    href = redirect + '?code=first&state=' + pending.state;
    check(JSON.parse(reloaded.callback()).kind === 'invalid', 'replay rejected');
    for (const kind of ['mismatch', 'denied', 'duplicate', 'expired', 'malformed']) {
      await auth.begin(); pending = JSON.parse(sessionStorage.getItem('music-party.oauth.pending'));
      if (kind === 'expired') { pending.created -= 700000; sessionStorage.setItem('music-party.oauth.pending', JSON.stringify(pending)); }
      if (kind === 'malformed') sessionStorage.setItem('music-party.oauth.pending', '{');
      href = redirect + '?state=' + (kind === 'mismatch' ? 'wrong' : pending.state) +
        (kind === 'denied' ? '&error=access_denied' : '&code=x' + (kind === 'duplicate' ? '&code=y' : ''));
      check(JSON.parse(auth.callback()).kind === (kind === 'denied' ? 'denied' : 'invalid'), kind);
      check(href === redirect && !sessionStorage.getItem('music-party.oauth.pending'), 'consume ' + kind);
    }
    sessionStore.write('credentials'); auth.clear(); check(sessionStore.read() === null, 'logout storage');

    let fake, scans = 0, released = 0, failures = [];
    class Scanner {
      constructor(video, decoded, options) { fake = this; this.decoded = decoded; check(options.preferredCamera === 'environment', 'rear preference'); }
      setInversionMode(mode) { check(mode === 'both', 'inversion'); }
      async start() {}
      stop() {}
      destroy() { this.destroyed = true; }
    }
    const video = document.createElement('video');
    const camera = new CameraAdapter(video, () => scans++, error => failures.push(error), Scanner);
    await camera.start();
    fake.decoded({ data: 'card' }); fake.decoded({ data: 'card' }); check(scans === 1, 'duplicate suppression');
    // Use a real synthetic media stream to verify explicit track release.
    const stream = await navigator.mediaDevices.getUserMedia({ video: true });
    const tracks = stream.getTracks(); video.srcObject = stream;
    camera.pause(); released = tracks.every(t => t.readyState === 'ended');
    check(released && video.srcObject === null, 'media release');
    await camera.start(); fake.decoded({ data: 'next' }); check(scans === 2, 'explicit resume');
    fake.start = async () => { throw new DOMException('denied', 'NotAllowedError'); };
    await camera.start(); check(failures.at(-1) === 'permission-denied', 'permission error');
    fake.start = async () => { throw Error('Camera not found.'); };
    await camera.start(); check(failures.at(-1) === 'no-camera', 'camera missing');
    let finishStart; fake.start = () => new Promise(resolve => { finishStart = resolve; });
    const starting = camera.start(); camera.dispose(); finishStart(); await starting;
    check(fake.destroyed && video.srcObject === null, 'start/disposal race');

    let count = 0, player, token = 'first', callbacks = [], states = [];
    window.Spotify = { Player: class {
      constructor(options) { count++; player = this; this.options = options; this.listeners = new Map(); }
      addListener(name, fn) { this.listeners.set(name, fn); }
      removeListener(name) { this.listeners.delete(name); }
      async connect() { return true; }
      async activateElement() { this.activated = true; }
      disconnect() { this.disconnected = true; }
    } };
    const adapter = new PlayerAdapter((success) => success(token), raw => states.push(JSON.parse(raw)));
    await Promise.all([adapter.connect(), adapter.connect()]);
    check(count === 1 && states.at(-1).loaded && states.at(-1).connected && !states.at(-1).ready, 'loaded/connected');
    const options = player.options;
    options.getOAuthToken(t => callbacks.push(t)); token = 'refreshed'; options.getOAuthToken(t => callbacks.push(t));
    check(callbacks.join(',') === 'first,refreshed', 'fresh SDK tokens');
    player.listeners.get('ready')({ device_id: 'local' });
    check(states.at(-1).ready && !states.at(-1).active && !states.at(-1).playing, 'ready separate');
    await adapter.activate(); check(player.activated && states.at(-1).activated, 'gesture activation');
    player.listeners.get('player_state_changed')({ paused: false }); check(states.at(-1).active && states.at(-1).playing, 'playing');
    player.listeners.get('autoplay_failed')(); check(states.at(-1).failure === 'autoplay-blocked', 'autoplay');
    player.listeners.get('not_ready')({}); check(!states.at(-1).ready && !states.at(-1).active, 'not ready');
    const lateReady = player.listeners.get('ready');
    adapter.dispose(); lateReady({ device_id: 'stale' }); check(!states.at(-1).ready, 'late event ignored');
    check(player.disconnected && player.listeners.size === 0 && !states.at(-1).connected, 'SDK cleanup');
    options.getOAuthToken(() => { throw Error('stale token delivered'); });
    const failed = new PlayerAdapter(() => {}, raw => states.push(JSON.parse(raw)), async () => { throw Error('offline'); });
    await failed.connect().catch(() => {}); check(states.at(-1).failure === 'sdk-unavailable', 'remote fallback'); failed.dispose();
    let finishLoad;
    const pendingPlayer = new PlayerAdapter(() => {}, raw => states.push(JSON.parse(raw)), () => new Promise(resolve => { finishLoad = resolve; }));
    const opening = pendingPlayer.connect(); pendingPlayer.disconnect(); finishLoad(); await opening;
    check(count === 1 && !states.at(-1).connected, 'disconnect during SDK load'); pendingPlayer.dispose();
    const rejectedToken = new PlayerAdapter((success, failure) => failure(), raw => states.push(JSON.parse(raw)));
    await rejectedToken.connect(); player.options.getOAuthToken(() => { throw Error('token failure delivered token'); });
    check(player.disconnected && states.at(-1).failure === 'login-required', 'rejected refresh disconnects'); rejectedToken.dispose();
    delete window.Spotify;
    let scripts = 0;
    const append = document.head.append.bind(document.head);
    document.head.append = element => { if (element.src === 'https://sdk.scdn.co/spotify-player.js') { scripts++; queueMicrotask(() => { window.Spotify = { Player: class {} }; window.onSpotifyWebPlaybackSDKReady(); }); } else append(element); };
    await Promise.all([loadSdk(), loadSdk()]); document.head.append = append;
    check(scripts === 1, 'SDK script loads once');
    return 'PKCE callback/replay/denial, session cleanup, camera lifecycle/duplicates/errors, SDK states/activation/fresh tokens/cleanup/load once';
  });
  assert.ok(result);
  console.log('PASS: browser adapters: ' + result);
}
