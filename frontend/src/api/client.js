export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const apiBaseUrl = resolveApiBaseUrl();
let redirectingToLogin = false;

export async function apiGet(path, options = {}) {
  const response = await fetch(apiUrl(path), {
    headers: jsonHeaders(options),
  });

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

  if (!response.ok) {
    await handleFailedResponse(response, options);
  }

  return response.json();
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
  if (window.location.hostname === "www.schooldays.cc" || window.location.hostname === "schooldays.cc") {
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
