import { useEffect, useState } from "react";
import { Route, Routes } from "react-router-dom";
import ContextProvider from "../context-provider.tsx";
import { getAccessToken } from "../lib/spotify-client.ts";
import { SpotifyCallback } from "../spotify/spotify-callback.tsx";
import type { DeviceId, SpotifyState } from "../spotify/spotify-state.ts";
import { SpotifyUrlPlayer } from "../spotify/spotify-url-player.tsx";
import QrScannerRoute from "./QrScannerRoute.tsx";
import StartPage from "./StartPage.tsx";

function MusicPartyRoutes() {
  const [spotifyToken, setSpotifyToken] = useState<string | null>(null);
  const [deviceId, setDeviceId] = useState<DeviceId | null>(null);

  useEffect(() => {
    getAccessToken().then((accessToken) => {
      setSpotifyToken(accessToken?.access_token ?? null);
    });
  }, []);

  const spotifyState: SpotifyState = {
    spotifyToken,
    setSpotifyToken,
    deviceId,
    setDeviceId,
  };

  return (
    <ContextProvider spotifyToken={spotifyToken}>
      <Routes>
        <Route path="/" element={<StartPage spotifyState={spotifyState} />} />
        <Route
          path="/oauth/spotify/callback"
          element={<SpotifyCallback setSpotifyToken={setSpotifyToken} />}
        />
        <Route path="/qr-scanner" element={<QrScannerRoute />} />
        <Route path="/play/spotify/:songId" element={<SpotifyUrlPlayer />} />
      </Routes>
    </ContextProvider>
  );
}

export default MusicPartyRoutes;
