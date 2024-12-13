import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { completeLoginToSpotify } from "../lib/spotify-client.ts";

export function SpotifyCallback(props: {
  setSpotifyToken: (token: string) => void;
}) {
  const navigate = useNavigate();

  useEffect(() => {
    completeLoginToSpotify().then((accessToken) => {
      props.setSpotifyToken(accessToken.access_token);
      navigate("/");
    });
  });

  return <p>Authenticating to Spotify...</p>;
}
