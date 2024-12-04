import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'
import {
    createBrowserRouter,
    RouterProvider,
} from "react-router-dom";
import {SpotifyCallback} from "./spotify/spotify-callback.tsx";
import {SpotifyUrlPlayer} from "./spotify/spotify-url-player.tsx";

const router = createBrowserRouter([
    {
        path: "/",
        element: <App />,
    },
    {
        path: "/oauth/spotify/callback",
        element: <SpotifyCallback />,
    },
    {
        path: "/play/spotify/{songId}",
        element: <SpotifyUrlPlayer/>,
    },
]);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
      <RouterProvider router={router} />
  </React.StrictMode>,
)
