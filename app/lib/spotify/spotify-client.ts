import {SpotifyApi} from '@spotify/web-api-ts-sdk';

// Choose one of the following:
const sdk = SpotifyApi.withUserAuthorization(
    "03d28779053a402ebd2b876d3339d6db",
    "http://localhost:3000/oauth/spotify/callback",
    [
        "playlist-read-private",
        "playlist-read-collaborative",
    ],
);

export async function loginToSpotify() {
    await sdk.authenticate()
}