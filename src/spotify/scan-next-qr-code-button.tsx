import { useNavigate } from "react-router-dom";

export function ScanNextQrCodeButton() {
  const navigate = useNavigate();

  return (
    <button
      type="button"
      onClick={async () => {
        navigate("/qr-scanner");
      }}
    >
      Scan new song
    </button>
  );
}
