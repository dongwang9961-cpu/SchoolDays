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

export function checkInExternalStudent(request) {
  return apiPost(`/api/tenants/${encodeURIComponent(request.tenantId)}/external-check-ins`, request);
}

export function listExternalCheckIns({ tenantId, classId, checkDate }) {
  const params = new URLSearchParams({
    classId,
    checkDate,
  });
  return apiGet(`/api/tenants/${encodeURIComponent(tenantId)}/external-check-ins?${params.toString()}`);
}

export function listExternalCheckInCounts({ tenantId, classId, startDate, endDate }) {
  const params = new URLSearchParams({
    classId,
    startDate,
    endDate,
  });
  return apiGet(`/api/tenants/${encodeURIComponent(tenantId)}/external-check-ins/counts?${params.toString()}`);
}

export function getClassAttendanceGrid(tenantId, classId) {
  return apiGet(`/api/tenants/${tenantId}/classes/${classId}/attendance-grid`);
}
