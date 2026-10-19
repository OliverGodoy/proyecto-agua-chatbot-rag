# BFF del chatbot

El BFF expone `POST /api/v1/chat` para el navegador y reenvía la pregunta al
ApiRest desde el servidor. La URL del ApiRest se configura con `API_REST_URL`;
no se incluye en las respuestas del BFF ni se entrega al cliente.

## Ejecución local

1. Inicia el ApiRest y sus dependencias locales.
2. Desde esta carpeta, crea la configuración local:

   ```powershell
   Copy-Item .env.example .env
   ```

3. Ajusta `API_REST_URL` en `.env` si el ApiRest escucha en otra dirección.
4. Inicia el BFF:

   ```powershell
   npm start
   ```

El BFF queda disponible en `http://localhost:3000`. Envía solicitudes JSON con
una pregunta de 1 a 500 caracteres; `sessionId` es opcional y admite hasta 64
caracteres.

```json
{
  "question": "¿Cómo se calcula mi consumo de agua?"
}
```

`BFF_TIMEOUT_MS` configura el tiempo máximo de espera al ApiRest (70 segundos
por defecto). Las fallas del backend se traducen a errores amigables y no se
reenvían detalles internos.

## Pruebas

```powershell
npm test
```
