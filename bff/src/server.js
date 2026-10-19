const http = require("node:http");

const CHAT_PATH = "/api/v1/chat";
const MAX_BODY_BYTES = 16 * 1024;
const MAX_QUESTION_LENGTH = 500;
const MAX_SESSION_ID_LENGTH = 64;

const ERRORS = {
  invalidQuestion: {
    status: 400,
    body: {
      code: "INVALID_QUESTION",
      message: "La pregunta debe tener entre 1 y 500 caracteres.",
    },
  },
  rateLimited: {
    status: 429,
    body: {
      code: "RATE_LIMITED",
      message: "Hay demasiadas preguntas. Espera un momento e intenta de nuevo.",
    },
  },
  llmUnavailable: {
    status: 503,
    body: {
      code: "LLM_UNAVAILABLE",
      message: "El asistente no está disponible en este momento. Intenta de nuevo en unos minutos.",
    },
  },
  llmTimeout: {
    status: 504,
    body: {
      code: "LLM_TIMEOUT",
      message: "El asistente tardó demasiado en responder. Intenta de nuevo.",
    },
  },
  internal: {
    status: 502,
    body: {
      code: "INTERNAL_ERROR",
      message: "No se pudo completar la solicitud. Intenta de nuevo más tarde.",
    },
  },
};

function apiChatUrl(apiBaseUrl) {
  const url = new URL(apiBaseUrl);
  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password
  ) {
    throw new Error("API_REST_URL debe ser una URL HTTP(S) sin credenciales");
  }
  url.pathname = `${url.pathname.replace(/\/+$/, "")}${CHAT_PATH}`;
  url.search = "";
  url.hash = "";
  return url.toString();
}

function sendJson(response, status, body) {
  response.writeHead(status, {
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
    "X-Content-Type-Options": "nosniff",
  });
  response.end(JSON.stringify(body));
}

async function readRequestBody(request) {
  const chunks = [];
  let totalBytes = 0;

  for await (const chunk of request) {
    totalBytes += chunk.length;
    if (totalBytes > MAX_BODY_BYTES) {
      throw new RangeError("El cuerpo excede el tamaño permitido");
    }
    chunks.push(chunk);
  }

  return Buffer.concat(chunks).toString("utf8");
}

function parseQuestion(body) {
  if (typeof body !== "object" || body === null || Array.isArray(body)) {
    return null;
  }

  const question = body.question;
  if (
    typeof question !== "string" ||
    question.trim().length === 0 ||
    question.length > MAX_QUESTION_LENGTH
  ) {
    return null;
  }

  if (
    body.sessionId !== undefined &&
    (typeof body.sessionId !== "string" ||
      body.sessionId.length > MAX_SESSION_ID_LENGTH)
  ) {
    return null;
  }

  const payload = { question: question.trim() };
  if (body.sessionId !== undefined) {
    payload.sessionId = body.sessionId;
  }
  return payload;
}

function validChatResponse(body) {
  return (
    typeof body === "object" &&
    body !== null &&
    typeof body.answer === "string" &&
    Array.isArray(body.sources) &&
    body.sources.every(
      (source) =>
        typeof source === "object" &&
        source !== null &&
        typeof source.document === "string" &&
        (source.section === undefined || typeof source.section === "string"),
    )
  );
}

function errorForUpstreamStatus(status) {
  switch (status) {
    case 400:
      return ERRORS.invalidQuestion;
    case 429:
      return ERRORS.rateLimited;
    case 503:
      return ERRORS.llmUnavailable;
    case 504:
      return ERRORS.llmTimeout;
    default:
      return ERRORS.internal;
  }
}

function createServer({
  apiBaseUrl = process.env.API_REST_URL || "http://localhost:8081",
  timeoutMs = Number(process.env.BFF_TIMEOUT_MS || 70000),
  fetchImpl = fetch,
} = {}) {
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1) {
    throw new Error("BFF_TIMEOUT_MS debe ser un entero positivo");
  }
  const destination = apiChatUrl(apiBaseUrl);

  return http.createServer(async (request, response) => {
    if (request.method !== "POST" || request.url !== CHAT_PATH) {
      sendJson(response, 404, ERRORS.internal.body);
      return;
    }

    if (!/^application\/json(?:\s*;|$)/i.test(request.headers["content-type"] || "")) {
      sendJson(response, ERRORS.invalidQuestion.status, ERRORS.invalidQuestion.body);
      return;
    }

    let payload;
    try {
      const body = await readRequestBody(request);
      payload = parseQuestion(JSON.parse(body));
    } catch (error) {
      const tooLarge = error instanceof RangeError;
      sendJson(
        response,
        tooLarge ? 413 : ERRORS.invalidQuestion.status,
        ERRORS.invalidQuestion.body,
      );
      return;
    }

    if (payload === null) {
      sendJson(response, ERRORS.invalidQuestion.status, ERRORS.invalidQuestion.body);
      return;
    }

    let upstream;
    try {
      upstream = await fetchImpl(destination, {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (error) {
      const mapped = error.name === "TimeoutError" ? ERRORS.llmTimeout : ERRORS.llmUnavailable;
      sendJson(response, mapped.status, mapped.body);
      return;
    }

    if (!upstream.ok) {
      const mapped = errorForUpstreamStatus(upstream.status);
      sendJson(response, mapped.status, mapped.body);
      return;
    }

    let result;
    try {
      result = await upstream.json();
    } catch {
      sendJson(response, ERRORS.internal.status, ERRORS.internal.body);
      return;
    }

    if (!validChatResponse(result)) {
      sendJson(response, ERRORS.internal.status, ERRORS.internal.body);
      return;
    }

    sendJson(response, 200, {
      answer: result.answer,
      sources: result.sources.map((source) => ({
        document: source.document,
        ...(source.section === undefined ? {} : { section: source.section }),
      })),
    });
  });
}

if (require.main === module) {
  const port = Number(process.env.BFF_PORT || 3000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("BFF_PORT debe ser un puerto entre 1 y 65535");
  }

  createServer().listen(port, () => {
    console.log(`BFF escuchando en el puerto ${port}`);
  });
}

module.exports = { createServer };
