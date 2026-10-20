export type ChatSource = { document: string; section?: string };
export type ChatReply = { answer: string; sources: ChatSource[] };

/** Error con un mensaje listo para mostrarse al usuario (nunca detalles internos). */
export class ChatError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ChatError';
  }
}

export const MENSAJE_GENERICO = 'No se pudo obtener la respuesta. Intenta de nuevo en unos minutos.';
const MENSAJE_SIN_CONEXION = 'No se pudo conectar con el asistente. Revisa tu conexión e intenta de nuevo.';
const MENSAJE_TIEMPO = 'El asistente tardó demasiado en responder. Intenta de nuevo.';
const MENSAJE_LIMITE = 'Hay demasiadas preguntas. Espera un momento e intenta de nuevo.';

function esRespuestaValida(cuerpo: unknown): cuerpo is ChatReply {
  const c = cuerpo as ChatReply | null;
  return (
    typeof c === 'object' &&
    c !== null &&
    typeof c.answer === 'string' &&
    Array.isArray(c.sources) &&
    c.sources.every((s) => typeof s === 'object' && s !== null && typeof s.document === 'string')
  );
}

async function mensajeDeError(respuesta: Response): Promise<string> {
  try {
    const cuerpo = (await respuesta.json()) as { message?: unknown };
    if (typeof cuerpo.message === 'string' && cuerpo.message.length > 0 && cuerpo.message.length <= 300) {
      return cuerpo.message;
    }
  } catch {
    // El cuerpo no era JSON: se usa un mensaje según el código HTTP.
  }
  return respuesta.status === 429 ? MENSAJE_LIMITE : MENSAJE_GENERICO;
}

type Opciones = { signal?: AbortSignal; timeoutMs: number };

/** Envía la pregunta al endpoint (BFF) y devuelve la respuesta o lanza un ChatError. */
export async function askChat(endpoint: string, question: string, { signal, timeoutMs }: Opciones): Promise<ChatReply> {
  const controlador = new AbortController();
  let vencido = false;
  const temporizador = setTimeout(() => {
    vencido = true;
    controlador.abort();
  }, timeoutMs);
  signal?.addEventListener('abort', () => controlador.abort(), { once: true });

  try {
    let respuesta: Response;
    try {
      respuesta = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ question }),
        signal: controlador.signal,
      });
    } catch (error) {
      if (signal?.aborted) throw error; // lo canceló el componente al desmontarse
      throw new ChatError(vencido ? MENSAJE_TIEMPO : MENSAJE_SIN_CONEXION);
    }

    if (!respuesta.ok) throw new ChatError(await mensajeDeError(respuesta));

    let cuerpo: unknown;
    try {
      cuerpo = await respuesta.json();
    } catch {
      throw new ChatError(MENSAJE_GENERICO);
    }
    if (!esRespuestaValida(cuerpo)) throw new ChatError(MENSAJE_GENERICO);
    return cuerpo;
  } finally {
    clearTimeout(temporizador);
  }
}
