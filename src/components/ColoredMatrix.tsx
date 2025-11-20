import "./ColoredMatrix.css";

type Color = "pink" | "purple" | "yellow" | "cyan" | "green";

// Seeded random number generator using Mulberry32
class SeededRandom {
  private seed: number;

  constructor(seed: number) {
    this.seed = seed;
  }

  // Returns a random number between 0 and 1
  next(): number {
    let t = (this.seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }
}

// Function to create a 5x5 array with evenly distributed colors
function generateColorMatrix(seed: number): Color[] {
  const colors: Color[] = ["pink", "purple", "yellow", "cyan", "green"];
  const rng = new SeededRandom(seed);

  // Create array with each color appearing exactly 5 times (5 colors × 5 = 25 squares)
  const colorArray: Color[] = [];
  for (const color of colors) {
    for (let i = 0; i < 5; i++) {
      colorArray.push(color);
    }
  }

  // Shuffle the array using Fisher-Yates algorithm with seeded random
  for (let i = colorArray.length - 1; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1));
    [colorArray[i], colorArray[j]] = [colorArray[j], colorArray[i]];
  }

  return colorArray;
}

interface ColoredMatrixProps {
  seed: number;
}

function ColoredMatrix({ seed }: ColoredMatrixProps) {
  const colors = generateColorMatrix(seed);

  return (
    <div className="colored-matrix-container">
      <div className="bingo-card">
        <div className="bingo-card-top">
          <div className="colored-matrix">
            {colors.map((color, index) => (
              <div
                key={index}
                className={`matrix-square color-${color}`}
              />
            ))}
          </div>
        </div>
        <div className="bingo-card-middle">
          <img src="/bingo-logo.png" alt="Bingo Logo" className="bingo-logo" />
        </div>
        <div className="bingo-card-bottom">
        </div>
      </div>
    </div>
  );
}

export default ColoredMatrix;
