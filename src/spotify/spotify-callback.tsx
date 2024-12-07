import { completeLoginToSpotify } from "../lib/spotify-client.ts";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

export function SpotifyCallback() {
  const navigate = useNavigate();

  useEffect(() => {
    completeLoginToSpotify().then(() => {
      navigate("/");
    });
  });

  return <p>Authenticating to Spotify...</p>;
}
