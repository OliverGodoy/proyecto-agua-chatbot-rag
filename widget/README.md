# oficina-agua-chat-widget

Chatbot de la Oficina de Agua para la página pública (Feature 2). Es un paquete con **dos piezas**:

| Pieza | Import | Qué es |
|---|---|---|
| `ChatWidget` | `oficina-agua-chat-widget` | El botón y la ventana de chat (React). Muestra historial, indicador de escritura y errores. |
| BFF | `oficina-agua-chat-widget/server` | Un *route handler* que valida la pregunta y la reenvía al ApiRest. |

El widget habla **solo** con el BFF, y el BFF es lo único que conoce la URL del ApiRest. El navegador nunca recibe esa URL ni credenciales.

## Decisión de arquitectura: BFF como route handler reutilizable

Se evaluaron dos formas de entregar el BFF junto con el widget:

| Opción | Cómo se integra | Resultado |
|---|---|---|
| **A. Route handler dentro del paquete (elegida)** | Una línea en la página: `export { POST } from 'oficina-agua-chat-widget/server'` | Sin servicio extra que desplegar, mismo origen que la página (no hay CORS) y la URL del ApiRest vive solo en el servidor de la página |
| B. Servicio Node propio (`bff/` de este repositorio) | Otro contenedor y otro puerto | Sirve para páginas que no usan Next.js, pero exige CORS o un proxy y más configuración |

Se eligió **A** porque cumple que la página pública no necesite lógica adicional. El servicio `bff/` se conserva para hosts que no sean Next.js; ambos aplican las mismas reglas (pregunta de 1 a 500 caracteres, mismos mensajes de error, sin detalles internos).

## Requisitos

- React 18 o superior y Next.js con App Router. Probado con **Next.js 16.3.6 y React 19.3.0**.
- Node 22 o superior (para desarrollar el paquete).

## Instalación

Desde la página pública (`web/`):

```bash
npm install https://github.com/OliverGodoy/proyecto-agua-chatbot-rag/releases/download/widget-v1.0.0/oficina-agua-chat-widget-1.0.0.tgz
```

Para actualizar, se cambia la versión en la URL (`widget-v1.1.0`, etc.).

## Uso

**1. Montar el BFF** (un archivo de una línea): `src/app/api/chat/route.ts`

```ts
export { POST } from 'oficina-agua-chat-widget/server';
```

**2. Montar el widget** en `src/app/layout.tsx`, para que aparezca en todas las páginas:

```tsx
import { ChatWidget } from 'oficina-agua-chat-widget';
import 'oficina-agua-chat-widget/styles.css';

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="es">
      <body>
        {children}
        <ChatWidget />
      </body>
    </html>
  );
}
```

**3. Configurar la URL del ApiRest** en el servidor (por ejemplo, en el servicio `web` de `docker-compose.yml`):

```yaml
environment:
  CHAT_API_URL: http://chatbot-api:8081
```

Con esos tres pasos el chat funciona. `ChatWidget` usa por defecto la ruta `/api/chat`.

## Configuración

### Variables de entorno (solo servidor)

| Variable | Por defecto | Descripción |
|---|---|---|
| `CHAT_API_URL` | `http://localhost:8081` | URL del ApiRest del chatbot. Nunca llega al navegador. |
| `CHAT_TIMEOUT_MS` | `70000` | Espera máxima al ApiRest (el LLM puede tardar). |
| `CHAT_RATE_LIMIT_MAX` | `10` | Preguntas permitidas por IP en cada ventana. `0` desactiva el límite. |
| `CHAT_RATE_LIMIT_WINDOW_SECONDS` | `60` | Duración de la ventana. |

El límite por IP protege el costo del LLM. Toma la IP de `X-Forwarded-For`, así que solo es confiable si la página corre detrás de un proxy de confianza o reemplaza esa cabecera por la IP real de la conexión. Si la página ya resuelve la IP de otra forma, se puede indicar:

```ts
import { createChatHandler } from 'oficina-agua-chat-widget/server';
import { resolverIp } from '@/lib/ip-cliente';

export const POST = createChatHandler({ getClientIp: (cabeceras) => resolverIp(cabeceras) });
```

### Propiedades de `ChatWidget`

| Propiedad | Por defecto | Descripción |
|---|---|---|
| `endpoint` | `/api/chat` | Ruta del BFF. Solo se cambia si se montó en otra ruta. |
| `title` | `Asistente de la Oficina de Agua` | Título de la ventana. |
| `welcomeMessage` | Saludo del asistente | Primer mensaje. |
| `placeholder` | `Escribe tu pregunta completa…` | Texto del campo. |
| `defaultOpen` | `false` | Abre el chat al cargar. |
| `timeoutMs` | `75000` | Espera máxima en el navegador. |

### Colores

El widget usa la paleta de agua del sistema. Si la página ya define `--oa-navy`, `--oa-blue`, `--oa-blue-light`, `--oa-yellow` y `--oa-yellow-dark` (como `globals.css` de la consulta pública), los hereda. Para cambiar uno solo del chat:

```css
:root {
  --oa-chat-navy: #04263d;
  --oa-chat-yellow: #ffd500;
  --oa-chat-fuente: 'Poppins', sans-serif; /* por defecto hereda la fuente de la página */
}
```

## Comportamiento que conviene conocer

- El historial se muestra en pantalla, pero **el asistente no recuerda mensajes anteriores**: cada pregunta se responde por separado. Por eso el campo pide "preguntas completas".
- El widget avisa que no se escriban datos personales; el sistema enmascara los que detecta, pero no puede reconocer nombres.
- En celular, el chat ocupa toda la pantalla.

## Desarrollo del paquete

```bash
npm install        # usa package-lock.json
npm test           # 28 pruebas: widget y BFF
npm run dev        # página de prueba en http://localhost:5173 (envía /api/chat al ApiRest en CHAT_API_URL)
npm run build      # genera dist/
```

## Publicar una versión

1. Subir `version` en `package.json`.
2. `npm run empaquetar` (compila y genera `oficina-agua-chat-widget-X.Y.Z.tgz`).
3. En GitHub: **Releases > Draft a new release**, etiqueta `widget-vX.Y.Z`, y adjuntar el `.tgz`.
4. Avisar a la página pública la nueva URL.

El `.tgz` **no se sube al repositorio** (está en `.gitignore`): vive solo en el release.

## Problemas frecuentes

| Síntoma | Causa |
|---|---|
| El chat responde 404 | Falta `src/app/api/chat/route.ts` |
| El chat responde "no está disponible" | `CHAT_API_URL` apunta a un lugar sin el ApiRest |
| Siempre responde "demasiadas preguntas" | La IP no se está resolviendo bien detrás de un proxy; ver `getClientIp` |
| `npm install` falla con `edgesOut` al desarrollar el paquete | Borra `node_modules` y usa `npm ci` (con el `package-lock.json` incluido) |
| Error de versión de Node | Instala Node 22 LTS |
