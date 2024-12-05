import {Scanner} from "../qr-scanner/scanner.tsx";
import {resolveSongUrl} from "../lib/resolve-song-url.ts";
import {useNavigate} from "react-router-dom";


export function PlaySongFromQrCode () {
    const navigate = useNavigate();

    const qrCodeScanned = async (result: string) => {
        console.log("QR code recognized: ", result);
        const spotifyTrack = (await resolveSongUrl(result))!!;
        navigate(`/play/spotify/${spotifyTrack?.uri!!}`)
    }

    const qrCodeFailed = (error: Error | string) => {
        console.error(error)
    }

    return <div>Scan Your Songs QR Code<Scanner onResult={qrCodeScanned} onError={qrCodeFailed} /></div>
}