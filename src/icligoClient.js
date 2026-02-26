import crypto from 'node:crypto';

const LOGIN_URL = 'https://myoffice.icligo.com/account/login';
const NOTIFICATIONS_URL = 'https://myoffice.icligo.com/account/notifications';

export async function fetchNotificationsFingerprint(username, password) {
  let cookieHeader = '';

  const loginPageResponse = await fetch(LOGIN_URL, {
    headers: cookieHeader ? { Cookie: cookieHeader } : undefined,
    redirect: 'follow'
  });
  ensureSuccess(loginPageResponse);
  cookieHeader = mergeCookies(cookieHeader, loginPageResponse.headers.getSetCookie?.() || []);

  const loginHtml = await loginPageResponse.text();
  const form = extractFirstForm(loginHtml);
  if (!form) throw new Error('Formulário de login não encontrado.');

  const action = new URL(form.action || LOGIN_URL, LOGIN_URL).toString();
  const fields = parseInputFields(form.html);

  const userField = fields.find((f) => /user|email|login/i.test(f.name))?.name || 'username';
  const passField = fields.find((f) => f.type === 'password' || /pass/i.test(f.name))?.name || 'password';

  const params = new URLSearchParams();
  for (const field of fields) {
    if (field.name) params.append(field.name, field.value || '');
  }
  params.set(userField, username);
  params.set(passField, password);

  const loginSubmitResponse = await fetch(action, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      ...(cookieHeader ? { Cookie: cookieHeader } : {})
    },
    body: params.toString(),
    redirect: 'follow'
  });
  ensureSuccess(loginSubmitResponse);
  cookieHeader = mergeCookies(cookieHeader, loginSubmitResponse.headers.getSetCookie?.() || []);

  const notificationsResponse = await fetch(NOTIFICATIONS_URL, {
    headers: cookieHeader ? { Cookie: cookieHeader } : undefined,
    redirect: 'follow'
  });
  ensureSuccess(notificationsResponse);

  const notificationsHtml = await notificationsResponse.text();
  const normalized = notificationsHtml.replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  return crypto.createHash('sha256').update(normalized).digest('hex');
}

function ensureSuccess(response) {
  if (!response.ok) throw new Error(`Erro HTTP ${response.status}`);
}

function mergeCookies(currentCookieHeader, setCookies) {
  const jar = new Map();
  if (currentCookieHeader) {
    currentCookieHeader.split(';').map((c) => c.trim()).filter(Boolean).forEach((cookie) => {
      const [name, ...rest] = cookie.split('=');
      jar.set(name, rest.join('='));
    });
  }

  for (const setCookie of setCookies) {
    const pair = setCookie.split(';')[0];
    const [name, ...rest] = pair.split('=');
    jar.set(name.trim(), rest.join('=').trim());
  }

  return Array.from(jar.entries()).map(([k, v]) => `${k}=${v}`).join('; ');
}

function extractFirstForm(html) {
  const formMatch = html.match(/<form\b([^>]*)>([\s\S]*?)<\/form>/i);
  if (!formMatch) return null;

  const attrs = formMatch[1] || '';
  const action = attrs.match(/action=["']([^"']*)["']/i)?.[1] || '';
  return { action, html: formMatch[2] };
}

function parseInputFields(formHtml) {
  const inputRegex = /<input\b([^>]*)>/gi;
  const fields = [];
  let match;
  while ((match = inputRegex.exec(formHtml)) !== null) {
    const attrs = match[1] || '';
    const name = attrs.match(/name=["']([^"']*)["']/i)?.[1] || '';
    const value = attrs.match(/value=["']([^"']*)["']/i)?.[1] || '';
    const type = (attrs.match(/type=["']([^"']*)["']/i)?.[1] || 'text').toLowerCase();
    if (name) fields.push({ name, value, type });
  }
  return fields;
}
