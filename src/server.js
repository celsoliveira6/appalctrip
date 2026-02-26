import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { fetchNotificationsFingerprint } from './icligoClient.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const publicDir = path.join(__dirname, '..', 'public');
const PORT = Number(process.env.PORT || 3000);

const monitors = new Map();

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);

    if (req.method === 'GET' && url.pathname === '/') return serveFile('index.html', res);
    if (req.method === 'GET' && url.pathname === '/healthz') return sendJson(res, 200, { ok: true });
    if (req.method === 'GET' && url.pathname.startsWith('/api/events/')) return sse(url.pathname.split('/').pop(), req, res);
    if (req.method === 'GET' && url.pathname.startsWith('/api/status/')) return status(url.pathname.split('/').pop(), res);
    if (req.method === 'POST' && url.pathname === '/api/start') return start(req, res);
    if (req.method === 'POST' && url.pathname.startsWith('/api/stop/')) return stop(url.pathname.split('/').pop(), res);

    return sendJson(res, 404, { error: 'Rota não encontrada.' });
  } catch (error) {
    return sendJson(res, 500, { error: error.message });
  }
});

server.listen(PORT, () => {
  console.log(`Servidor web em http://localhost:${PORT}`);
});


process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

function shutdown(signal) {
  for (const monitor of monitors.values()) {
    clearInterval(monitor.timer);
    for (const listener of monitor.listeners) listener.end();
  }
  server.close(() => {
    console.log(`Servidor terminado com ${signal}`);
    process.exit(0);
  });
}

async function start(req, res) {
  const body = await parseJsonBody(req);
  const { username, password, intervalSeconds = 60 } = body || {};
  if (!username || !password) return sendJson(res, 400, { error: 'username e password são obrigatórios.' });

  try {
    const fingerprint = await fetchNotificationsFingerprint(username, password);
    const monitor = {
      id: randomUUID(),
      username,
      password,
      intervalSeconds: Math.max(30, Number(intervalSeconds) || 60),
      lastFingerprint: fingerprint,
      status: 'ativo',
      lastCheckAt: new Date().toISOString(),
      lastError: null,
      listeners: new Set()
    };

    monitor.timer = setInterval(() => pollMonitor(monitor), monitor.intervalSeconds * 1000);
    monitors.set(monitor.id, monitor);
    return sendJson(res, 200, { id: monitor.id, status: monitor.status });
  } catch (error) {
    return sendJson(res, 400, { error: `Falha no login/leitura: ${error.message}` });
  }
}

function stop(id, res) {
  const monitor = monitors.get(id);
  if (!monitor) return sendJson(res, 404, { error: 'Monitor não encontrado.' });

  clearInterval(monitor.timer);
  notify(monitor, { type: 'stopped' });
  for (const listener of monitor.listeners) listener.end();
  monitors.delete(id);
  return sendJson(res, 200, { ok: true });
}

function status(id, res) {
  const monitor = monitors.get(id);
  if (!monitor) return sendJson(res, 404, { error: 'Monitor não encontrado.' });

  return sendJson(res, 200, {
    status: monitor.status,
    lastCheckAt: monitor.lastCheckAt,
    lastError: monitor.lastError,
    intervalSeconds: monitor.intervalSeconds
  });
}

function sse(id, req, res) {
  const monitor = monitors.get(id);
  if (!monitor) {
    res.writeHead(404);
    return res.end();
  }

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive'
  });
  res.write(`data: ${JSON.stringify({ type: 'connected', status: monitor.status })}\n\n`);

  monitor.listeners.add(res);
  req.on('close', () => monitor.listeners.delete(res));
}

async function pollMonitor(monitor) {
  try {
    const currentFingerprint = await fetchNotificationsFingerprint(monitor.username, monitor.password);
    monitor.lastCheckAt = new Date().toISOString();
    monitor.lastError = null;

    if (currentFingerprint !== monitor.lastFingerprint) {
      monitor.lastFingerprint = currentFingerprint;
      notify(monitor, {
        type: 'new_notification',
        title: 'Nova notificação iCLigo',
        message: 'Foram detetadas alterações na página de notificações.'
      });
    } else {
      notify(monitor, { type: 'heartbeat', lastCheckAt: monitor.lastCheckAt });
    }
  } catch (error) {
    monitor.lastError = error.message;
    notify(monitor, { type: 'error', message: error.message });
  }
}

function notify(monitor, payload) {
  const message = `data: ${JSON.stringify(payload)}\n\n`;
  for (const listener of monitor.listeners) listener.write(message);
}

function serveFile(relativePath, res) {
  const abs = path.join(publicDir, relativePath);
  const content = fs.readFileSync(abs, 'utf8');
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(content);
}

function sendJson(res, status, obj) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}

function parseJsonBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => {
      data += chunk;
      if (data.length > 1_000_000) {
        reject(new Error('Payload demasiado grande.'));
        req.destroy();
      }
    });
    req.on('end', () => {
      if (!data) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch {
        reject(new Error('JSON inválido.'));
      }
    });
    req.on('error', reject);
  });
}
