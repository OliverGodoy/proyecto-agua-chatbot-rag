import { createRoot } from 'react-dom/client';

import { ChatWidget } from '../src';
import '../src/styles.css';

// Página de pruebas: reproduce la paleta de la consulta pública y llama al BFF por el proxy de Vite.
const estilo = document.createElement('style');
estilo.textContent = `
  :root { --oa-navy:#04263d; --oa-blue:#35a0d8; --oa-blue-light:#eaf7ff; --oa-yellow:#ffd500; }
  body { margin:0; font-family: system-ui, sans-serif; background: linear-gradient(180deg,#eaf7ff,#fff 70%); min-height:100vh; }
  header { background:#04263d; color:#fff; padding:.75rem 1rem; }
  main { max-width:48rem; margin:2rem auto; padding:0 1rem; }
`;
document.head.appendChild(estilo);

createRoot(document.getElementById('raiz')!).render(
  <>
    <header>Oficina de Agua · página de prueba</header>
    <main>
      <h1>Consulta tu estado de cuenta</h1>
      <p>Aquí iría la página pública. El botón del chat aparece abajo a la derecha.</p>
    </main>
    <ChatWidget endpoint="/api/chat" />
  </>,
);
