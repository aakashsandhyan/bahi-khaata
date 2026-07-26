import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { existsSync, readFileSync } from 'node:fs'

// HTTPS with a certificate a phone can actually trust, made by mkcert. A self-signed cert let
// the page load but Chrome still refused the camera on it, silently — the camera needs a
// certificate signed by an authority the device trusts. mkcert is that authority; its root is
// installed on each phone once (downloadable at /rootCA.pem while the server runs), and then
// this cert is trusted and the camera prompts normally.
//
// The certificate is looked for at a fixed path and used only if it is there. Absent — a fresh
// checkout, or a machine that has not run mkcert — the server falls back to plain http: the app
// still works for typed and Bluetooth scanning, only the camera needs the trusted https. So a
// missing cert degrades a feature rather than breaking the server.
//
// If the PC's LAN address changes, regenerate the cert for the new one and update these paths.
const CERT = './certs/lan.pem'
const KEY = './certs/lan-key.pem'
const https =
  existsSync(CERT) && existsSync(KEY)
    ? { cert: readFileSync(CERT), key: readFileSync(KEY) }
    : undefined

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true, // reachable from other devices on the Wi-Fi
    https, // trusted https when the cert is present; plain http otherwise
    // Allow an ngrok tunnel host through the dev server's host check, for temporary public
    // access. The tunnel itself is gated by basic auth (ngrok --basic-auth); nothing here is
    // a substitute for that — the app has no login of its own.
    allowedHosts: ['.ngrok-free.app', '.ngrok.app'],
    // The API is proxied so the browser stays on one origin — no mixed content, no cross-origin.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Strip the browser's Origin before forwarding. To the browser these calls are
        // same-origin — page and api share this https address — but a POST carries an Origin
        // header even so, and forwarding it made the backend treat a same-origin call as a
        // cross-origin one and refuse it. With no Origin, the backend sees an ordinary request,
        // which is what it is. GETs never showed this: a same-origin GET sends no Origin at all.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
    },
  },
})
