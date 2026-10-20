# Oficina de Agua — Feature 2: Chatbot con RAG

Fase 2 · Desarrollo Web 2026 · Universidad Mariano Gálvez

## Descripción

Este repositorio contiene la Feature 2 del sistema de la Oficina de Agua: un
chatbot en español que responde preguntas sobre los servicios de agua usando
contenido documental de la oficina. La solución incluye una API REST con RAG,
un BFF que protege la API del navegador, un widget React reutilizable y la
infraestructura local para búsqueda vectorial y registro de interacciones.

El asistente responde con información de los documentos disponibles. Si no
encuentra contexto suficientemente relevante, responde que no tiene esa
información y recomienda comunicarse con la oficina; no consulta al LLM en ese
caso.

## Componentes

| Directorio | Responsabilidad |
|---|---|
| `api/` | API REST Java con Spring Boot, pipeline RAG, ingesta de documentos, clientes de embeddings/LLM y registro de interacciones. |
| `content/` | Documentación de dominio que alimenta las respuestas: tarifas, cálculo de consumo, pagos y fugas. |
| `db/` | Inicialización de pgvector y creación de la tabla de DynamoDB Local. |
| `bff/` | BFF Node independiente para integraciones que no usen el route handler del paquete. |
| `widget/` | Paquete React `oficina-agua-chat-widget`, route handler reutilizable de Next.js App Router y playground. |
| `docker-compose.yml` | Servicios de desarrollo: PostgreSQL con pgvector, DynamoDB Local, inicialización de la tabla y Swagger UI. No ejecuta la API Java ni el BFF. |
| `preguntas.http` | Solicitudes de ejemplo para probar el endpoint del chat desde un cliente HTTP compatible. |

## Arquitectura y flujo de una pregunta

1. El widget envía la pregunta a `/api/chat` en el mismo sitio web. El BFF
   valida tipo, formato y longitud, y aplica un límite de solicitudes por IP.
2. El BFF reenvía la pregunta por HTTP al endpoint `POST /api/v1/chat` de la
   API. La URL del ApiRest solo está configurada en el servidor.
3. La API genera un embedding de la pregunta y busca los fragmentos más
   similares en PostgreSQL/pgvector usando distancia coseno.
4. Si la relevancia supera el umbral configurado, la API construye un prompt
   con los fragmentos recuperados y solicita una respuesta al proveedor LLM.
   Devuelve la respuesta junto con sus documentos/secciones fuente.
5. Si no hay fragmentos suficientemente relevantes, devuelve un mensaje de
   falta de información sin llamar al LLM.
6. La pregunta, respuesta y fuentes se registran en DynamoDB Local. El registro
   es secundario: un fallo al guardar no impide responder al usuario.

El prompt instruye al LLM a responder solo con el contexto recuperado, no
inventar datos, no pedir datos personales y rechazar instrucciones ajenas al
dominio del servicio de agua. La API y ambos BFF validan las preguntas y
traducen errores a mensajes públicos sin exponer detalles internos.

## Tecnologías

- **API:** Java 21, Spring Boot, Maven, Spring MVC, validación y OpenFeign.
- **Recuperación:** PostgreSQL 17 con extensión pgvector, embeddings de OpenAI
  u Ollama y búsqueda por similitud coseno con índice HNSW.
- **Generación:** API de chat compatible con OpenAI; Ollama puede usarse con un
  endpoint compatible.
- **Registro:** DynamoDB Local durante el desarrollo.
- **BFF:** Node.js para el servicio independiente; el handler incluido en el
  paquete widget usa APIs estándar `Request`/`Response` y funciona con Next.js
  App Router.
- **Widget:** React 18 o superior; el paquete se desarrolla con Node.js 22 o
  superior.

## Requisitos

- Docker con Docker Compose.
- Java 21 para compilar y ejecutar la API.
- Una clave de proveedor con acceso al modelo de embeddings y al LLM, o una
  instancia local de Ollama configurada para ambos.
- Node.js según el componente: 22 o superior para desarrollar/empaquetar el
  widget; 20.6 o superior para el BFF independiente.

## Inicio local

### 1. Configurar e iniciar las dependencias

Desde la raíz del repositorio, crea el archivo local de variables para Docker
Compose e inicia las bases de datos y Swagger UI:

```powershell
Copy-Item .env.example .env
docker compose up -d
```

Compose inicia PostgreSQL/pgvector en el puerto `5432`, DynamoDB Local en el
`8000` y Swagger UI en el `8082`. PostgreSQL crea la tabla e índice vectorial
al inicializar su volumen por primera vez. La dimensión configurada para los
embeddings debe coincidir con la dimensión de esa tabla; cambiarla después de
crear el volumen requiere migrar o recrear la base de datos.

### 2. Configurar la API

La API carga propiedades de `api/secrets.properties` (o `secrets.properties`
en el directorio de ejecución). Crea el archivo local a partir de la plantilla:

```powershell
Copy-Item api/secrets.properties.example api/secrets.properties
```

Configura ahí las propiedades del proveedor que vayas a usar, por ejemplo:

```properties
chatbot.llm.api-key=TU_CLAVE
chatbot.embedding.api-key=TU_CLAVE
```

Se pueden usar claves distintas para cada propiedad. También se pueden
configurar mediante variables de entorno `LLM_API_KEY` y
`EMBEDDING_API_KEY`. **Spring Boot no carga `.env` automáticamente**: el `.env`
de la raíz lo usa Docker Compose; para la API Java usa `secrets.properties` o
configura las variables en el entorno del proceso. No incluyas claves reales
en Git.

Los valores predeterminados de la API apuntan a los servicios locales de
Compose. Para proveedor OpenAI, el embedding predeterminado es
`text-embedding-3-small` (1536 dimensiones). Para Ollama se puede configurar
`EMBEDDING_PROVIDER=ollama`, `EMBEDDING_MODEL` y, si hace falta,
`EMBEDDING_URL`; la dimensión debe corresponder al modelo.

### 3. Ingestar el contenido y arrancar el chat

Ejecuta desde `api/`, para que `../content` resuelva a los documentos del
repositorio:

```powershell
cd api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--ingesta"
```

La ingesta divide los Markdown por secciones y fragmentos, calcula embeddings
por lotes y persiste los nuevos en pgvector. Es idempotente: no duplica los
fragmentos sin cambios y elimina los obsoletos. Vuelve a ejecutarla cuando
cambie el contenido. Al terminar, inicia la API normalmente:

```powershell
.\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8081`; la interfaz Swagger UI está en
`http://localhost:8082`. El contrato de la API está en
[`api/src/main/resources/openapi/chatbot-api.yaml`](api/src/main/resources/openapi/chatbot-api.yaml).

## API de chat

**`POST /api/v1/chat`** recibe JSON con una pregunta entre 1 y 500 caracteres;
`sessionId` es opcional y admite hasta 64 caracteres:

```json
{
  "question": "¿Cómo se calcula mi consumo de agua?"
}
```

Respuesta:

```json
{
  "answer": "La respuesta del asistente.",
  "sources": [
    {
      "document": "calculo-consumo.md",
      "section": "Lectura del medidor"
    }
  ]
}
```

Los errores usan `{ "code": "...", "message": "..." }`. Entre los códigos
documentados están `INVALID_QUESTION` (400), `RATE_LIMITED` (429),
`LLM_UNAVAILABLE` (503) y `LLM_TIMEOUT` (504). Una pregunta válida sin
información relevante devuelve `200`, un mensaje de falta de información y
`sources: []`.

## Datos y privacidad

Los documentos Markdown en `content/` son la fuente de conocimiento del
asistente. Agregar o cambiar documentos requiere volver a ejecutar la ingesta.
Antes de guardar preguntas y respuestas en DynamoDB, se enmascaran correos,
NIT y secuencias numéricas largas detectables. Esto no identifica nombres de
personas de manera confiable; no se deben ingresar datos personales.

## Pruebas

Desde `api/`, ejecuta las pruebas Java:

```powershell
.\mvnw.cmd test
```

El widget y su handler incluyen **28 pruebas**:

```powershell
cd widget
npm ci
npm test
npm run typecheck
```

El BFF Node independiente tiene pruebas propias:

```powershell
cd bff
npm test
```

## Documentación por componente

- [BFF Node independiente](bff/README.md)
- [Paquete widget, handler de Next.js y publicación](widget/README.md)
- [Contrato OpenAPI de la API del chatbot](api/src/main/resources/openapi/chatbot-api.yaml)
- [Contrato OpenAPI del proveedor LLM](api/src/main/resources/openapi/llm-chat.yaml)

## HU-36 y HU-37: BFF y widget reutilizable

El chat se entrega como el paquete npm `oficina-agua-chat-widget`, que incluye
el componente React y el BFF. El navegador solo hace solicitudes a la ruta del
BFF en la página pública; las credenciales y la URL interna del ApiRest se
mantienen en el servidor.

### Decisión de arquitectura

El BFF principal se distribuye como un **route handler reutilizable** para
Next.js App Router (`oficina-agua-chat-widget/server`). La página pública lo
monta con una exportación de una línea. Así no hace falta desplegar otro
servicio, la comunicación navegador-BFF queda en el mismo origen y la URL
interna del ApiRest solo se configura en el servidor de la página.

El servicio Node independiente de `bff/` se conserva para integraciones que no
usen Next.js. Ambos BFF validan las solicitudes y traducen los errores del
ApiRest a respuestas públicas sin filtrar detalles internos.

### Instalar e integrar en la página pública

Después de publicar el paquete en un GitHub Release, desde el proyecto Next.js:

```bash
npm install https://github.com/OliverGodoy/proyecto-agua-chatbot-rag/releases/download/widget-v1.0.0/oficina-agua-chat-widget-1.0.0.tgz
```

1. Crear `src/app/api/chat/route.ts`:

   ```ts
   export { POST } from 'oficina-agua-chat-widget/server';
   ```

2. Montar el widget y sus estilos en `src/app/layout.tsx`:

   ```tsx
   import type { ReactNode } from 'react';
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

3. Configurar `CHAT_API_URL` solo en el entorno servidor del sitio. Por
   ejemplo, si ambos servicios comparten una red de Docker:

   ```yaml
   services:
     web:
       environment:
         CHAT_API_URL: http://chatbot-api:8081
   ```

El widget usa `/api/chat` por defecto; no necesita configuración adicional del
navegador. El componente ofrece historial visual, indicador de escritura,
errores amigables, diseño adaptable a móvil y la paleta de agua del sistema.
La documentación completa de propiedades y opciones está en
[`widget/README.md`](widget/README.md).

### Red y coordinación de despliegue

- El contenedor `web` debe poder alcanzar el ApiRest del chatbot. Si el ApiRest
  corre directamente en el host, se puede usar `http://host.docker.internal:8081`;
  si comparten una red de Docker, es preferible usar el nombre del servicio.
- Si ambas Features corren en la misma máquina, sus ApiRest no pueden usar a la
  vez el puerto `8081`. La API del sitio consumidor debe usar su variable
  `API_PORT` para cambiar de puerto. `CHAT_API_URL` debe apuntar al puerto
  efectivo del ApiRest del chatbot.
- El límite por IP del route handler usa `X-Forwarded-For` o `X-Real-IP`. Si hay
  un proxy, este debe reemplazar esas cabeceras con la IP real. Para una
  resolución propia se puede configurar `getClientIp`; véase
  [`widget/README.md`](widget/README.md).

### Pruebas y desarrollo

El paquete widget/BFF tiene **28 pruebas**:

```powershell
cd widget
npm ci
npm test
npm run typecheck
```

El BFF Node independiente tiene su propia suite:

```powershell
cd bff
npm test
```

Para probar el widget localmente, ejecuta `npm run dev` desde `widget/`; el
playground usa la ruta `/api/chat` y reenvía al ApiRest configurado por
`CHAT_API_URL`.

### Publicación y consumo

Después de fusionar los cambios, desde `widget/`:

```powershell
npm run empaquetar
```

En GitHub, crea un release con la etiqueta `widget-v1.0.0` y adjunta el archivo
`.tgz` generado. **No se agrega el `.tgz` al repositorio.** La instalación para
el sitio consumidor se hace usando la URL de ese asset, como en el ejemplo
anterior. Al equipo que integra el sitio público hay que indicarle que instale
esa URL, agregue el route handler, monte el widget en el layout y defina
`CHAT_API_URL` en el servicio `web`.

## Equipo

| Nombre completo | Carnet | Correo electrónico | 
|---|---|---|
| Yourgen Kraven Thommel Arevalo | 0905-23-14003 | ythommela@miumg.edu.gt |
| Karen Yamileth Jiménez Galicia | 0905-23-7626 | kjimenezg6@miumg.edu.gt |
| Enner Osvaldo Godoy Ramirez | 0905-23-15908 | egodoyr2@miumg.edu.gt |
| Oliver Isaac Godoy Salguero | 0905-23-10816 | ogodoys@miumg.edu.gt |
