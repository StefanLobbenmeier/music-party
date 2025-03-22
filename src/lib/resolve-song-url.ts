import { getSpotifySdk } from "./spotify-client.ts";

const playlistIds = new Map<string, string>([
  ["de", "26zIHVncgI9HmHlgYWwnDi"], // general german game
  ["de/aaaa0012", "15hZ0ez6sHYhTeCCshxJTN"], // summer party
  ["de/aaaa0015", "2u0vgWYqU1TWVcDehJnZuN"], // guilty pleasures
  ["de/aaaa0019", "58y9xPPIRWd8tqlOaKoDOI"], // bingo
  ["de/aaaa0026", "2jlbmBYM1RLZrsyY67wuDQ"], // movies and tv soundtracks
]);

export async function resolveSongUrl(urlString: string) {
  const url = new URL(
    urlString.startsWith("http") ? urlString : `https://${urlString}`,
  );

  if (url.host === "www.hitstergame.com") {
    return resolveHitsterUrl(url);
  }

  return null;
}

async function resolveHitsterUrl(url: URL) {
  const { songIndex, playlistId } = getSongAndPlaylistId(url);

  const playlistItems = await getSpotifySdk().playlists.getPlaylistItems(
    playlistId!,
    undefined,
    undefined,
    1,
    songIndex,
  );
  return playlistItems.items[0].track;
}

function getSongAndPlaylistId(url: URL) {
  // pathname starts with / so first component is empty
  const pathComponents = url.pathname.split("/");

  if (pathComponents.length === 3) {
    const country = pathComponents[1];
    const songId = pathComponents[2];
    const songIndex = Number(songId) - 1;
    const playlistId = playlistIds.get(country);
    return { songIndex, playlistId };
  }

  if (pathComponents.length === 4) {
    const country = pathComponents[1];
    const game = pathComponents[2];
    const songId = pathComponents[3];
    const songIndex = Number(songId) - 1;
    const playlistId = playlistIds.get(`${country}/${game}`);
    return { songIndex, playlistId };
  }

  throw new Error(`Unable to resolve url ${url}.`);
}
