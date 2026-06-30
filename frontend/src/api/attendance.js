import { apiGet, apiPost } from "./client.js";

export function listParentAttendance(tenantId) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/parents/me/attendance?${params.toString()}`);
}

export function listChildAttendance(tenantId, childId) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/parents/me/children/${encodeURIComponent(childId)}/attendance?${params.toString()}`);
}

export function checkInAttendance(request) {
  return apiPost("/api/attendance/check-in", request);
}
