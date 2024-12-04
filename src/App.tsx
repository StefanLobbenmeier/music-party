import {useEffect, useState} from 'react'
import './App.css'
import {SpotifyLoginButton} from "./spotify/spotify-login-button.tsx";
import {getAccessToken} from "./lib/spotify-client.ts";
import {AccessToken} from "@spotify/web-api-ts-sdk";
import {SpotifyPlayer} from "./spotify/spotify-player.tsx";
import {SpotifyLogoutButton} from "./spotify/spotify-logout-button.tsx";

function App() {
    const [spotifyToken, setSpotifyToken] = useState<AccessToken>(
    )
    useEffect(() => {
        getAccessToken().then((token) => {
            if (token != null) setSpotifyToken(token)
        })
    }, []);

  return (
    <>
      <div className="card">
          {
              spotifyToken == null ?
              <SpotifyLoginButton/> : <SpotifyPlayer token={spotifyToken}/>
          }
          <SpotifyLogoutButton/>
      </div>
    </>
  )
}

export default App
