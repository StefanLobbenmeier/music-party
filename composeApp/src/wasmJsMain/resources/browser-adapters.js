import QrScanner from 'qr-scanner';

const pendingKey = 'music-party.oauth.pending';
const tokenKey = 'music-party.oauth.tokens';
export const sessionStore = {
  read: () => sessionStorage.getItem(tokenKey),
  write: value => value === null ? sessionStorage.removeItem(tokenKey) : sessionStorage.setItem(tokenKey, value),
};
const base64url = bytes => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
export class BrowserAuth {
  constructor(clientId, redirectUri, env = window) {
    const redirect = new URL(redirectUri);
    if (!clientId || redirect.username || redirect.password || redirect.search || redirect.hash ||
        redirect.pathname !== '/oauth/spotify/callback' ||
        !(redirect.protocol === 'https:' || (redirect.protocol === 'http:' && ['127.0.0.1', '[::1]'].includes(redirect.hostname)))) throw Error('Invalid OAuth configuration');
    this.clientId = clientId; this.redirectUri = redirect.href; this.env = env; this.generation = 0;
  }
  async begin() {
    const generation = ++this.generation;
    const random = () => base64url(this.env.crypto.getRandomValues(new Uint8Array(32)));
    const state = random(), verifier = random();
    const challenge = base64url(new Uint8Array(await this.env.crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));
    if (generation !== this.generation) return;
    this.env.sessionStorage.setItem(pendingKey, JSON.stringify({ state, verifier, created: Date.now(), redirect: this.redirectUri }));
    const url = new URL('https://accounts.spotify.com/authorize');
    url.search = new URLSearchParams({ client_id: this.clientId, response_type: 'code', redirect_uri: this.redirectUri,
      code_challenge_method: 'S256', code_challenge: challenge, state,
      scope: 'streaming user-read-playback-state user-modify-playback-state playlist-read-private playlist-read-collaborative user-read-email user-read-private' });
    this.env.location.assign(url.href);
  }
  // Consumes before exchange: reload/replay never exchanges the same code twice.
  callback() {
    const url = new URL(this.env.location.href);
    if (url.origin + url.pathname !== this.redirectUri) return JSON.stringify({ kind: 'none' });
    const raw = this.env.sessionStorage.getItem(pendingKey);
    this.env.sessionStorage.removeItem(pendingKey);
    this.env.history.replaceState(null, '', url.pathname);
    let pending;
    try { pending = JSON.parse(raw); } catch { /* malformed storage is an invalid callback */ }
    const one = name => url.searchParams.getAll(name).length === 1 && url.searchParams.get(name);
    if (!pending || !one('state') || one('state') !== pending.state || pending.redirect !== this.redirectUri ||
        !/^[A-Za-z0-9_-]{43}$/.test(pending.verifier) || !Number.isFinite(pending.created) ||
        Date.now() - pending.created < 0 || Date.now() - pending.created > 600000) return JSON.stringify({ kind: 'invalid' });
    if (one('error') && !url.searchParams.has('code')) return JSON.stringify({ kind: 'denied' });
    if (!one('code') || url.searchParams.has('error') || url.hash) return JSON.stringify({ kind: 'invalid' });
    return JSON.stringify({ kind: 'code', code: one('code'), verifier: pending.verifier });
  }
  clear() { ++this.generation; this.env.sessionStorage.removeItem(pendingKey); this.env.sessionStorage.removeItem(tokenKey); }
}

export class CameraAdapter {
  constructor(video, decoded, failed, Scanner = QrScanner) {
    this.video = video; this.decoded = decoded; this.failed = failed; this.live = false; this.disposed = false; this.generation = 0;
    video.muted = true; video.playsInline = true;
    this.scanner = new Scanner(video, result => {
      if (!this.live || this.disposed) return;
      this.stop(); this.decoded(result.data);
    }, { preferredCamera: 'environment', returnDetailedScanResult: true });
    this.scanner.setInversionMode('both');
    this.onHide = () => this.stop(); window.addEventListener('pagehide', this.onHide);
  }
  async start() {
    if (this.disposed) throw Error('Camera disposed');
    if (!navigator.userActivation?.isActive) throw Error('Camera requires a user action');
    if (this.starting) throw Error('Camera start is pending');
    const generation = ++this.generation; this.live = true; this.starting = true;
    try { await this.scanner.start(); if (generation !== this.generation) this.release(); }
    catch (error) {
      if (generation !== this.generation) return;
      this.stop();
      const text = String(error?.name || '') + ' ' + String(error?.message || error);
      this.failed(/NotAllowed|Permission|denied/i.test(text) ? 'permission-denied' : /NotFound|Camera not found|no camera/i.test(text) ? 'no-camera' : 'camera-unavailable');
    } finally { this.starting = false; }
  }
  release() { this.scanner.stop(); this.video.srcObject?.getTracks().forEach(track => track.stop()); this.video.srcObject = null; }
  stop() { this.live = false; ++this.generation; this.release(); }
  pause() { this.stop(); }
  dispose() { if (this.disposed) return; this.stop(); this.disposed = true; this.scanner.destroy(); window.removeEventListener('pagehide', this.onHide); }
}

let sdkLoading;
export function loadSdk() {
  if (window.Spotify?.Player) return Promise.resolve();
  if (!sdkLoading) sdkLoading = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    const previous = window.onSpotifyWebPlaybackSDKReady;
    const finish = error => { clearTimeout(timer); window.onSpotifyWebPlaybackSDKReady = previous;
      if (error) { script.remove(); sdkLoading = undefined; reject(Error('sdk-unavailable')); } else resolve(); };
    window.onSpotifyWebPlaybackSDKReady = () => { finish(); previous?.(); };
    const timer = setTimeout(() => finish(true), 15000);
    script.src = 'https://sdk.scdn.co/spotify-player.js'; script.onerror = () => finish(true); document.head.append(script);
  });
  return sdkLoading;
}
let owner;
export class PlayerAdapter {
  constructor(token, changed, loader = loadSdk) {
    this.token = token; this.changed = changed; this.loader = loader; this.listeners = []; this.generation = 0;
    this.state = { loaded: false, connected: false, ready: false, deviceId: null, active: false, playing: false, activated: false, failure: null };
    this.onHide = () => this.disconnect(); window.addEventListener('pagehide', this.onHide);
  }
  emit(patch) { Object.assign(this.state, patch); this.changed(JSON.stringify(this.state)); }
  connect() {
    if (this.connecting) return this.connecting;
    if (this.player) return Promise.resolve();
    if (owner && owner !== this) return Promise.reject(Error('browser-player-already-connected'));
    owner = this; const generation = ++this.generation;
    this.connecting = this.open(generation).finally(() => { if (generation === this.generation) this.connecting = null; });
    return this.connecting;
  }
  async open(generation) {
    try {
      await this.loader(); if (generation !== this.generation) return;
      this.emit({ loaded: true, failure: null });
      this.player = new window.Spotify.Player({ name: 'music-party', getOAuthToken: callback => {
        // The Kotlin callback obtains a fresh token from the shared provider each time.
        this.token(value => { if (generation === this.generation) callback(value); }, () => {
          if (generation === this.generation) { this.disconnect(); this.emit({ failure: 'login-required' }); }
        });
      } });
      const listen = (name, fn) => { const guarded = value => { if (generation === this.generation) fn(value); };
        this.listeners.push([name, guarded]); this.player.addListener(name, guarded); };
      listen('ready', event => this.emit({ ready: true, deviceId: event.device_id }));
      listen('not_ready', () => this.emit({ ready: false, deviceId: null, active: false, playing: false }));
      listen('player_state_changed', value => this.emit({ active: !!value, playing: !!value && !value.paused }));
      listen('autoplay_failed', () => this.emit({ activated: false, playing: false, failure: 'autoplay-blocked' }));
      for (const event of ['initialization_error', 'authentication_error', 'account_error', 'playback_error']) {
        listen(event, () => { this.disconnect(); this.emit({ failure: event }); });
      }
      const connected = await this.player.connect(); if (generation !== this.generation) return;
      if (!connected) throw Error('connection-failed');
      this.emit({ connected: true });
    } catch (error) { if (generation === this.generation) { this.disconnect(); this.emit({ failure: 'sdk-unavailable' }); } throw Error('Browser playback unavailable; choose a remote device'); }
  }
  activate() {
    if (!this.player || !navigator.userActivation?.isActive) return Promise.reject(Error('Audio requires a user action and connected player'));
    const generation = this.generation;
    return this.player.activateElement().then(() => { if (generation === this.generation) this.emit({ activated: true, failure: null }); }, () => {
      if (generation === this.generation) this.emit({ activated: false, failure: 'autoplay-blocked' }); throw Error('autoplay-blocked');
    });
  }
  disconnect() {
    ++this.generation;
    for (const [name, fn] of this.listeners) this.player?.removeListener(name, fn);
    this.listeners = []; this.player?.disconnect(); this.player = null; this.connecting = null;
    if (owner === this) owner = null;
    this.emit({ connected: false, ready: false, deviceId: null, active: false, playing: false, activated: false });
  }
  dispose() { this.disconnect(); window.removeEventListener('pagehide', this.onHide); }
}

// Namespace exports keep Kotlin/Wasm interop explicit and independently testable.
export const createAuth = (clientId, redirectUri) => new BrowserAuth(clientId, redirectUri);
export const createCamera = (video, decoded, failed) => new CameraAdapter(video, decoded, failed);
export const createPlayer = (token, changed) => new PlayerAdapter(token, changed);
export const read = () => sessionStore.read();
export const write = value => sessionStore.write(value);
