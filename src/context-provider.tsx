import type React from "react";
import { WebPlaybackSDK } from "react-spotify-web-playback-sdk";

function ContextProvider(props: {
  children: React.ReactNode;
  spotifyToken: string | null;
}) {
  const spotifyToken = props.spotifyToken;

  if (spotifyToken == null) {
    return props.children;
  }
  return (
    <WebPlaybackSDK
      initialDeviceName={"music-party"}
      initialVolume={0.5}
      getOAuthToken={(cb) => cb(spotifyToken)}
    >
      {props.children}
    </WebPlaybackSDK>
  );
}

export default ContextProvider;
