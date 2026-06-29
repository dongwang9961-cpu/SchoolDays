import { apiGet, apiPatch, apiPost } from "./client.js";

export function listSites(tenantId) {
  return apiGet(`/api/tenants/${tenantId}/sites`);
}

export function createSite(tenantId, request) {
  return apiPost(`/api/tenants/${tenantId}/sites`, request);
}

export function updateSite(tenantId, siteId, request) {
  return apiPatch(`/api/tenants/${tenantId}/sites/${siteId}`, request);
}
