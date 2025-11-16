import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import ColoredMatrix from "../components/ColoredMatrix";

function BingoRoute() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const seedParam = searchParams.get("seed");

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

  return (
    <div>
      <h1>Bingo Board</h1>
      <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
        <p style={{ margin: 0 }}>Seed: {seed}</p>
        <button type="button" onClick={generateNewSeed}>
          New Random Seed
        </button>
      </div>
      <ColoredMatrix seed={seed} />
    </div>
  );
}

export default BingoRoute;
