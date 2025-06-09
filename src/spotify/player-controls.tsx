import { useEffect, useState } from "react";
import useSWR from "swr";
import { getSpotifySdk } from "../lib/spotify-client.ts";

function msToHMS(ms) {
  let seconds: string | number = ms / 1000;
  let minutes = seconds / 60;
  seconds = seconds % 60;

  seconds = Math.floor(seconds);
  minutes = Math.floor(minutes);

  let secondsFormatted = seconds.toString();

  if (seconds < 10) {
    secondsFormatted = `0${secondsFormatted}`;
  }
  return `${minutes}:${secondsFormatted}`;
}

function TrackProgress(props: {
  progress: number;
  duration: number;
  onProgressChange: (progress: number) => Promise<void>;
}) {
  const [localProgress, setLocalProgress] = useState(props.progress);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    if (editing) return;
    setLocalProgress(props.progress);
  });

  const durationString = msToHMS(props.duration);

  let progressString: string;
  if (editing) {
    progressString = msToHMS(localProgress);
  } else {
    progressString = msToHMS(props.progress);
  }

  return (
    <div className="slidecontainer">
      <div>
        {progressString} / {durationString}
      </div>
      <input
        type="range"
        min="0"
        max={props.duration}
        value={localProgress}
        className="slider"
        id="myRange"
        onChange={(event) => {
          setEditing(true);
          setLocalProgress(Number.parseInt(event.target.value));
        }}
        onMouseUp={async (_event) => {
          await props.onProgressChange(localProgress);
          setEditing(false);
        }}
      />
    </div>
  );
}

export function PlayerControls() {
  const { data, error, isLoading } = useSWR(
    "PlayerControls - getPlaybackState",
    () => {
      return getSpotifySdk().player.getPlaybackState();
    },
    {
      refreshInterval: 5000,
    },
  );

  console.log({ data, error, isLoading });

  return (
    <>
      {data && (
        <TrackProgress
          progress={data.progress_ms}
          duration={data.item.duration_ms}
          onProgressChange={async (progress) => {
            try {
              await getSpotifySdk().player.seekToPosition(progress);
            } catch (e) {
              console.warn(e);
            }
          }}
        />
      )}
    </>
  );
}
