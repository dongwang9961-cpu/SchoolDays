import { apiGet, apiPost } from "./client.js";

export function listParentEnrollments(tenantId) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/parents/me/enrollments?${params.toString()}`);
}

export function createEnrollment(request) {
  return apiPost("/api/enrollments", request);
}
