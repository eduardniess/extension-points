import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig ({
  plugins: [react()],
  build: {
    minify: 'terser',
    terserOptions: {
      mangle: {
        reserved: ['initExtensionPoint'],
      },
    },
    rollupOptions: {
      output: {
        //manualChunks: {},
        inlineDynamicImports : true,
        assetFileNames: "assets/[name].[ext]",
        entryFileNames: `assets/[name].js`,
        chunkFileNames: `assets/[name].js`,
      },
    },
  },
})
