import { useEffect, useState } from "react";
import "./App.css";
import type { AccessToken } from "@spotify/web-api-ts-sdk";
import { getAccessToken } from "./lib/spotify-client.ts";
import { ScanSongQrCode } from "./spotify/scan-song-qr-code.tsx";
import { SpotifyLoginButton } from "./spotify/spotify-login-button.tsx";
import { SpotifyLogoutButton } from "./spotify/spotify-logout-button.tsx";

function App() {
  const [spotifyToken, setSpotifyToken] = useState<AccessToken>();
  useEffect(() => {
    getAccessToken().then((token) => {
      if (token != null) setSpotifyToken(token);
    });
  }, []);

  return (
    <>
      <div className="card">
        {spotifyToken == null ? <SpotifyLoginButton /> : <ScanSongQrCode />}
        <SpotifyLogoutButton />
      </div>
    </>
  );
}

export default App;
