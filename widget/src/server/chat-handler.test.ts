// @vitest-environment node
import { describe, expect, it, vi } from 'vitest';

import { createChatHandler } from './chat-handler';

const API = 'http://api-interna.local:8081';

function pedido(cuerpo: unknown, cabeceras: Record<string, string> = {}) {
  const texto = typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo);
  return new Request('http://pagina.local/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...cabeceras },
    body: texto,
  });
}

function apiResponde(cuerpo: unknown, status = 200) {
  return vi.fn(async () => new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } }));
}

const OK = { answer: 'Se paga en la oficina.', sources: [{ document: 'como-pagar.md', section: 'Dónde pagar', score: 0.9 }] };

function manejador(fetchImpl: typeof fetch, extra: Parameters<typeof createChatHandler>[0] = {}) {
  return createChatHandler({ apiUrl: API, fetchImpl, rateLimit: false, log: () => {}, ...extra });
}

describe('createChatHandler', () => {
  it('reenvía la pregunta limpia al ApiRest y devuelve solo respuesta y fuentes', async () => {
    const api = apiResponde(OK);
    const respuesta = await manejador(api as unknown as typeof fetch)(pedido({ question: '  ¿Dónde pago?  ' }));

    expect(respuesta.status).toBe(200);
    expect(await respuesta.json()).toEqual({
      answer: 'Se paga en la oficina.',
      sources: [{ document: 'como-pagar.md', section: 'Dónde pagar' }],
    });
    const [url, opciones] = api.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${API}/api/v1/chat`);
    expect(JSON.parse(opciones.body as string)).toEqual({ question: '¿Dónde pago?' });
    expect(respuesta.headers.get('cache-control')).toBe('no-store');
  });

  it.each([
    ['vacía', { question: '   ' }],
    ['demasiado larga', { question: 'a'.repeat(501) }],
    ['que no es texto', { question: 42 }],
    ['sin campo', {}],
    ['con sessionId inválido', { question: 'hola', sessionId: 'x'.repeat(65) }],
    ['JSON roto', '{"question":'],
    ['un arreglo', ['hola']],
  ])('rechaza una pregunta %s sin llamar al ApiRest', async (_nombre, cuerpo) => {
    const api = apiResponde(OK);
    const respuesta = await manejador(api as unknown as typeof fetch)(pedido(cuerpo));
    expect(respuesta.status).toBe(400);
    expect((await respuesta.json()).code).toBe('INVALID_QUESTION');
    expect(api).not.toHaveBeenCalled();
  });

  it('acepta exactamente 500 caracteres', async () => {
    const respuesta = await manejador(apiResponde(OK) as unknown as typeof fetch)(pedido({ question: 'a'.repeat(500) }));
    expect(respuesta.status).toBe(200);
  });

  it('exige application/json y limita el tamaño del cuerpo', async () => {
    const api = apiResponde(OK);
    const sinJson = new Request('http://pagina.local/api/chat', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: '{}' });
    expect((await manejador(api as unknown as typeof fetch)(sinJson)).status).toBe(400);
    expect((await manejador(api as unknown as typeof fetch)(pedido({ question: 'a'.repeat(20_000) }))).status).toBe(413);
    expect(api).not.toHaveBeenCalled();
  });

  it.each([
    [400, 400, 'INVALID_QUESTION'],
    [429, 429, 'RATE_LIMITED'],
    [503, 503, 'LLM_UNAVAILABLE'],
    [504, 504, 'LLM_TIMEOUT'],
    [500, 502, 'INTERNAL_ERROR'],
  ])('traduce el %i del ApiRest a %i %s sin detalles internos', async (estadoApi, estado, codigo) => {
    const api = apiResponde({ code: 'X', message: `fallo en ${API} con la clave sk-secreta` }, estadoApi);
    const respuesta = await manejador(api as unknown as typeof fetch)(pedido({ question: 'hola' }));
    const texto = await respuesta.text();
    expect(respuesta.status).toBe(estado);
    expect(JSON.parse(texto).code).toBe(codigo);
    expect(texto).not.toContain('api-interna');
    expect(texto).not.toContain('sk-secreta');
  });

  it('responde 504 si el ApiRest tarda y 503 si no se puede contactar', async () => {
    const tarda = vi.fn().mockRejectedValue(Object.assign(new Error('x'), { name: 'TimeoutError' }));
    const caido = vi.fn().mockRejectedValue(new TypeError('fetch failed'));
    expect((await manejador(tarda as unknown as typeof fetch)(pedido({ question: 'hola' }))).status).toBe(504);
    expect((await manejador(caido as unknown as typeof fetch)(pedido({ question: 'hola' }))).status).toBe(503);
  });

  it('responde 502 si el ApiRest devuelve una respuesta con forma inesperada', async () => {
    const respuesta = await manejador(apiResponde({ algo: 'raro' }) as unknown as typeof fetch)(pedido({ question: 'hola' }));
    expect(respuesta.status).toBe(502);
  });

  it('responde 502 sin llamar a nadie si la URL del ApiRest es inválida o trae credenciales', async () => {
    const api = apiResponde(OK);
    for (const apiUrl of ['no es una url', 'http://usuario:clave@api.local']) {
      const respuesta = await createChatHandler({ apiUrl, fetchImpl: api as unknown as typeof fetch, log: () => {} })(pedido({ question: 'hola' }));
      expect(respuesta.status).toBe(502);
    }
    expect(api).not.toHaveBeenCalled();
  });

  it('limita las preguntas por IP y vuelve a permitirlas al terminar la ventana', async () => {
    let reloj = 1_000;
    const api = apiResponde(OK);
    const post = manejador(api as unknown as typeof fetch, { rateLimit: { maxRequests: 2, windowSeconds: 60 }, now: () => reloj });
    const desde = (ip: string) => pedido({ question: 'hola' }, { 'X-Forwarded-For': ip });

    expect((await post(desde('1.1.1.1'))).status).toBe(200);
    expect((await post(desde('1.1.1.1'))).status).toBe(200);
    const bloqueada = await post(desde('1.1.1.1'));
    expect(bloqueada.status).toBe(429);
    expect(bloqueada.headers.get('retry-after')).toBe('60');
    expect((await post(desde('2.2.2.2'))).status).toBe(200); // otra IP no se afecta

    reloj += 61_000;
    expect((await post(desde('1.1.1.1'))).status).toBe(200);
  });
});
