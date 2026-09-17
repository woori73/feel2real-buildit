import React from 'react';
import ReactDOM from 'react-dom/client';
import { ZonesApp } from './zones/ZonesApp';
import './index.css';

ReactDOM.createRoot(document.getElementById('zones-root')!).render(
  <React.StrictMode>
    <ZonesApp />
  </React.StrictMode>,
);
