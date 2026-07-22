import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import basicSsl from '@vitejs/plugin-basic-ssl'

// HTTPS, because a browser gives a web page the camera only on a secure context — https, or
// localhost. The phones load this from the PC's LAN address, which is neither over plain http,
// so without this the camera is refused however the code asks. basicSsl generates a self-signed
// certificate; a phone accepts its warning once.
//
// The API is proxied rather than called across origins. The browser then talks only to this
// server, over https, and this server talks to the backend on localhost — so there is no mixed
// content (an https page may not call an http backend) and no cross-origin request to permit.
export default defineConfig({
  plugins: [react(), basicSsl()],
  server: {
    port: 5173,
    host: true, // reachable from other devices on the Wi-Fi
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
