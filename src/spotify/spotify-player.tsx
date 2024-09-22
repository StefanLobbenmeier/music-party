import {useCallback } from "react";
import {AccessToken} from "@spotify/web-api-ts-sdk";
import {WebPlaybackSDK} from "react-spotify-web-playback-sdk";
import {TogglePlay} from "./toggle-play.tsx";
import {SongTitle} from "./song-title.tsx";
import {PlayCarlyRaeJepsen} from "./tmp.tsx";


export function SpotifyPlayer(props: { token: AccessToken; }) {
    const getOAuthToken = useCallback(callback => callback(props.token.access_token), []);

    return (
        <WebPlaybackSDK
            getOAuthToken={getOAuthToken}
            initialVolume={0.5}
            initialDeviceName="music-party">

            <TogglePlay />
            <SongTitle />
            <PlayCarlyRaeJepsen token={props.token} />

        </WebPlaybackSDK>
    );
}