import { apiGet, apiPatch, apiPost } from "./client.js";

export function listChildren(tenantId) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/parents/me/children?${params.toString()}`);
}

export function createChild(request) {
  return apiPost("/api/parents/me/children", request);
}

export function updateChild(childId, request) {
  return apiPatch(`/api/parents/me/children/${encodeURIComponent(childId)}`, request);
}
