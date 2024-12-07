import { logout } from "../lib/spotify-client.ts";

export function SpotifyLogoutButton() {
  return <button onClick={logout}>Logout</button>;
}
