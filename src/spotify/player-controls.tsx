import useSWR from "swr";
import { getSpotifySdk } from "../lib/spotify-client.ts";

export function PlayerControls() {
  const { data, error, isLoading } = useSWR(
    "props.spotifyToken + browserDevice?.status",
    () => {
      return getSpotifySdk().player.getPlaybackState();
    },
    {
      refreshInterval: 1000,
    },
  );

  return (
    <>
      <div>Player Controls {JSON.stringify({ data, error, isLoading })}</div>
    </>
  );
}
