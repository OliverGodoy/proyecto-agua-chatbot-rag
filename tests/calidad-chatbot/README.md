# Pruebas de calidad del chatbot (HU-39)

Conjunto de preguntas para verificar que el chatbot responde con base en el contenido real de `content/` y no inventa información.

## Qué se prueba

| Tipo | Casos | Qué se verifica |
|---|---|---|
| Dominio | D01 a D16 | Respuesta correcta según el contenido (montos, pasos y datos clave) y documento de origen en las fuentes. Incluye un cálculo que no aparece como ejemplo (D08). |
| Fuera de dominio | F01 a F05 | Rechazo con cortesía. F04 y F05 preguntan datos cercanos al tema que el contenido no tiene (horario y teléfono), para comprobar que no los inventa. |
| Seguridad | S01 y S02 | No obedece intentos de cambiar sus reglas y no repite datos personales. |
| Validación | V01 | Una pregunta vacía responde 400 con `INVALID_QUESTION`. |

Las preguntas, las respuestas esperadas y los patrones de verificación están en `casos.json`. Los patrones se comparan contra la respuesta en minúsculas y sin tildes, así que no dependen de la redacción exacta del LLM.

## Cómo ejecutarlas

1. Levantar las bases de datos desde la raíz: `docker compose up -d`
2. Cargar los embeddings, desde `api`: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--ingesta"`
3. Iniciar el ApiRest, desde `api`: `.\mvnw.cmd spring-boot:run`
4. En otra ventana, desde la raíz: `.\tests\calidad-chatbot\ejecutar-pruebas.ps1`

## Prueba de estabilidad

Un LLM no siempre responde igual a la misma pregunta, así que una sola respuesta correcta no demuestra que el chatbot sea confiable. Con el parámetro `-Repeticiones` cada pregunta se envía varias veces y el caso solo se aprueba si todas las respuestas son correctas:

`.\tests\calidad-chatbot\ejecutar-pruebas.ps1 -Repeticiones 3`

El reporte muestra cuántos intentos fueron correctos en cada caso (por ejemplo, 3/3).

## Prueba a través del BFF

Para probar el mismo camino que usa el widget, con el BFF corriendo:

`.\tests\calidad-chatbot\ejecutar-pruebas.ps1 -Url http://localhost:3000/api/v1/chat`

Si PowerShell bloquea el script por la política de ejecución, se puede ejecutar así:
`powershell -ExecutionPolicy Bypass -File .\tests\calidad-chatbot\ejecutar-pruebas.ps1`

## Evidencia

Cada ejecución guarda un reporte en `resultados/` con el resumen por tipo, el tiempo promedio de respuesta y, para cada caso, los intentos correctos, la respuesta esperada, la respuesta obtenida, las fuentes y el resultado.
