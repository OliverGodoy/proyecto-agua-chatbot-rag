const assert = require("node:assert/strict");
const { after, before, test } = require("node:test");

const { createServer } = require("../src/server");

let server;
let address;

async function startServer(options) {
  const instance = createServer(options);
  await new Promise((resolve) => instance.listen(0, "127.0.0.1", resolve));
  return {
    server: instance,
    url: `http://127.0.0.1:${instance.address().port}`,
  };
}

async function post(url, body, headers = { "Content-Type": "application/json" }) {
  return fetch(`${url}/api/v1/chat`, {
    method: "POST",
    headers,
    body,
  });
}

before(async () => {
  ({ server, url: address } = await startServer({
    apiBaseUrl: "http://api-interno:8081",
    fetchImpl: async (url, options) => {
      assert.equal(url, "http://api-interno:8081/api/v1/chat");
      assert.equal(options.method, "POST");
      assert.deepEqual(JSON.parse(options.body), {
        question: "¿Cómo se calcula el consumo?",
        sessionId: "sesion-anonima",
      });
      return Response.json({
        answer: "La lectura actual menos la anterior.",
        sources: [{ document: "calculo-consumo.md", section: "Consumo" }],
      });
    },
  }));
});

after(async () => {
  await new Promise((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  );
});

test("valida la pregunta, reenvía al ApiRest y devuelve solo la respuesta pública", async () => {
  const response = await post(
    address,
    JSON.stringify({
      question: " ¿Cómo se calcula el consumo? ",
      sessionId: "sesion-anonima",
      ignored: "no se reenvía",
    }),
  );
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body, {
    answer: "La lectura actual menos la anterior.",
    sources: [{ document: "calculo-consumo.md", section: "Consumo" }],
  });
  assert.equal(JSON.stringify(body).includes("api-interno"), false);
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("rechaza preguntas inválidas antes de llamar al ApiRest", async () => {
  const invalidBodies = [
    "{",
    JSON.stringify({ question: "" }),
    JSON.stringify({ question: "   " }),
    JSON.stringify({ question: "a".repeat(501) }),
    JSON.stringify({ question: 42 }),
    JSON.stringify({ question: "válida", sessionId: "s".repeat(65) }),
  ];

  for (const body of invalidBodies) {
    const response = await post(address, body);
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), {
      code: "INVALID_QUESTION",
      message: "La pregunta debe tener entre 1 y 500 caracteres.",
    });
  }

  const unsupportedType = await post(address, "{}", { "Content-Type": "text/plain" });
  assert.equal(unsupportedType.status, 400);
});

test("mapea errores del ApiRest sin exponer sus detalles", async () => {
  const failing = await startServer({
    apiBaseUrl: "http://api-interno:8081",
    fetchImpl: async () =>
      Response.json(
        { code: "INTERNAL_ERROR", message: "token privado y traza interna" },
        { status: 503 },
      ),
  });

  try {
    const response = await post(
      failing.url,
      JSON.stringify({ question: "¿Dónde pago?" }),
    );
    const text = await response.text();

    assert.equal(response.status, 503);
    assert.equal(text.includes("api-interno"), false);
    assert.equal(text.includes("token privado"), false);
    assert.deepEqual(JSON.parse(text), {
      code: "LLM_UNAVAILABLE",
      message:
        "El asistente no está disponible en este momento. Intenta de nuevo en unos minutos.",
    });
  } finally {
    await new Promise((resolve, reject) =>
      failing.server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test("traduce fallas de red y timeout a mensajes amigables", async () => {
  for (const [failure, expectedStatus, expectedCode] of [
    [new TypeError("fetch failed: api-interno:8081"), 503, "LLM_UNAVAILABLE"],
    [Object.assign(new Error("timeout"), { name: "TimeoutError" }), 504, "LLM_TIMEOUT"],
  ]) {
    const failing = await startServer({
      apiBaseUrl: "http://api-interno:8081",
      fetchImpl: async () => {
        throw failure;
      },
    });

    try {
      const response = await post(
        failing.url,
        JSON.stringify({ question: "¿Dónde pago?" }),
      );
      const text = await response.text();
      assert.equal(response.status, expectedStatus);
      assert.equal(JSON.parse(text).code, expectedCode);
      assert.equal(text.includes("api-interno"), false);
      assert.equal(text.includes("timeout"), false);
    } finally {
      await new Promise((resolve, reject) =>
        failing.server.close((error) => (error ? reject(error) : resolve())),
      );
    }
  }
});

test("rechaza rutas que no pertenecen al contrato del chat", async () => {
  const response = await fetch(`${address}/internal/api`, { method: "POST" });
  assert.equal(response.status, 404);
  assert.equal(JSON.stringify(await response.json()).includes("api-interno"), false);
});
