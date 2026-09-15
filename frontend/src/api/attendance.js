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

export function checkInStudent(request) {
  return apiPost("/api/attendance/check-in", {
    tenantId: request.tenantId,
    childId: request.childId || request.externalStudentId,
    classId: request.classId,
    classDate: request.checkDate,
  });
}

export function listClassCheckIns({ tenantId, classId, checkDate }) {
  const params = new URLSearchParams({
    date: checkDate,
  });
  return apiGet(`/api/tenants/${encodeURIComponent(tenantId)}/classes/${encodeURIComponent(classId)}/attendance?${params.toString()}`)
    .then((response) => ({
      checkIns: (response.attendance || []).map((entry) => ({
        id: entry.id,
        seqId: entry.seqId,
        externalStudentId: entry.childId,
        studentName: entry.childName,
        gender: "",
        classId: entry.classId,
        className: entry.className,
        checkDate: entry.classDate,
        checkInTime: entry.checkedInAt,
        checkedInByUserId: entry.checkedInByUserId,
        checkedInByRole: entry.checkedInByRole,
        status: entry.status,
        createdAt: entry.createdAt,
        updatedAt: entry.updatedAt,
      })),
    }));
}

export function listClassAttendanceCounts({ tenantId, classId }) {
  return getClassAttendanceGrid(tenantId, classId).then((response) => {
    const counts = new Map();
    (response.students || []).forEach((student) => {
      (student.attendance || []).forEach((entry) => {
        if (!entry.checkedIn) {
          return;
        }
        counts.set(entry.classDate, (counts.get(entry.classDate) || 0) + 1);
      });
    });
    return Array.from(counts, ([checkDate, checkInCount]) => ({ checkDate, checkInCount }));
  });
}

export function getClassAttendanceGrid(tenantId, classId) {
  return apiGet(`/api/tenants/${tenantId}/classes/${classId}/attendance-grid`);
}
