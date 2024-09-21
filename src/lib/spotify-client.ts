"use client"

import {AuthenticationResponse, SpotifyApi} from '@spotify/web-api-ts-sdk';

// Choose one of the following:
const host = window.location.protocol + "//" + window.location.host;
const sdk = SpotifyApi.withUserAuthorization(
    "03d28779053a402ebd2b876d3339d6db",
    `${host}/oauth/spotify/callback`,
    [
        "playlist-read-private",
        "playlist-read-collaborative",
    ],
);

export function startLoginToSpotify() {
    sdk.authenticate()
}

export async function completeLoginToSpotify(): Promise<AuthenticationResponse>  {
    // authenticate will take the parameters from the current url path
    return await sdk.authenticate()
}

export function isAuthenticated() {
    return sdk.getAccessToken() != null;
}