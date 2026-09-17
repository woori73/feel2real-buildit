import React from 'react';
import ReactDOM from 'react-dom/client';
import { PreviewApp } from './preview/PreviewApp';
import './index.css';

ReactDOM.createRoot(document.getElementById('preview-root')!).render(
  <React.StrictMode>
    <PreviewApp />
  </React.StrictMode>,
);
