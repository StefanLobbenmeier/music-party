import {
   useSpotifyPlayer} from "react-spotify-web-playback-sdk";

export function TogglePlay() {
  const player = useSpotifyPlayer();

    if (player === null) return <div>No Player Found</div>;

  return <button onClick={() => {
      console.log("player", player)

      player.activateElement();
      player.togglePlay();
  }}>Pause / Play</button>;
}