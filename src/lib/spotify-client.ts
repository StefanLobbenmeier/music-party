"use client"

import {AuthenticationResponse, SpotifyApi} from '@spotify/web-api-ts-sdk';

let sdk: SpotifyApi;
function getSpotifySdk() {
    if (sdk == null) {
        const host = window.location.protocol + "//" + window.location.host;
        sdk = SpotifyApi.withUserAuthorization(
            "03d28779053a402ebd2b876d3339d6db",
            `${host}/oauth/spotify/callback`,
            [
                "playlist-read-private",
                "playlist-read-collaborative",
            ],
        );
    }
    return sdk;
}

export function startLoginToSpotify() {
    getSpotifySdk().authenticate()
}

export async function completeLoginToSpotify(): Promise<AuthenticationResponse>  {
    // authenticate will take the parameters from the current url path
    return await getSpotifySdk().authenticate()
}

export function isAuthenticated() {
    return getSpotifySdk().getAccessToken() != null;
}