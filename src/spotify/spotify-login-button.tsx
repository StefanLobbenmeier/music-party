import { startLoginToSpotify } from "../lib/spotify-client.ts";

export function SpotifyLoginButton() {
  return (
    <button type="button" onClick={startLoginToSpotify}>
      Click me
    </button>
  );
}
