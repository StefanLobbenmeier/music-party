import {useParams} from "react-router-dom";

export function SpotifyUrlPlayer() {
    const {songId} = useParams();

    return <div>Song id is {songId}</div>
}