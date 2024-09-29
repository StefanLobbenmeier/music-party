import {usePlayerDevice, useSpotifyPlayer} from "react-spotify-web-playback-sdk";
import {AccessToken} from "@spotify/web-api-ts-sdk";
import {useCallback, useEffect, useState} from "react";
import {Scanner} from "../qr-scanner/scanner.tsx";
import QrScanner from "qr-scanner";

const SPOTIFY_URI = "spotify:track:7xGfFoTpQ2E7fRF5lN10tr";
export function PlaySongFromQrCode (props: { token: AccessToken; }) {
    console.log("PlaySongFromQrCode")

    const device = usePlayerDevice();
    const player = useSpotifyPlayer();


    console.log("device", device);
    console.log("player", player);

    const [song, setSong] = useState<string | null>(null);
    const qrCodeScanned = (result: QrScanner.ScanResult) => {
        setSong(result.data);
        resolveSong();
    }
    const qrCodeFailed = (_: Error | string) => {

    }

    const resolveSong = useCallback(() => {
        console.log(song)
    }, [song]);

    useEffect(() => {
        player?.activateElement();
    }, []);

    const playCarlyRaeJepsen = () => {
        if (device === null) return;

        player?.activateElement();


        fetch(
            `https://api.spotify.com/v1/me/player/play?device_id=${device.device_id}`,
            {
                method: "PUT",
                body: JSON.stringify({ uris: [SPOTIFY_URI] }),
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${props.token.access_token}`,
                },
            },
        );
    };

    if (device === null) return <div>Device is null</div>;

    return song == null ?
        <div>Scan Your Songs QR Code<Scanner onResult={qrCodeScanned} onError={qrCodeFailed} /> or click the button <button onClick={playCarlyRaeJepsen}>Play Carly</button></div>
        : <div>The scanned song is {song}</div>
}