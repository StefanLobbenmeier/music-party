import QrScanner from 'qr-scanner';
let scanner;
let sdkLoading;
export function install(callback) {
  const status = document.getElementById('bridge-status');
  const report = message => { status.textContent = message; callback(message); };
  document.getElementById('camera-start').onclick = async () => {
    try {
      if (!scanner) scanner = new QrScanner(document.getElementById('camera'), result => {
        report(`QR decoded: ${result.data}`); scanner.stop();
      }, { preferredCamera: 'environment', returnDetailedScanResult: true });
      scanner.setInversionMode('both');
      await scanner.start(); report('Camera started');
    } catch (error) { report(`Camera blocked: ${error.message || error}`); }
  };
  document.getElementById('camera-stop').onclick = () => { scanner?.stop(); report('Camera released'); };
  document.getElementById('qr-fixture').onclick = async () => {
    let worker;
    try {
      const { createWorker } = await import('qr-scanner/qr-scanner-worker.min.js');
      worker = createWorker();
      const result = await QrScanner.scanImage('/qr-fixture.png', { qrEngine: worker, returnDetailedScanResult: true });
      report(`QR worker fixture decoded: ${result.data}`);
    } catch (error) { report(`QR worker fixture failed: ${error.message || error}`); }
    finally { worker?.terminate(); }
  };
  document.getElementById('fake').onclick = () => report('Fake SDK ready callback: fake-device (no Spotify access proven)');
  document.getElementById('sdk-load').onclick = () => {
    if (sdkLoading) return;
    window.onSpotifyWebPlaybackSDKReady = () => report(`Spotify SDK ready callback; Player constructor: ${typeof window.Spotify.Player}`);
    sdkLoading = document.createElement('script');
    sdkLoading.src = 'https://sdk.scdn.co/spotify-player.js';
    sdkLoading.onerror = () => { report('Spotify SDK script blocked'); sdkLoading.remove(); sdkLoading = undefined; };
    document.head.append(sdkLoading);
  };
  window.addEventListener('pagehide', () => { scanner?.destroy(); scanner = undefined; });
  report('QR module loaded; worker starts on camera decoding');
}
