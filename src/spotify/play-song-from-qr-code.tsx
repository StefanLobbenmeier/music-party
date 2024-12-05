import {Scanner} from "../qr-scanner/scanner.tsx";
import {resolveSongUrl} from "../lib/resolve-song-url.ts";
import {redirect} from "react-router";


export function PlaySongFromQrCode () {
    const qrCodeScanned = async (result: string) => {
        const spotifyTrack = (await resolveSongUrl(result))!!;
        redirect(`/play/spotify/${spotifyTrack?.uri!!}`)
    }

    const qrCodeFailed = (_: Error | string) => {

    }

    return <div>Scan Your Songs QR Code<Scanner onResult={qrCodeScanned} onError={qrCodeFailed} /></div>
}