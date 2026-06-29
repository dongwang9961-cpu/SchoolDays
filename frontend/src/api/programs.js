import { apiGet, apiPatch, apiPost } from "./client.js";

export function listPrograms(tenantId, siteId) {
  const params = new URLSearchParams({ siteId });
  return apiGet(`/api/tenants/${tenantId}/programs?${params.toString()}`);
}

export function createProgram(tenantId, request) {
  return apiPost(`/api/tenants/${tenantId}/programs`, request);
}

export function updateProgram(tenantId, programId, request) {
  return apiPatch(`/api/tenants/${tenantId}/programs/${programId}`, request);
}
