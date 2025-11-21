import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { toPng } from "html-to-image";
import ColoredMatrix from "../components/ColoredMatrix";
import "./BingoRoute.css";

function BingoRoute() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const seedParam = searchParams.get("seed");
  const matrixRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // If no seed is provided, generate a random one and redirect
    if (seedParam === null) {
      const randomSeed = Math.floor(Math.random() * 1000000000);
      navigate(`/bingo?seed=${randomSeed}`, { replace: true });
    }
  }, [seedParam, navigate]);

  // If no seed yet (before redirect), show nothing
  if (seedParam === null) {
    return null;
  }

  // Parse the seed as a number
  const seed = parseInt(seedParam, 10);

  // Validate the seed is a valid number
  if (isNaN(seed)) {
    return (
      <div>
        <h2>Invalid seed parameter</h2>
        <p>Please provide a valid number for the seed parameter.</p>
      </div>
    );
  }

  const generateNewSeed = () => {
    const randomSeed = Math.floor(Math.random() * 1000000000);
    navigate(`/bingo?seed=${randomSeed}`);
  };

  const copyMatrixToClipboard = async () => {
    if (!matrixRef.current) return;
    try {
      const dataUrl = await toPng(matrixRef.current, { cacheBust: true });
      const res = await fetch(dataUrl);
      const blob = await res.blob();
      await navigator.clipboard.write([
        new window.ClipboardItem({ "image/png": blob })
      ]);
      // Optionally, show a success message here
    } catch (err) {
      // Optionally, show an error message here
      console.error("Failed to copy image:", err);
    }
  };

    const downloadMatrixAsPng = async () => {
        if (!matrixRef.current) return;
        try {
            const dataUrl = await toPng(matrixRef.current, { cacheBust: true });
            const link = document.createElement("a");
            link.href = dataUrl;
            link.download = `bingo-matrix-seed-${seed}.png`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (err) {
            console.error("Failed to download image:", err);
        }
    };

  return (
    <div>
      <h1 className="no-print">Bingo Board</h1>
      <div className="no-print" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
        <p style={{ margin: 0 }}>Seed: {seed}</p>
        <button type="button" onClick={generateNewSeed}>
          New Random Seed
        </button>
        <button type="button" onClick={copyMatrixToClipboard}>
          Copy Matrix as PNG
        </button>
          <button type="button" onClick={downloadMatrixAsPng}>
              Download Matrix as PNG
          </button>
      </div>
      <ColoredMatrix seed={seed} ref={matrixRef} />
    </div>
  );
}

export default BingoRoute;
