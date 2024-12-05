import {useParams} from "react-router-dom";
import {usePlayerDevice } from "react-spotify-web-playback-sdk";
import {useEffect} from "react";
import {getSpotifySdk} from "../lib/spotify-client.ts";
import {TogglePlay} from "./toggle-play.tsx";

export function SpotifyUrlPlayer() {
    const {songId} = useParams();
    const device = usePlayerDevice();

    useEffect(() => {
        if (songId) {
            getSpotifySdk().player.startResumePlayback(device?.device_id!!, undefined, [songId]);
        }
    }, [songId]);

    return <>
        <div>Song id is {songId}</div>
        <div>Device id is {device?.device_id}</div>
        <TogglePlay />
    </>
}

// https://open.spotify.com/track/3RIYlQ20buPfanKbSyGh98?si=1addc55fe7874b94
// spotify:track:5T7FLSnK2cQLfsP1hODPiC
// https://localhost:5173/play/spotify/spotify:track:5T7FLSnK2cQLfsP1hODPiC