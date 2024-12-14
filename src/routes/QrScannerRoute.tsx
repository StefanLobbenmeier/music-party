import { useNavigate } from "react-router-dom";
import { resolveSongUrl } from "../lib/resolve-song-url.ts";
import { Scanner } from "../qr-scanner/scanner.tsx";

export function QrScannerRoute() {
  const navigate = useNavigate();

  const qrCodeScanned = async (result: string) => {
    console.log("QR code recognized: ", result);
    const spotifyTrack = (await resolveSongUrl(result))!;
    navigate(`/play/spotify/${spotifyTrack?.uri!}`);
  };

  const qrCodeFailed = (error: Error | string) => {
    console.error(error);
  };

  return <Scanner onResult={qrCodeScanned} onError={qrCodeFailed} />;
}

export default QrScannerRoute;
