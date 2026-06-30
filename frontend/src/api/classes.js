import { apiGet, apiPatch, apiPost } from "./client.js";

export function listClasses(tenantId, siteId) {
  const params = new URLSearchParams({ siteId });
  return apiGet(`/api/tenants/${tenantId}/classes?${params.toString()}`);
}

export function listAvailableClasses(tenantId) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/parents/me/classes?${params.toString()}`);
}

export function createClass(tenantId, request) {
  return apiPost(`/api/tenants/${tenantId}/classes`, request);
}

export function updateClass(tenantId, classId, request) {
  return apiPatch(`/api/tenants/${tenantId}/classes/${classId}`, request);
}
