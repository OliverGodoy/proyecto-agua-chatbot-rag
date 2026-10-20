import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// `npm run dev`: página de pruebas; /api/chat se reenvía directo al ApiRest (solo para pruebas visuales).
// `npm run build`: genera el paquete en dist/ (widget de cliente + handler de servidor).
export default defineConfig(({ command }) => ({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/chat': {
        target: process.env.CHAT_API_URL ?? 'http://localhost:8081',
        changeOrigin: true,
        rewrite: () => '/api/v1/chat',
      },
    },
  },
  build:
    command === 'build'
      ? {
          lib: {
            entry: { index: 'src/index.ts', server: 'src/server/index.ts' },
            formats: ['es', 'cjs'],
            fileName: (formato, nombre) => `${nombre}.${formato === 'es' ? 'js' : 'cjs'}`,
          },
          rollupOptions: {
            external: ['react', 'react-dom', 'react/jsx-runtime'],
            output: {
              // "use client" solo en el widget: Next.js lo trata como componente de cliente.
              // El handler de servidor NO debe llevarlo.
              banner: (chunk: { name: string }) => (chunk.name === 'server' ? '' : '"use client";'),
            },
          },
        }
      : undefined,
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    globals: false,
    css: false,
  },
}));
