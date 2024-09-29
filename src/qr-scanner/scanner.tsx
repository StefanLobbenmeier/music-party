import {useEffect, useRef} from "react";
import QrScanner from "qr-scanner";


export function Scanner(props: { onResult: (result: QrScanner.ScanResult) => void, onError: (error: Error | string) => void })  {
    const videoRef = useRef<HTMLVideoElement>(null)

    useEffect(() => {
        let scanner = null
        if (!videoRef.current) return

        scanner = new QrScanner(videoRef.current, props.onResult, {
            onDecodeError: props.onError,
            returnDetailedScanResult: true,
            preferredCamera: 'environment',
            maxScansPerSecond: 2,
            highlightScanRegion: true
        });

        scanner.setInversionMode('invert');

        scanner.start().then(() => console.log('started')).catch(console.error);

        return () => {
            if (scanner) scanner.destroy();
        }
    }, [])

    return (
        <div className='scanner fullscreen__panel'>
            <video className='scanner__video' ref={videoRef} playsInline muted/>
        </div>
    )
}