import {AuthenticationResponse, SpotifyApi} from '@spotify/web-api-ts-sdk';

// Choose one of the following:
const sdk = SpotifyApi.withUserAuthorization(
    "03d28779053a402ebd2b876d3339d6db",
    "http://localhost:3000/oauth/spotify/callback",
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