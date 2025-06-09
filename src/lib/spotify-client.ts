"use client";

import { type AccessToken, SpotifyApi } from "@spotify/web-api-ts-sdk";

let sdk: SpotifyApi;

export function getSpotifySdk() {
  if (sdk == null) {
    const host = `${window.location.protocol}//${window.location.host}`;
    sdk = SpotifyApi.withUserAuthorization(
      "03d28779053a402ebd2b876d3339d6db",
      `${host}/oauth/spotify/callback`,
      [
        "playlist-read-private",
        "playlist-read-collaborative",
        "streaming",
        "user-read-email",
        "user-read-private",
        "user-read-playback-state",
      ],
    );
  }
  return sdk;
}

export function startLoginToSpotify() {
  return getSpotifySdk().authenticate();
}

export function logout() {
  getSpotifySdk().logOut();
}

export async function completeLoginToSpotify(): Promise<AccessToken> {
  // authenticate will take the parameters from the current url path
  const authenticationResponse = await getSpotifySdk().authenticate();
  return authenticationResponse.accessToken;
}

export function isAuthenticated() {
  return getSpotifySdk().getAccessToken() != null;
}

export function getAccessToken() {
  return getSpotifySdk().getAccessToken();
}
