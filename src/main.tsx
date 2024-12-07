import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import { BrowserRouter} from "react-router-dom";
import ContextProvider from "./context-provider.tsx";
import MusicPartyRoutes from "./routes/routes.tsx";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ContextProvider>
        <BrowserRouter>
            <MusicPartyRoutes></MusicPartyRoutes>
        </BrowserRouter>
    </ContextProvider>
  </React.StrictMode>,
);
