export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const apiBaseUrl = resolveApiBaseUrl();

export async function apiGet(path, options = {}) {
  const response = await fetch(apiUrl(path), {
    headers: jsonHeaders(options),
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => undefined);
    if (payload && typeof payload.error === "string") {
      throw new ApiError(payload.error, response.status);
    }
    throw new ApiError(`Request failed with ${response.status}`, response.status);
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
    const payload = await response.json().catch(() => undefined);
    if (payload && typeof payload.error === "string") {
      throw new ApiError(payload.error, response.status);
    }
    throw new ApiError(`Request failed with ${response.status}`, response.status);
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
    const payload = await response.json().catch(() => undefined);
    if (payload && typeof payload.error === "string") {
      throw new ApiError(payload.error, response.status);
    }
    throw new ApiError(`Request failed with ${response.status}`, response.status);
  }

  return response.json();
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
