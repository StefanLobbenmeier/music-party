import {getSpotifySdk} from "./spotify-client.ts";

const playlistIds = new Map<string, string>([
    ["de", "26zIHVncgI9HmHlgYWwnDi"]
]);

export async function resolveSongUrl(urlString: string) {
    const url = new URL(urlString.startsWith("http") ? urlString : `https://${urlString}`);

    if (url.host === "www.hitstergame.com") {
        return resolveHitsterUrl(url)
    }

    return null
}

async function resolveHitsterUrl(url: URL) {
    // pathname starts with / so first component is empty
    let pathComponents = url.pathname.split("/");
    const country = pathComponents[1];
    const songId = pathComponents[2];
    const songIndex = Number(songId) - 1;

    const playlistId = playlistIds.get(country)!!;

    const playlistItems = await getSpotifySdk().playlists.getPlaylistItems(playlistId, undefined, undefined, 1, songIndex);
    return playlistItems.items[0].track;
}