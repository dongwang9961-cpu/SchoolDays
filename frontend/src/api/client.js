export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const apiBaseUrl = resolveApiBaseUrl();
const AUTH_IDLE_VALIDATION_MS = 30 * 60 * 1000;
const AUTH_IDLE_VALIDATION_RETRY_MS = 60 * 1000;
let redirectingToLogin = false;
let lastAuthenticatedRemoteCallAt = localStorage.getItem("schooldays.accessToken") ? Date.now() : 0;
let authIdleValidationTimer = null;
let authValidationInFlight = false;

if (lastAuthenticatedRemoteCallAt) {
  scheduleAuthIdleValidation();
}

export async function apiGet(path, options = {}) {
  const response = await fetch(apiUrl(path), {
    headers: jsonHeaders(options),
  });

  noteAuthenticatedRemoteCall(options);
  if (!response.ok) {
    await handleFailedResponse(response, options);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function apiPost(path, body, options = {}) {
  const response = await fetch(apiUrl(path), {
    method: "POST",
    headers: jsonHeaders(options),
    body: JSON.stringify(body),
  });

  noteAuthenticatedRemoteCall(options);
  if (!response.ok) {
    await handleFailedResponse(response, options);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function apiDelete(path, options = {}) {
  const response = await fetch(apiUrl(path), {
    method: "DELETE",
    headers: jsonHeaders(options),
  });

  noteAuthenticatedRemoteCall(options);
  if (!response.ok) {
    await handleFailedResponse(response, options);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function apiPostForm(path, formData, options = {}) {
  const headers = {};
  const accessToken = localStorage.getItem("schooldays.accessToken");
  if (options.auth !== false && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const response = await fetch(apiUrl(path), {
    method: "POST",
    headers,
    body: formData,
  });

  noteAuthenticatedRemoteCall(options);
  if (!response.ok) {
    await handleFailedResponse(response, options);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function apiPatch(path, body, options = {}) {
  const response = await fetch(apiUrl(path), {
    method: "PATCH",
    headers: jsonHeaders(options),
    body: JSON.stringify(body),
  });

  noteAuthenticatedRemoteCall(options);
  if (!response.ok) {
    await handleFailedResponse(response, options);
  }

  return response.json();
}

export function markAuthenticatedSessionStarted() {
  if (!localStorage.getItem("schooldays.accessToken")) {
    return;
  }
  lastAuthenticatedRemoteCallAt = Date.now();
  scheduleAuthIdleValidation();
}

export function stopAuthenticatedSessionTracking() {
  lastAuthenticatedRemoteCallAt = 0;
  authValidationInFlight = false;
  if (authIdleValidationTimer) {
    window.clearTimeout(authIdleValidationTimer);
    authIdleValidationTimer = null;
  }
}

function noteAuthenticatedRemoteCall(options = {}) {
  if (options.auth === false || !localStorage.getItem("schooldays.accessToken")) {
    return;
  }
  lastAuthenticatedRemoteCallAt = Date.now();
  scheduleAuthIdleValidation();
}

function scheduleAuthIdleValidation(delayMs = AUTH_IDLE_VALIDATION_MS) {
  if (!lastAuthenticatedRemoteCallAt || !localStorage.getItem("schooldays.accessToken")) {
    stopAuthenticatedSessionTracking();
    return;
  }
  if (authIdleValidationTimer) {
    window.clearTimeout(authIdleValidationTimer);
  }
  authIdleValidationTimer = window.setTimeout(validateAuthAfterIdle, delayMs);
}

async function validateAuthAfterIdle() {
  authIdleValidationTimer = null;
  if (authValidationInFlight || !localStorage.getItem("schooldays.accessToken")) {
    return;
  }
  const idleMs = Date.now() - lastAuthenticatedRemoteCallAt;
  if (idleMs < AUTH_IDLE_VALIDATION_MS) {
    scheduleAuthIdleValidation(AUTH_IDLE_VALIDATION_MS - idleMs);
    return;
  }

  authValidationInFlight = true;
  try {
    const response = await fetch(apiUrl("/api/auth/me"), {
      headers: jsonHeaders(),
    });
    if (response.status === 401) {
      redirectToLogin();
      return;
    }
    if (!response.ok) {
      scheduleAuthIdleValidation(AUTH_IDLE_VALIDATION_RETRY_MS);
      return;
    }
    lastAuthenticatedRemoteCallAt = Date.now();
    scheduleAuthIdleValidation();
  } catch (_error) {
    scheduleAuthIdleValidation(AUTH_IDLE_VALIDATION_RETRY_MS);
  } finally {
    authValidationInFlight = false;
  }
}

async function handleFailedResponse(response, options = {}) {
  const payload = await response.json().catch(() => undefined);
  const message = responseErrorMessage(payload) || `Request failed with ${response.status}`;

  if (response.status === 401 && options.auth !== false) {
    redirectToLogin();
  }

  throw new ApiError(message, response.status);
}

function responseErrorMessage(payload) {
  if (!payload || typeof payload !== "object") {
    return "";
  }
  return [payload.detail, payload.message, payload.error, payload.title]
    .find((value) => typeof value === "string" && value.trim()) || "";
}

function redirectToLogin() {
  if (redirectingToLogin) {
    return;
  }
  redirectingToLogin = true;
  stopAuthenticatedSessionTracking();
  localStorage.removeItem("schooldays.accessToken");
  window.location.replace(window.location.pathname);
}

function apiUrl(path) {
  return `${apiBaseUrl}${path}`;
}

function resolveApiBaseUrl() {
  const configured = String(import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
  if (configured) {
    return configured;
  }
  if (window.location.hostname === "www.schooldays.cc"
    || window.location.hostname === "schooldays.cc"
    || window.location.hostname.endsWith(".schooldays.cc")) {
    return "https://api.schooldays.cc";
  }
  return "";
}

function jsonHeaders(options = {}) {
  const headers = {
    Accept: "application/json",
    "Content-Type": "application/json",
  };
  const accessToken = localStorage.getItem("schooldays.accessToken");
  if (options.auth !== false && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return headers;
}
