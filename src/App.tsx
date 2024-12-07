import { useEffect, useState } from "react";
import "./App.css";
import { SpotifyLoginButton } from "./spotify/spotify-login-button.tsx";
import { getAccessToken } from "./lib/spotify-client.ts";
import { AccessToken } from "@spotify/web-api-ts-sdk";
import { SpotifyLogoutButton } from "./spotify/spotify-logout-button.tsx";
import { ScanSongQrCode } from "./spotify/scan-song-qr-code.tsx";

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
