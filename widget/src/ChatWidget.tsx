import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';

import { askChat, ChatError, MENSAJE_GENERICO, type ChatSource } from './chat-client';

/** Igual que el límite del BFF y del ApiRest. */
export const MAX_QUESTION_LENGTH = 500;

type Mensaje = {
  id: number;
  rol: 'usuario' | 'asistente' | 'error';
  texto: string;
  fuentes?: ChatSource[];
};

export type ChatWidgetProps = {
  /** URL a la que se envían las preguntas (POST). Por defecto, /api/chat (la ruta donde se monta el route handler del paquete). */
  endpoint?: string;
  title?: string;
  welcomeMessage?: string;
  placeholder?: string;
  /** Abre el chat al cargar la página. */
  defaultOpen?: boolean;
  /** Tiempo máximo de espera de una respuesta, en milisegundos. */
  timeoutMs?: number;
};

const NOMBRES_DOCUMENTOS: Record<string, string> = {
  'tarifas.md': 'Tarifas',
  'calculo-consumo.md': 'Cálculo del consumo',
  'como-pagar.md': 'Cómo pagar',
  'fugas.md': 'Fugas de agua',
};

/** Convierte el nombre de un documento fuente en un texto legible. */
export function nombreAmigable(documento: string): string {
  const conocido = NOMBRES_DOCUMENTOS[documento];
  if (conocido) return conocido;
  const base = documento.replace(/\.md$/i, '').replace(/[-_]+/g, ' ').trim();
  return base.charAt(0).toUpperCase() + base.slice(1);
}

function IconoChat() {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H11l-4.2 3.6c-.5.4-1.3.1-1.3-.6V16A2.5 2.5 0 0 1 4 13.5v-8Z" fill="currentColor" />
    </svg>
  );
}

function IconoEnviar() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3.4 20.4 21 12 3.4 3.6l.1 6.5L15 12 3.5 13.9l-.1 6.5Z" fill="currentColor" />
    </svg>
  );
}

export function ChatWidget({
  endpoint = '/api/chat',
  title = 'Asistente de la Oficina de Agua',
  welcomeMessage = 'Hola, soy el asistente virtual de la Oficina de Agua. Pregúntame sobre tarifas, consumo, pagos o fugas.',
  placeholder = 'Escribe tu pregunta completa…',
  defaultOpen = false,
  timeoutMs = 75_000,
}: ChatWidgetProps) {
  const [abierto, setAbierto] = useState(defaultOpen);
  const [mensajes, setMensajes] = useState<Mensaje[]>([]);
  const [texto, setTexto] = useState('');
  const [cargando, setCargando] = useState(false);

  const idPanel = useId();
  const siguienteId = useRef(1);
  const controlador = useRef<AbortController | null>(null);
  const finMensajes = useRef<HTMLDivElement>(null);
  const campo = useRef<HTMLTextAreaElement>(null);
  const lanzador = useRef<HTMLButtonElement>(null);

  useEffect(() => () => controlador.current?.abort(), []);

  useEffect(() => {
    finMensajes.current?.scrollIntoView?.({ block: 'end' });
  }, [mensajes, cargando, abierto]);

  useEffect(() => {
    if (abierto) campo.current?.focus();
  }, [abierto]);

  function alternar() {
    setAbierto((valor) => !valor);
  }

  function cerrar() {
    setAbierto(false);
    lanzador.current?.focus();
  }

  async function enviar(evento?: FormEvent) {
    evento?.preventDefault();
    const pregunta = texto.trim();
    if (pregunta.length === 0 || cargando) return;

    const idPregunta = siguienteId.current++;
    const idRespuesta = siguienteId.current++;
    setMensajes((actuales) => [...actuales, { id: idPregunta, rol: 'usuario', texto: pregunta }]);
    setTexto('');
    setCargando(true);

    const actual = new AbortController();
    controlador.current = actual;
    try {
      const respuesta = await askChat(endpoint, pregunta, { signal: actual.signal, timeoutMs });
      setMensajes((m) => [...m, { id: idRespuesta, rol: 'asistente', texto: respuesta.answer, fuentes: respuesta.sources }]);
    } catch (error) {
      if (actual.signal.aborted) return;
      const mensaje = error instanceof ChatError ? error.message : MENSAJE_GENERICO;
      setMensajes((m) => [...m, { id: idRespuesta, rol: 'error', texto: mensaje }]);
    } finally {
      if (!actual.signal.aborted) setCargando(false);
    }
  }

  function alPulsarTecla(evento: KeyboardEvent<HTMLTextAreaElement>) {
    if (evento.key === 'Enter' && !evento.shiftKey && !evento.nativeEvent.isComposing) {
      evento.preventDefault();
      void enviar();
    }
  }

  return (
    <div className="oa-chat">
      {abierto && (
        <section
          id={idPanel}
          className="oa-chat__panel"
          role="dialog"
          aria-label={title}
          onKeyDown={(evento) => evento.key === 'Escape' && cerrar()}
        >
          <header className="oa-chat__cabecera">
            <h2 className="oa-chat__titulo">{title}</h2>
            <button type="button" className="oa-chat__cerrar" onClick={cerrar} aria-label="Cerrar chat">
              <span aria-hidden="true">×</span>
            </button>
          </header>

          <div className="oa-chat__mensajes" role="log" aria-live="polite" aria-busy={cargando}>
            <div className="oa-chat__burbuja oa-chat__burbuja--asistente">{welcomeMessage}</div>

            {mensajes.map((mensaje) => (
              <div key={mensaje.id} className={`oa-chat__burbuja oa-chat__burbuja--${mensaje.rol}`}>
                {mensaje.texto}
                {mensaje.fuentes && mensaje.fuentes.length > 0 && (
                  <p className="oa-chat__fuentes">
                    Fuentes: {[...new Set(mensaje.fuentes.map((f) => nombreAmigable(f.document)))].join(' · ')}
                  </p>
                )}
              </div>
            ))}

            {cargando && (
              <div className="oa-chat__escribiendo" role="status" aria-label="El asistente está escribiendo">
                <span />
                <span />
                <span />
              </div>
            )}
            <div ref={finMensajes} />
          </div>

          <form className="oa-chat__formulario" onSubmit={enviar}>
            <p className="oa-chat__aviso">No escribas datos personales (nombre, teléfono o DPI).</p>
            <div className="oa-chat__fila">
              <textarea
                ref={campo}
                className="oa-chat__campo"
                value={texto}
                onChange={(evento) => setTexto(evento.target.value)}
                onKeyDown={alPulsarTecla}
                placeholder={placeholder}
                aria-label="Tu pregunta"
                maxLength={MAX_QUESTION_LENGTH}
                rows={2}
              />
              <button
                type="submit"
                className="oa-chat__enviar"
                disabled={texto.trim().length === 0 || cargando}
                aria-label="Enviar pregunta"
              >
                <IconoEnviar />
              </button>
            </div>
            {texto.length >= MAX_QUESTION_LENGTH - 100 && (
              <p className="oa-chat__contador">
                {texto.length}/{MAX_QUESTION_LENGTH}
              </p>
            )}
          </form>
        </section>
      )}

      <button
        ref={lanzador}
        type="button"
        className="oa-chat__lanzador"
        onClick={alternar}
        aria-expanded={abierto}
        aria-controls={abierto ? idPanel : undefined}
        aria-label={abierto ? 'Ocultar chat' : 'Abrir chat'}
      >
        <IconoChat />
      </button>
    </div>
  );
}
