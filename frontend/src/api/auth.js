import { apiDelete, apiGet, apiPost } from "./client.js";

export function login({ email, password }) {
  return apiPost("/api/auth/login", { email, password }, { auth: false });
}

export function completeRegistration(request) {
  return apiPost("/api/auth/complete-registration", request, { auth: false });
}

export function completePasswordReset(request) {
  return apiPost("/api/auth/complete-password-reset", request, { auth: false });
}

export function requestParentRegistrationLink({ tenantId, email }) {
  return apiPost("/api/auth/request-parent-registration-link", { tenantId, email }, { auth: false });
}

export function inviteUsers({ tenantId, role, emails, classId, siteId }) {
  return apiPost(`/api/tenants/${encodeURIComponent(tenantId)}/user-invitations`, {
    role,
    emails,
    classId,
    siteId,
  });
}

export function sendPasswordResetLinks({ tenantId, role, emails }) {
  return apiPost(`/api/tenants/${encodeURIComponent(tenantId)}/password-reset-links`, {
    role,
    emails,
  });
}

export function deleteUser({ tenantId, email }) {
  const params = new URLSearchParams({ email });
  return apiDelete(`/api/tenants/${encodeURIComponent(tenantId)}/users?${params.toString()}`);
}

export function listStudentsForCheckIn({ tenantId, classId } = {}) {
  const params = new URLSearchParams();
  if (classId) {
    params.set("classId", String(classId));
  }
  const query = params.toString();
  return apiGet(`/api/tenants/${encodeURIComponent(tenantId)}/students${query ? `?${query}` : ""}`)
    .then((response) => ({
      students: (response.students || []).map((student) => ({
        externalId: student.childId,
        studentName: student.childName,
        firstName: student.firstName,
        lastName: student.lastName,
        birthDate: student.dateOfBirth,
        gradeLevelCode: "",
        genderCode: "",
      })),
      page: 1,
      pageSize: response.students?.length || 0,
      totalRows: response.students?.length || 0,
      totalPages: 1,
    }));
}

export function getAuthConfig() {
  return apiGet("/api/auth/config", { auth: false });
}

export function startGoogleAuth({ tenantId }) {
  const params = new URLSearchParams({ tenantId });
  return apiGet(`/api/auth/google/start?${params.toString()}`, { auth: false });
}

export function startGoogleRegistration({ tenantId }) {
  return startGoogleAuth({ tenantId });
}

export function getCurrentAuthUser() {
  return apiGet("/api/auth/me");
}

export function acceptTenantInvitation(request) {
  return apiPost("/api/auth/accept-tenant-invitation", request, { auth: false });
}

export function acceptTeacherInvitation(request) {
  return apiPost("/api/auth/accept-teacher-invitation", request, { auth: false });
}
