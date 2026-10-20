import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ChatWidget, nombreAmigable } from './ChatWidget';

function respuestaJson(cuerpo: unknown, status = 200) {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
}

const RESPUESTA_OK = {
  answer: 'El pago se realiza en la oficina.',
  sources: [
    { document: 'como-pagar.md', section: '¿Dónde puedo pagar?' },
    { document: 'como-pagar.md', section: '¿Qué necesito para pagar?' },
    { document: 'tarifas.md' },
  ],
};

async function abrirChat() {
  const usuario = userEvent.setup();
  render(<ChatWidget endpoint="/api/test" />);
  await usuario.click(screen.getByRole('button', { name: 'Abrir chat' }));
  return usuario;
}

describe('ChatWidget', () => {
  const fetchFalso = vi.fn();

  beforeEach(() => {
    fetchFalso.mockReset();
    vi.stubGlobal('fetch', fetchFalso);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('empieza cerrado y el botón lo abre y lo cierra', async () => {
    const usuario = userEvent.setup();
    render(<ChatWidget />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Abrir chat' }));
    expect(screen.getByRole('dialog', { name: 'Asistente de la Oficina de Agua' })).toBeInTheDocument();
    expect(screen.getByText(/asistente virtual de la Oficina de Agua/)).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Cerrar chat' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('se cierra con la tecla Escape', async () => {
    const usuario = await abrirChat();
    await usuario.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('envía la pregunta al endpoint y muestra la respuesta con sus fuentes sin repetir', async () => {
    fetchFalso.mockResolvedValue(respuestaJson(RESPUESTA_OK));
    const usuario = await abrirChat();

    await usuario.type(screen.getByLabelText('Tu pregunta'), '¿Dónde pago mi recibo?');
    await usuario.click(screen.getByRole('button', { name: 'Enviar pregunta' }));

    expect(await screen.findByText(/El pago se realiza en la oficina\./)).toBeInTheDocument();
    expect(screen.getByText('¿Dónde pago mi recibo?')).toBeInTheDocument();
    expect(screen.getByText('Fuentes: Cómo pagar · Tarifas')).toBeInTheDocument();

    const [url, opciones] = fetchFalso.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/test');
    expect(opciones.method).toBe('POST');
    expect(JSON.parse(opciones.body as string)).toEqual({ question: '¿Dónde pago mi recibo?' });
  });

  it('muestra el indicador de escritura mientras espera y lo quita al responder', async () => {
    let resolver: (respuesta: Response) => void = () => {};
    fetchFalso.mockReturnValue(new Promise<Response>((resolve) => (resolver = resolve)));
    const usuario = await abrirChat();

    await usuario.type(screen.getByLabelText('Tu pregunta'), 'hola');
    await usuario.keyboard('{Enter}');

    expect(await screen.findByRole('status', { name: 'El asistente está escribiendo' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar pregunta' })).toBeDisabled();

    resolver(respuestaJson(RESPUESTA_OK));
    await screen.findByText(/El pago se realiza/);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('muestra el mensaje amigable que envía el BFF cuando hay un error', async () => {
    fetchFalso.mockResolvedValue(
      respuestaJson({ code: 'LLM_UNAVAILABLE', message: 'El asistente no está disponible en este momento.' }, 503),
    );
    const usuario = await abrirChat();

    await usuario.type(screen.getByLabelText('Tu pregunta'), 'hola');
    await usuario.click(screen.getByRole('button', { name: 'Enviar pregunta' }));

    expect(await screen.findByText('El asistente no está disponible en este momento.')).toBeInTheDocument();
  });

  it('muestra un mensaje de conexión si la red falla', async () => {
    fetchFalso.mockRejectedValue(new TypeError('Failed to fetch'));
    const usuario = await abrirChat();

    await usuario.type(screen.getByLabelText('Tu pregunta'), 'hola');
    await usuario.click(screen.getByRole('button', { name: 'Enviar pregunta' }));

    expect(await screen.findByText(/No se pudo conectar con el asistente/)).toBeInTheDocument();
  });

  it('no deja enviar preguntas vacías y limita el largo a 500 caracteres', async () => {
    const usuario = await abrirChat();
    const enviar = screen.getByRole('button', { name: 'Enviar pregunta' });
    expect(enviar).toBeDisabled();

    await usuario.type(screen.getByLabelText('Tu pregunta'), '   ');
    expect(enviar).toBeDisabled();
    expect(screen.getByLabelText('Tu pregunta')).toHaveAttribute('maxlength', '500');
    expect(fetchFalso).not.toHaveBeenCalled();
  });

  it('muestra el aviso de no escribir datos personales', async () => {
    await abrirChat();
    await waitFor(() => expect(screen.getByText(/No escribas datos personales/)).toBeInTheDocument());
  });
});

describe('nombreAmigable', () => {
  it('traduce los documentos conocidos y humaniza los demás', () => {
    expect(nombreAmigable('calculo-consumo.md')).toBe('Cálculo del consumo');
    expect(nombreAmigable('horarios-oficina.md')).toBe('Horarios oficina');
  });
});
