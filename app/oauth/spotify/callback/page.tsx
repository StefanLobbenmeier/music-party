"use client"

import {useEffect} from "react";
import {completeLoginToSpotify} from "@/app/lib/spotify/spotify-client";
import {useRouter} from "next/navigation";

export default function Page() {
    const router = useRouter()

    useEffect(() => {
        completeLoginToSpotify().then((auth) => {
            console.log("Hello", auth)
            router.replace("/")
        });
    })

    return <h1>Logging in to spotify</h1>
}