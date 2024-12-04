import {usePlayerDevice, useSpotifyPlayer} from "react-spotify-web-playback-sdk";
import { TrackItem} from "@spotify/web-api-ts-sdk";
import { useEffect, useState} from "react";
import {Scanner} from "../qr-scanner/scanner.tsx";
import {resolveSongUrl} from "../lib/resolve-song-url.ts";
import {getSpotifySdk} from "../lib/spotify-client.ts";

export function PlaySongFromQrCode () {
    console.log("PlaySongFromQrCode")

    const device = usePlayerDevice();
    const player = useSpotifyPlayer();


    console.log("device", device);
    console.log("player", player);

    const [song, setSong] = useState<string | null>(null);
    const [spotifyTrack, setSpotifyTrack] = useState<TrackItem>()
    const qrCodeScanned = async (result: string) => {
        const song = result;
        setSong(song);
        const spotifyTrack = (await resolveSongUrl(song))!!;
        setSpotifyTrack(spotifyTrack!!);
        await getSpotifySdk().player.startResumePlayback(device?.device_id!!, undefined, [spotifyTrack?.uri!!])
    }
    const qrCodeFailed = (_: Error | string) => {

    }

    useEffect(() => {
        player?.activateElement();
    }, []);

    if (device === null) return <div>Device is null</div>;

    return song == null ?
        <div>Scan Your Songs QR Code<Scanner onResult={qrCodeScanned} onError={qrCodeFailed} /></div>
        : <div>The scanned song is {song} and spotify track is {spotifyTrack?.name}</div>
}