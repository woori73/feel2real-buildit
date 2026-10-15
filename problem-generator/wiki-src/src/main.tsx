import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import './styles/workspace.css'
import './styles/wiki-ink.css'
import { initializeWorkspace } from './lib/workspaceStorage'

const root = ReactDOM.createRoot(document.getElementById('root')!)
initializeWorkspace()
  .then(() => root.render(<React.StrictMode><App /></React.StrictMode>))
  .catch(error => root.render(<main className="tool-main"><div className="empty-state"><b>Wiki를 열 수 없습니다.</b><p>{error instanceof Error ? error.message : '로그인 상태를 확인해 주세요.'}</p></div></main>))
