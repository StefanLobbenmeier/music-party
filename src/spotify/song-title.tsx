import { usePlaybackState } from "react-spotify-web-playback-sdk";

export function SongTitle() {
  const playbackState = usePlaybackState();

  return <p>Current song: {playbackState?.track_window.current_track.name}</p>;
};