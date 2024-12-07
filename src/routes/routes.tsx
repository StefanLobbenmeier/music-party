import {BrowserRouter, Route, Routes} from "react-router-dom";
import App from "../App.tsx";
import {SpotifyCallback} from "../spotify/spotify-callback.tsx";
import {SpotifyUrlPlayer} from "../spotify/spotify-url-player.tsx";

function MusicPartyRoutes() {
    return <Routes>
        <Route path="/" element={<App/>}/>
        <Route path="/oauth/spotify/callback" element={<SpotifyCallback/>}/>
        <Route path="/play/spotify/:songId" element={<SpotifyUrlPlayer/>}/>
    </Routes>;
}

export default MusicPartyRoutes;