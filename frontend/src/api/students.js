import { apiGet } from "./client.js";

export function listStudents(tenantId, classId = "", siteId = "") {
  const params = new URLSearchParams();
  if (classId) {
    params.set("classId", classId);
  }
  if (siteId) {
    params.set("siteId", siteId);
  }
  const query = params.toString();
  return apiGet(`/api/tenants/${tenantId}/students${query ? `?${query}` : ""}`);
}
