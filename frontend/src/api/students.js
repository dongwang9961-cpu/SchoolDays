import { apiGet } from "./client.js";

export function listStudents(tenantId, classId = "", siteId = "", group = "active", year = "", detailed = false) {
  const params = new URLSearchParams();
  if (classId) {
    params.set("classId", classId);
  }
  if (siteId) {
    params.set("siteId", siteId);
  }
  if (group) {
    params.set("group", group);
  }
  if (year) {
    params.set("year", year);
  }
  if (detailed) {
    params.set("detailed", "true");
  }
  const query = params.toString();
  return apiGet(`/api/tenants/${tenantId}/students${query ? `?${query}` : ""}`);
}
