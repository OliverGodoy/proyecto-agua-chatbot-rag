/**
 * BFF del chatbot como route handler reutilizable (Web standard Request/Response).
 *
 * En Next.js basta una línea en app/api/chat/route.ts:
 *   export { POST } from 'oficina-agua-chat-widget/server';
 *
 * Valida la pregunta, la reenvía al ApiRest y traduce cualquier fallo a un mensaje amigable.
 * La URL del ApiRest (CHAT_API_URL) solo existe en el servidor: nunca llega al navegador.
 */

const RUTA_CHAT = '/api/v1/chat';
const MAX_BYTES_CUERPO = 16 * 1024;
const MAX_PREGUNTA = 500;
const MAX_SESION = 64;

type ErrorHttp = { status: number; body: { code: string; message: string } };

const ERRORES = {
  preguntaInvalida: {
    status: 400,
    body: { code: 'INVALID_QUESTION', message: 'La pregunta debe tener entre 1 y 500 caracteres.' },
  },
  demasiadasPreguntas: {
    status: 429,
    body: { code: 'RATE_LIMITED', message: 'Hay demasiadas preguntas. Espera un momento e intenta de nuevo.' },
  },
  asistenteNoDisponible: {
    status: 503,
    body: { code: 'LLM_UNAVAILABLE', message: 'El asistente no está disponible en este momento. Intenta de nuevo en unos minutos.' },
  },
  asistenteTardo: {
    status: 504,
    body: { code: 'LLM_TIMEOUT', message: 'El asistente tardó demasiado en responder. Intenta de nuevo.' },
  },
  interno: {
    status: 502,
    body: { code: 'INTERNAL_ERROR', message: 'No se pudo completar la solicitud. Intenta de nuevo más tarde.' },
  },
} satisfies Record<string, ErrorHttp>;

export type ChatHandlerOptions = {
  /** URL base del ApiRest del chatbot. Por defecto, la variable de entorno CHAT_API_URL. */
  apiUrl?: string;
  /** Espera máxima al ApiRest. Por defecto, CHAT_TIMEOUT_MS o 70 000 ms. */
  timeoutMs?: number;
  /** Límite de preguntas por IP. Por defecto, 10 por minuto (CHAT_RATE_LIMIT_MAX / CHAT_RATE_LIMIT_WINDOW_SECONDS). */
  rateLimit?: { maxRequests: number; windowSeconds: number } | false;
  /** Cómo obtener la IP del cliente. Por defecto, X-Forwarded-For / X-Real-IP. */
  getClientIp?: (headers: Headers) => string;
  /** Solo para pruebas. */
  fetchImpl?: typeof fetch;
  now?: () => number;
  log?: (mensaje: string) => void;
};

function respuestaJson(status: number, cuerpo: unknown, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: {
      'Cache-Control': 'no-store',
      'Content-Type': 'application/json; charset=utf-8',
      'X-Content-Type-Options': 'nosniff',
      ...extra,
    },
  });
}

const enviarError = (e: ErrorHttp, extra?: Record<string, string>) => respuestaJson(e.status, e.body, extra);

function destinoDe(apiUrl: string): string {
  const url = new URL(apiUrl);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
    throw new Error('CHAT_API_URL debe ser una URL HTTP(S) sin credenciales');
  }
  url.pathname = `${url.pathname.replace(/\/+$/, '')}${RUTA_CHAT}`;
  url.search = '';
  url.hash = '';
  return url.toString();
}

function leerPregunta(cuerpo: unknown): { question: string; sessionId?: string } | null {
  if (typeof cuerpo !== 'object' || cuerpo === null || Array.isArray(cuerpo)) return null;
  const { question, sessionId } = cuerpo as { question?: unknown; sessionId?: unknown };
  if (typeof question !== 'string' || question.trim().length === 0 || question.length > MAX_PREGUNTA) return null;
  if (sessionId !== undefined && (typeof sessionId !== 'string' || sessionId.length > MAX_SESION)) return null;
  return sessionId === undefined ? { question: question.trim() } : { question: question.trim(), sessionId };
}

type RespuestaApi = { answer: string; sources: { document: string; section?: string }[] };

function esRespuestaValida(cuerpo: unknown): cuerpo is RespuestaApi {
  const c = cuerpo as RespuestaApi | null;
  return (
    typeof c === 'object' &&
    c !== null &&
    typeof c.answer === 'string' &&
    Array.isArray(c.sources) &&
    c.sources.every(
      (s) => typeof s === 'object' && s !== null && typeof s.document === 'string' && (s.section === undefined || typeof s.section === 'string'),
    )
  );
}

function errorPorEstadoDelApi(status: number): ErrorHttp {
  switch (status) {
    case 400:
      return ERRORES.preguntaInvalida;
    case 429:
      return ERRORES.demasiadasPreguntas;
    case 503:
      return ERRORES.asistenteNoDisponible;
    case 504:
      return ERRORES.asistenteTardo;
    default:
      return ERRORES.interno;
  }
}

/** Límite de ventana fija por IP, en memoria. */
class LimitePorIp {
  private readonly ventanas = new Map<string, { inicio: number; cuenta: number }>();

  constructor(
    private readonly maximo: number,
    private readonly ventanaMs: number,
    private readonly ahora: () => number,
  ) {}

  /** Devuelve 0 si puede continuar, o los segundos que debe esperar. */
  usar(ip: string): number {
    const t = this.ahora();
    if (this.ventanas.size > 5000) {
      for (const [clave, v] of this.ventanas) if (t - v.inicio >= this.ventanaMs) this.ventanas.delete(clave);
    }
    const actual = this.ventanas.get(ip);
    if (!actual || t - actual.inicio >= this.ventanaMs) {
      this.ventanas.set(ip, { inicio: t, cuenta: 1 });
      return 0;
    }
    if (actual.cuenta >= this.maximo) return Math.max(1, Math.ceil((actual.inicio + this.ventanaMs - t) / 1000));
    actual.cuenta += 1;
    return 0;
  }
}

function entero(valor: string | undefined, porDefecto: number): number {
  const n = Number(valor);
  return valor !== undefined && valor !== '' && Number.isInteger(n) && n >= 0 ? n : porDefecto;
}

function ipPorDefecto(headers: Headers): string {
  return headers.get('x-forwarded-for')?.split(',')[0]?.trim() || headers.get('x-real-ip')?.trim() || 'desconocida';
}

export function createChatHandler(opciones: ChatHandlerOptions = {}): (request: Request) => Promise<Response> {
  const fetchImpl = opciones.fetchImpl ?? ((...args: Parameters<typeof fetch>) => fetch(...args));
  const ahora = opciones.now ?? Date.now;
  const registrar = opciones.log ?? ((mensaje: string) => console.error(`[chat] ${mensaje}`));
  const obtenerIp = opciones.getClientIp ?? ipPorDefecto;
  let limite: LimitePorIp | null | undefined; // se crea en la primera petición (lee el entorno en ejecución)

  function limiteActivo(): LimitePorIp | null {
    if (limite !== undefined) return limite;
    const config =
      opciones.rateLimit === undefined
        ? {
            maxRequests: entero(process.env.CHAT_RATE_LIMIT_MAX, 10),
            windowSeconds: entero(process.env.CHAT_RATE_LIMIT_WINDOW_SECONDS, 60),
          }
        : opciones.rateLimit;
    limite = config && config.maxRequests > 0 && config.windowSeconds > 0
      ? new LimitePorIp(config.maxRequests, config.windowSeconds * 1000, ahora)
      : null;
    return limite;
  }

  return async function POST(request: Request): Promise<Response> {
    let destino: string;
    try {
      destino = destinoDe(opciones.apiUrl ?? process.env.CHAT_API_URL ?? 'http://localhost:8081');
    } catch {
      registrar('CHAT_API_URL no es una URL HTTP(S) válida');
      return enviarError(ERRORES.interno);
    }
    const timeoutMs = opciones.timeoutMs ?? entero(process.env.CHAT_TIMEOUT_MS, 70_000);

    if (!/^application\/json(?:\s*;|$)/i.test(request.headers.get('content-type') ?? '')) {
      return enviarError(ERRORES.preguntaInvalida);
    }
    if (Number(request.headers.get('content-length') ?? 0) > MAX_BYTES_CUERPO) {
      return respuestaJson(413, ERRORES.preguntaInvalida.body);
    }

    let carga: { question: string; sessionId?: string } | null;
    try {
      const texto = await request.text();
      if (new TextEncoder().encode(texto).length > MAX_BYTES_CUERPO) {
        return respuestaJson(413, ERRORES.preguntaInvalida.body);
      }
      carga = leerPregunta(JSON.parse(texto));
    } catch {
      return enviarError(ERRORES.preguntaInvalida);
    }
    if (carga === null) return enviarError(ERRORES.preguntaInvalida);

    const espera = limiteActivo()?.usar(obtenerIp(request.headers)) ?? 0;
    if (espera > 0) return enviarError(ERRORES.demasiadasPreguntas, { 'Retry-After': String(espera) });

    let respuestaApi: Response;
    try {
      respuestaApi = await fetchImpl(destino, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify(carga),
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (error) {
      const tardo = error instanceof Error && error.name === 'TimeoutError';
      registrar(tardo ? 'El ApiRest no respondió a tiempo' : 'No se pudo contactar al ApiRest');
      return enviarError(tardo ? ERRORES.asistenteTardo : ERRORES.asistenteNoDisponible);
    }

    if (!respuestaApi.ok) {
      registrar(`El ApiRest respondió con el código HTTP ${respuestaApi.status}`);
      return enviarError(errorPorEstadoDelApi(respuestaApi.status));
    }

    let resultado: unknown;
    try {
      resultado = await respuestaApi.json();
    } catch {
      return enviarError(ERRORES.interno);
    }
    if (!esRespuestaValida(resultado)) return enviarError(ERRORES.interno);

    return respuestaJson(200, {
      answer: resultado.answer,
      sources: resultado.sources.map((fuente) => ({
        document: fuente.document,
        ...(fuente.section === undefined ? {} : { section: fuente.section }),
      })),
    });
  };
}

/** Handler listo para usar: lee CHAT_API_URL del entorno en cada petición. */
export const POST = createChatHandler();
