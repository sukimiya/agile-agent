import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import HomePage from './HomePage.tsx'
import { isHomePath } from './homeRoute.ts'
import { subscribeSystemTheme } from './theme.ts'

subscribeSystemTheme()

const Page = isHomePath(window.location.pathname) ? HomePage : App

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Page />
  </StrictMode>,
)
