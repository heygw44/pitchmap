import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // docker compose와 백엔드가 읽는 저장소 루트의 .env를 프론트엔드도 함께 읽어서, 개발자가 .env 하나만 채우면 된다.
  // Vite는 VITE_ 접두사가 붙은 변수만 브라우저 코드에 내보내므로 DB 비밀번호 같은 다른 값은 번들에 들어가지 않는다.
  envDir: '..',
  server: {
    port: 5173,
    strictPort: true,
    // 개발 서버가 /api 요청을 백엔드로 넘겨서 브라우저는 같은 출처로만 요청한다.
    // 운영에서는 Caddy가 /api를 백엔드로 넘기므로, 개발에서도 세션 쿠키와 CSRF 토큰이 운영과 똑같이 동작하고 CORS 설정이 필요 없다.
    proxy: {
      '/api': { target: 'http://localhost:8080' },
    },
  },
});
