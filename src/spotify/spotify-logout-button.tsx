import { logout } from "../lib/spotify-client.ts";

export function SpotifyLogoutButton() {
  return (
    <button type="button" onClick={logout}>
      Logout
    </button>
  );
}
