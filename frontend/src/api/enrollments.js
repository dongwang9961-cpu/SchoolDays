import { apiGet, apiPost } from "./client.js";

export function listParentEnrollments(tenantId) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/parents/me/enrollments?${params.toString()}`);
}

export function listParentMessages(tenantId) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/parents/me/messages?${params.toString()}`);
}

export function markParentMessageRead(tenantId, messageId) {
  const params = new URLSearchParams({ tenantId });
  return apiPost(`/api/parents/me/messages/${encodeURIComponent(messageId)}/read?${params.toString()}`, {});
}

export function createEnrollment(request) {
  return apiPost("/api/enrollments", request);
}

export function listPendingEnrollmentRequests(tenantId, siteId) {
  const params = new URLSearchParams({ siteId });
  return apiGet(`/api/tenants/${tenantId}/enrollment-requests?${params.toString()}`);
}

export function approveEnrollmentRequest(tenantId, enrollmentId) {
  return apiPost(`/api/tenants/${tenantId}/enrollment-requests/${enrollmentId}/approve`, {});
}

export function rejectEnrollmentRequest(tenantId, enrollmentId, message) {
  return apiPost(`/api/tenants/${tenantId}/enrollment-requests/${enrollmentId}/reject`, { message });
}
