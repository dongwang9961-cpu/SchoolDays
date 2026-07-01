import { apiGet } from "./client.js";

export function listStudents(tenantId, classId = "") {
  const params = new URLSearchParams();
  if (classId) {
    params.set("classId", classId);
  }
  const query = params.toString();
  return apiGet(`/api/tenants/${tenantId}/students${query ? `?${query}` : ""}`);
}
