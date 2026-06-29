import { apiGet, apiPost } from "./client.js";

export function getClassPricing(classId) {
  return apiGet(`/api/classes/${classId}/pricing`);
}

export function getTenantClassPricing(tenantId, classId) {
  return apiGet(`/api/tenants/${tenantId}/classes/${classId}/pricing`);
}

export function saveClassPricing(tenantId, classId, request) {
  return apiPost(`/api/tenants/${tenantId}/classes/${classId}/pricing`, request);
}
