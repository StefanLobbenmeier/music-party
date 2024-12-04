import React from 'react';
import {WebPlaybackSDK} from "react-spotify-web-playback-sdk";
import {getAccessToken, startLoginToSpotify} from "./lib/spotify-client.ts";

function ContextProvider(props: { children: React.ReactNode }) {
    return (
        <WebPlaybackSDK
            initialDeviceName={'music-party'}
            initialVolume={0.5}
            getOAuthToken={(cb) => getAccessToken().then((token) => {
                    if (token != null) {
                        cb(token?.access_token);
                    } else {
                        startLoginToSpotify();
                    }
                }
            )}>
            {props.children}
        </WebPlaybackSDK>
    );
}

export default ContextProvider;