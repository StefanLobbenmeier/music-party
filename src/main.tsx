import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import "./index.css";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { SpotifyCallback } from "./spotify/spotify-callback.tsx";
import { SpotifyUrlPlayer } from "./spotify/spotify-url-player.tsx";
import ContextProvider from "./context-provider.tsx";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ContextProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/oauth/spotify/callback" element={<SpotifyCallback />} />
          <Route path="/play/spotify/:songId" element={<SpotifyUrlPlayer />} />
        </Routes>
      </BrowserRouter>
    </ContextProvider>
  </React.StrictMode>,
);
