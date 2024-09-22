import {useEffect, useState} from 'react'
import reactLogo from './assets/react.svg'
import viteLogo from '/vite.svg'
import './App.css'
import {SpotifyLoginButton} from "./spotify/spotify-login-button.tsx";
import {getAccessToken} from "./lib/spotify-client.ts";
import {AccessToken} from "@spotify/web-api-ts-sdk";
import {SpotifyPlayer} from "./spotify/spotify-player.tsx";
import {SpotifyLogoutButton} from "./spotify/spotify-logout-button.tsx";

function App() {
  const [count, setCount] = useState(0)

    const [spotifyToken, setSpotifyToken] = useState<AccessToken>(
    )
    useEffect(() => {
        getAccessToken().then((token) => {
            if (token != null) setSpotifyToken(token)
        })
    }, []);

  return (
    <>
      <div>
        <a href="https://vitejs.dev" target="_blank">
          <img src={viteLogo} className="logo" alt="Vite logo" />
        </a>
        <a href="https://react.dev" target="_blank">
          <img src={reactLogo} className="logo react" alt="React logo" />
        </a>
      </div>
      <h1>Vite + React</h1>
      <div className="card">
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>

          {
              spotifyToken == null ?
              <SpotifyLoginButton/> : <SpotifyPlayer token={spotifyToken}/>
          }
          <SpotifyLogoutButton/>

        <p>
          Edit <code>src/App.tsx</code> and save to test HMR
        </p>
      </div>
      <p className="read-the-docs">
        Click on the Vite and React logos to learn more
      </p>
    </>
  )
}

export default App
