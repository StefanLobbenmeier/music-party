import { useNavigate } from "react-router-dom";
import { useSpotifyPlayer } from "react-spotify-web-playback-sdk";

export function TogglePlay() {
  const player = useSpotifyPlayer();
  const navigate = useNavigate();

  if (player === null) return <div>No Player Found</div>;

  return (
    <>
      <button
        type="button"
        onClick={async () => {
          await player.activateElement();
          await player.togglePlay();
        }}
      >
        Pause / Play
      </button>
      <button
        type="button"
        onClick={async () => {
          navigate("/qr-scanner");
        }}
      >
        Scan new song
      </button>
    </>
  );
}
