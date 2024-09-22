import {useEffect, useState} from "react";
import {AccessToken} from "@spotify/web-api-ts-sdk";


export function SpotifyPlayer(props: { token: AccessToken; }) {
    const [player, setPlayer] = useState<Spotify.Player>()

    useEffect(() => {
        window.onSpotifyWebPlaybackSDKReady = () => {
            const token = props.token;
            setPlayer( new Spotify.Player({
                name: 'Web Playback SDK Quick Start Player',
                getOAuthToken: cb => { cb(token); },
                volume: 0.5
            }));
    }});

    return (
        <>
            <script src="https://sdk.scdn.co/spotify-player.js"></script>
            <div> Spotify Player </div>
        </>
    );
}