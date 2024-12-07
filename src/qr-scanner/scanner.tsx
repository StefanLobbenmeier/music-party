import QrScanner from "qr-scanner";
import { useEffect, useRef } from "react";

export function Scanner(props: {
  onResult: (result: string) => void;
  onError: (error: Error | string) => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    let scanner = null;
    if (!videoRef.current) return;

    scanner = new QrScanner(
      videoRef.current,
      (scanResult) => {
        props.onResult(scanResult.data);
      },
      {
        onDecodeError: props.onError,
        returnDetailedScanResult: true,
        preferredCamera: "environment",
        maxScansPerSecond: 2,
        highlightScanRegion: true,
      },
    );

    scanner.setInversionMode("invert");

    scanner
      .start()
      .then(() => console.log("started"))
      .catch(console.error);

    return () => {
      if (scanner) scanner.destroy();
    };
  }, [props.onResult, props.onError]);

  return (
    <div className="scanner fullscreen__panel">
      <video className="scanner__video" ref={videoRef} playsInline muted />
    </div>
  );
}
