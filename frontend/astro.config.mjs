import { defineConfig } from 'astro/config';
import tailwind from '@astrojs/tailwind';

export default defineConfig({
  integrations: [tailwind()],
  output: 'static',
  devToolbar: { enabled: false },
  vite: {
    server: {
      proxy: {
        '/api': { target: 'http://localhost:8081', changeOrigin: true },
        '/ws':  { target: 'ws://localhost:8081',  ws: true, changeOrigin: true }
      }
    }
  }
});
