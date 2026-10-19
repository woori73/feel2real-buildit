import React from 'react'
import ReactDOM from 'react-dom/client'
import GrammarItemsShell from './components/GrammarItemsShell'
import './index.css'
import './styles/generator.css'
import './styles/workspace.css'
import './styles/wiki-ink.css'
import './styles/grammar-items.css'

ReactDOM.createRoot(document.getElementById('root')!)
  .render(<React.StrictMode><GrammarItemsShell /></React.StrictMode>)
