import {startLoginToSpotify} from "../lib/spotify-client.ts";

export function SpotifyLoginButton() {
    return (
        <button onClick={startLoginToSpotify}>
            Click me
        </button>
    );
}