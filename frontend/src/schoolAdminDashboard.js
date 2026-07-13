import { escapeHtml } from "./authPage.js";
import { changePassword, getProfile, updateProfile } from "./api/account.js";
import { deleteUser, importExternalStudents, inviteUsers, listExternalStudents, sendPasswordResetLinks } from "./api/auth.js";
import { checkInAttendance, checkInExternalStudent, getClassAttendanceGrid, listExternalCheckIns, listExternalCheckInCounts, listParentAttendance } from "./api/attendance.js";
import { createChild, listChildren, updateChild } from "./api/children.js";
import { assignClassTeacher, closeClassEnrollment, createClass, listAvailableClasses, listClasses, listClassTeachers, listTeacherClasses, stopClass, updateClass } from "./api/classes.js";
import { createEnrollment, listParentEnrollments } from "./api/enrollments.js";
import { getClassPricing, getTenantClassPricing, saveClassPricing } from "./api/pricing.js";
import { createProgram, listPrograms, updateProgram } from "./api/programs.js";
import { createSite, listSites, updateSite } from "./api/sites.js";
import { listStudents } from "./api/students.js";
import jsQR from "jsqr";
import QRCode from "qrcode";
import {
  listNotificationHistory,
  listNotificationProviders,
  sendNotification,
  startGmailConnection,
} from "./api/notifications.js";

let googlePlacesPromise;
let tabulatorPromise;

const schoolAdminManagementSections = [
  {
    id: "sites",
    label: "Sites",
    title: "Sites",
    summary: "Create and manage school sites, then log on to a site workspace when you are ready.",
    actions: ["Add site"],
    rows: ["No sites have been created yet."],
  },
  {
    id: "teachers",
    label: "Teachers",
    title: "Teachers",
    summary: "Invite school teachers and review invitation status before assigning classes inside a site.",
    actions: ["Invite teacher", "View invitations"],
    rows: ["No teacher records loaded yet."],
  },
];

const schoolAdminSiteSections = [
  {
    id: "programs",
    label: "Programs",
    title: "Programs",
    summary: "Organize classes under programs such as camps, after school, or weekend tracks.",
    actions: ["Add program", "Edit selected program"],
    rows: ["Summer Camp", "After School", "Weekend Art"],
  },
  {
    id: "classes",
    label: "Classes",
    title: "Classes",
    summary: "Configure class dates, capacity, teacher assignments, pricing, and public links.",
    actions: ["Add class"],
    rows: ["No classes have been created for this site yet."],
  },
  {
    id: "students",
    label: "Students",
    title: "Students",
    summary: "Review students enrolled in active classes, or filter the roster by class.",
    actions: [],
    rows: ["No active-class students loaded yet."],
  },
  {
    id: "teachers",
    label: "Class Teachers",
    title: "Class Teachers",
    summary: "Manage teacher assignments for classes in the selected site.",
    actions: ["Assign to class", "Remove assignment"],
    rows: ["No class teacher assignments loaded yet."],
  },
  {
    id: "enrollments",
    label: "Enrollments",
    title: "Enrollments",
    summary: "Review parent registrations, approve receipt-based enrollments, and monitor capacity.",
    actions: ["Review pending", "Approve enrollment", "Reject enrollment"],
    rows: ["No enrollment records loaded yet."],
  },
  {
    id: "payments",
    label: "Payments",
    title: "Payments",
    summary: "Track Stripe payments, offline payments, uploaded receipts, and refunds.",
    actions: ["Record offline payment", "Approve receipt", "Reject receipt", "Issue refund"],
    rows: ["No payment records loaded yet."],
  },
  {
    id: "attendance",
    label: "Attendance",
    title: "Attendance",
    summary: "Review each class attendance grid across the full class date range.",
    actions: [],
    rows: ["No attendance records loaded yet."],
  },
  {
    id: "notifications",
    label: "Notifications",
    title: "Notifications",
    summary: "Configure email providers and send class or student notifications.",
    actions: ["Free send"],
    rows: ["No notification history loaded yet."],
  },
];

const teacherSections = [
  {
    id: "overview",
    label: "Overview",
    title: "Teacher overview",
    summary: "Review assigned classes and attendance tasks.",
    actions: ["Open today's classes", "Check attendance"],
    rows: ["No assigned classes loaded yet."],
  },
  {
    id: "classes",
    label: "My Classes",
    title: "My Classes",
    summary: "View class rosters, schedules, and class details.",
    actions: ["Open roster", "View schedule"],
    rows: ["No class records loaded yet."],
  },
  {
    id: "attendance",
    label: "Attendance",
    title: "Attendance",
    summary: "Check students in and review attendance by class date.",
    actions: ["Check in student", "View class attendance"],
    rows: ["No attendance records loaded yet."],
  },
  {
    id: "students",
    label: "Students",
    title: "Students",
    summary: "Review students enrolled in assigned classes.",
    actions: ["Open student list"],
    rows: ["No student records loaded yet."],
  },
  {
    id: "notifications",
    label: "Notifications",
    title: "Notifications",
    summary: "Send class-related email notifications where school policy allows it.",
    actions: ["Free send"],
    rows: ["No notification history loaded yet."],
  },
];

const parentSections = [
  {
    id: "overview",
    label: "Overview",
    title: "Family overview",
    summary: "Review children, current registrations, and open classes.",
    actions: [],
    rows: ["No family dashboard data loaded yet."],
  },
  {
    id: "children",
    label: "Children",
    title: "Children",
    summary: "Manage child profiles for this school.",
    actions: ["Add child"],
    rows: ["No child records loaded yet."],
  },
  {
    id: "classes",
    label: "Classes",
    title: "Available Classes",
    summary: "Browse public classes and choose available dates.",
    actions: [],
    rows: ["Class catalog is not loaded yet."],
  },
  {
    id: "enrollments",
    label: "Enrollments",
    title: "Enrollments",
    summary: "Review your children’s class registrations and enrollment status.",
    actions: [],
    rows: ["No enrollment records loaded yet."],
  },
  {
    id: "payments",
    label: "Payments",
    title: "Payments",
    summary: "Pay required fees, upload receipts, and review payment history.",
    disabled: true,
    actions: [],
    rows: ["No payment records loaded yet."],
  },
  {
    id: "attendance",
    label: "Attendance",
    title: "Attendance",
    summary: "Check in children and review attendance history.",
    disabled: true,
    actions: [],
    rows: ["No attendance records loaded yet."],
  },
];

const roleDashboards = {
  SCHOOL_ADMIN: {
    label: "Admin",
    navLabel: "School admin",
    sections: schoolAdminManagementSections,
  },
  TEACHER: {
    label: "Teacher",
    navLabel: "Teacher portal",
    sections: teacherSections,
  },
  PARENT: {
    label: "Parent",
    navLabel: "Parent portal",
    sections: parentSections,
  },
};

export function renderSchoolDashboard({ role, school, user, onLogout }) {
  const dashboard = roleDashboards[role] || roleDashboards.PARENT;
  const root = document.querySelector("#root");
  const currentUserEmail = String(user?.email || "").trim().toLowerCase();
  let adminMode = role === "SCHOOL_ADMIN" ? "" : "portal";
  let teacherMode = role === "TEACHER" ? "choice" : "";
  let activeSectionId = role === "SCHOOL_ADMIN" ? "sites" : "overview";
  let activeOperation = "";
  let notice = "";
  let error = "";
  let siteRows = null;
  let sites = [];
  let selectedSiteId = "";
  let siteQuota = null;
  let loadingSites = false;
  let programRows = null;
  let programs = [];
  let selectedProgramId = "";
  let loadingPrograms = false;
  let classRows = null;
  let classes = [];
  let selectedClassId = "";
  let selectedClassPricing = null;
  let loadingClasses = false;
  let loadingClassPricing = false;
  let classTeachers = [];
  let loadingClassTeachers = false;
  let students = [];
  let selectedStudentClassId = "";
  let loadingStudents = false;
  let notificationProviders = [];
  let notificationHistory = [];
  let loadingNotifications = false;
  let childRows = null;
  let children = [];
  let selectedChildId = "";
  let loadingChildren = false;
  let enrollments = [];
  let selectedEnrollmentId = "";
  let loadingEnrollments = false;
  let attendanceRecords = [];
  let attendanceGrid = null;
  let selectedAttendanceClassId = "";
  let adminAttendanceView = "table";
  let loadingAttendance = false;
  let enrollmentModalOpen = false;
  let enrollmentPricing = null;
  let loadingEnrollmentPricing = false;
  let profileOpen = false;
  let profile = null;
  let loadingProfile = false;
  let inviteUserRole = "SCHOOL_ADMIN";
  let inviteUserEmails = "";
  let inviteUserMessage = "";
  let inviteUserError = "";
  let inviteUserResults = [];
  let inviteUserSubmitting = false;
  let inviteUserPasswordResetSubmitting = false;
  let inviteUserSiteId = "";
  let inviteUserClassId = "";
  let inviteUserClasses = [];
  let inviteUserClassesLoadedForSiteId = "";
  let loadingInviteUserClasses = false;
  let deleteUserEmail = "";
  let deleteUserMessage = "";
  let deleteUserError = "";
  let deleteUserSubmitting = false;
  let externalAttendanceStage = "";
  let externalAttendanceDate = "";
  let externalAttendanceDetailRows = [];
  let externalAttendanceDetailLoading = false;
  let externalAttendanceDetailError = "";
  let externalAttendanceDetailQueryKey = "";
  let externalAttendanceDetailOpen = false;
  let externalAttendanceDetailTabulator = null;
  let externalAttendanceCountRows = [];
  let externalAttendanceCountLoading = false;
  let externalAttendanceCountError = "";
  let externalAttendanceCountQueryKey = "";
  let checkInFlowStage = "intro";
  let checkInSelectionError = "";
  let checkInImportFile = null;
  let checkInImportMessage = "";
  let checkInImportError = "";
  let checkInImportSubmitting = false;
  let checkInStudentsOpen = false;
  let checkInStudentsLoading = false;
  let checkInStudents = [];
  let checkInStudentsError = "";
  let checkInStudentsMessage = "";
  let checkInStudentsSelectedCount = 0;
  let checkInStudentsTabulator = null;
  let checkInStudentsPage = 1;
  let checkInStudentsPageSize = 25;
  let checkInStudentsTotalRows = 0;
  let checkInStudentsTotalPages = 1;
  let checkInStudentQrModal = null;
  let checkInTodayListOpen = false;
  let checkInTodayListLoading = false;
  let checkInTodayListRows = [];
  let checkInTodayListCount = 0;
  let checkInTodayListError = "";
  let checkInTodayListDate = "";
  let checkInTodayListQueryKey = "";
  let checkInReminderOpen = false;
  let checkInReminderDismissed = false;
  let noticeTimer = null;

  function resetExternalAttendanceState() {
    externalAttendanceStage = "intro";
    externalAttendanceDate = "";
    externalAttendanceDetailRows = [];
    externalAttendanceDetailLoading = false;
    externalAttendanceDetailError = "";
    externalAttendanceDetailQueryKey = "";
    externalAttendanceDetailOpen = false;
    externalAttendanceCountRows = [];
    externalAttendanceCountLoading = false;
    externalAttendanceCountError = "";
    externalAttendanceCountQueryKey = "";
    destroyExternalAttendanceDetailTabulator();
  }
let checkInCameraStream = null;
let checkInScannerStarting = false;
let checkInScanning = false;
let checkInScannerTimer = null;
let checkInDetector = null;
let checkInFallbackCanvas = null;
let checkInFallbackContext = null;
let checkInScannerMode = "native";
let checkInAudioContext = null;
let checkInSpeechUnlocked = false;
let checkInBarcodeValue = "";
let checkInManualStudentId = "";
let checkInLastRawBarcodeValue = "";
let checkInExternalCheckInSubmitting = false;
let checkInQuickListOpen = false;
let checkInQuickListMessage = "";
let checkInQuickListError = "";
let checkInQuickSubmittingStudentId = "";
let checkInQuickTabulator = null;
let checkInScannerStatus = "Starting camera...";
let checkInPeriodicRefreshTimer = null;
const classListCache = new Map();
const CLASS_LIST_CACHE_TTL_MS = 5000;
const CHECK_IN_PERIODIC_REFRESH_MS = 30000;

  if (role === "SCHOOL_ADMIN") {
    loadSites();
  } else if (role === "TEACHER") {
    loadTeacherClasses();
  } else if (role === "PARENT") {
    loadChildren();
    loadClasses();
    loadEnrollments();
    loadAttendance();
  }

  window.addEventListener("message", (event) => {
    if (event.data?.type === "schooldays:gmail-connected") {
      notice = "Gmail connected.";
      error = "";
      loadNotifications();
    }
  });
  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && checkInStudentQrModal) {
      closeCheckInStudentQrModal();
    } else if (event.key === "Escape" && checkInQuickListOpen) {
      closeQuickCheckInList();
    }
  });

  render();

  function render() {
    scheduleNoticeDismissal();
    syncCheckInPeriodicRefresh();
    if (!((role === "SCHOOL_ADMIN" && adminMode === "checkIn" && checkInFlowStage === "camera")
      || (role === "TEACHER" && teacherMode === "checkIn" && checkInFlowStage === "camera"))) {
      stopCheckInScanner();
    }
    if (!checkInStudentsOpen) {
      destroyCheckInStudentsTabulator();
    }
    if (!checkInQuickListOpen) {
      destroyQuickCheckInTabulator();
    }
    if (role === "TEACHER" && teacherMode === "choice") {
      renderTeacherChoiceScreen();
      return;
    }
    if ((role === "SCHOOL_ADMIN" && adminMode === "externalAttendance") || (role === "TEACHER" && teacherMode === "externalAttendance")) {
      if (externalAttendanceStage !== "calendar") {
        renderExternalAttendanceIntro();
      } else {
        renderExternalAttendanceCalendar();
      }
      return;
    }
    if (role === "TEACHER" && teacherMode === "checkIn" && checkInFlowStage !== "camera") {
      renderTeacherCheckInIntro();
      return;
    }
    if (role === "TEACHER" && teacherMode === "checkIn" && checkInFlowStage === "camera") {
      renderTeacherCheckIn();
      return;
    }
    if (role === "SCHOOL_ADMIN" && !adminMode) {
      renderSchoolAdminLanding();
      return;
    }
    if (role === "SCHOOL_ADMIN" && adminMode === "checkIn" && checkInFlowStage !== "camera") {
      renderSchoolAdminCheckInIntro();
      return;
    }
    if (role === "SCHOOL_ADMIN" && adminMode === "checkIn" && checkInFlowStage === "camera") {
      renderSchoolAdminCheckIn();
      return;
    }
    if (role === "SCHOOL_ADMIN" && adminMode === "inviteUser") {
      renderSchoolAdminInviteUser();
      return;
    }
    if (role === "SCHOOL_ADMIN" && adminMode === "site" && !selectedSite()) {
      renderSiteLoginScreen();
      return;
    }

    const sections = currentSections();
    const activeSection = sections.find((section) => section.id === activeSectionId) || sections[0];
    const rows = rowsFor(activeSection);
    const currentSite = selectedSite();
    const title = role === "SCHOOL_ADMIN" && adminMode === "site" && currentSite ? currentSite.name : dashboard.label;
    const notificationModalOpen = activeOperation && isNotificationOperation(activeOperation);
    const checkInTasks = role === "PARENT" ? pendingCheckInTasks() : [];
    const checkInReminderVisible = checkInTasks.length > 0 && (checkInReminderOpen || !checkInReminderDismissed);
    const activeOperationPanel = activeOperation && !notificationModalOpen
        ? operationPanel(activeSection, activeOperation, selectedSite(), selectedProgram(), selectedClass(), selectedChild(), selectedClassPricing, user, sites, programs, classes, loadingClassPricing)
        : "";

    root.innerHTML = `
      <main class="app-shell">
        <aside class="app-sidebar">
          <div class="app-brand">
            <p class="eyebrow">${escapeHtml(school.name)}</p>
            <h1>${escapeHtml(title)}</h1>
          </div>

          ${role === "SCHOOL_ADMIN" ? adminModeSwitcher() : role === "TEACHER" ? teacherModeSwitcher() : ""}

          <nav class="app-nav" aria-label="${escapeHtml(dashboard.navLabel)}">
            ${sections
              .map(
                (section) => `
                  <button
                    class="${section.id === activeSection.id ? "is-active" : ""}"
                    data-section-id="${escapeHtml(section.id)}"
                    type="button"
                    ${section.disabled ? "disabled" : ""}
                  >
                    ${escapeHtml(section.label)}
                  </button>
                `
              )
              .join("")}
          </nav>
        </aside>

        <section class="app-main">
          <header class="app-header">
            <div>
              <h2>${escapeHtml(activeSection.title)}</h2>
              <p>${escapeHtml(activeSection.summary)}</p>
            </div>
            <div class="header-actions">
              ${parentCheckInTaskButton(checkInTasks)}
              ${profileMenu(user, { profileDisabled: role === "PARENT" && hasExternalAuth(user) })}
            </div>
          </header>

          ${toolbarFor(activeSection)}

          <section class="workspace-panel">
            <div class="workspace-heading workspace-heading-row">
              <div>
                <h3>${escapeHtml(activeSection.title)} workspace</h3>
                <p>${escapeHtml(workspaceHint(activeSection, role))}</p>
                ${activeSection.id === "sites" && siteQuota ? `<p class="context-note">${escapeHtml(siteQuotaText(siteQuota))}</p>` : ""}
              </div>
              ${panelHeaderAction(activeSection, role)}
            </div>

            ${error ? `<p class="message error" role="alert">${escapeHtml(error)}</p>` : ""}
            ${activeOperationPanel}

            ${dataList(activeSection, rows)}
          </section>
          ${notificationModalOpen ? notificationModal(activeSection, activeOperation, selectedSite(), selectedProgram(), selectedClass(), selectedChild(), selectedClassPricing, user, sites, programs, classes, loadingClassPricing) : ""}
          ${checkInReminderVisible ? checkInReminderModal(checkInTasks) : ""}
          ${profileOpen ? profilePanel(profile, user, loadingProfile) : ""}
          ${enrollmentModalOpen ? enrollmentModal(selectedClass(), children, enrollmentPricing, enrollments, loadingEnrollmentPricing) : ""}
          ${noticeToast(notice)}
        </section>
      </main>
    `;

    root.querySelectorAll("[data-section-id]").forEach((button) => {
      button.addEventListener("click", () => {
        activeSectionId = button.dataset.sectionId;
        activeOperation = "";
        notice = "";
        error = "";
        if (adminMode === "manage" && activeSectionId !== "sites") {
          selectedSiteId = "";
        }
        render();
        if (activeSectionId === "notifications") {
          loadNotifications();
        }
        if (activeSectionId === "overview" && role === "PARENT") {
          loadChildren();
          loadClasses();
          loadEnrollments();
        }
        if (activeSectionId === "children") {
          loadChildren();
        }
        if (activeSectionId === "classes") {
          loadClasses();
        }
        if (activeSectionId === "students") {
          loadClasses();
          loadStudents();
        }
        if (activeSectionId === "enrollments") {
          loadEnrollments();
          loadAttendance();
        }
        if (activeSectionId === "attendance") {
          if (role === "PARENT") {
            loadChildren();
            loadClasses();
            loadEnrollments();
            loadAttendance();
          } else if (role === "SCHOOL_ADMIN") {
            loadClasses();
          }
        }
        if (activeSectionId === "payments") {
          loadClasses();
          loadEnrollments();
        }
      });
    });

    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-profile-menu-toggle]")?.addEventListener("click", () => {
      const menu = root.querySelector("[data-profile-menu]");
      menu?.toggleAttribute("hidden");
    });
    root.querySelector("[data-profile-action='profile']")?.addEventListener("click", () => {
      openProfilePanel();
    });
    root.querySelector("[data-check-in-taskbar]")?.addEventListener("click", () => {
      checkInReminderOpen = true;
      checkInReminderDismissed = false;
      notice = "";
      error = "";
      render();
    });
    root.querySelector("[data-check-in-reminder-close]")?.addEventListener("click", closeCheckInReminder);
    root.querySelector("[data-check-in-reminder-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeCheckInReminder();
      }
    });
    root.querySelector("[data-profile-close]")?.addEventListener("click", closeProfilePanel);
    root.querySelector("[data-profile-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeProfilePanel();
      }
    });
    root.querySelector("[data-profile-form]")?.addEventListener("submit", handleProfileSubmit);
    root.querySelector("[data-password-form]")?.addEventListener("submit", handlePasswordSubmit);
    initializeDirtyForms(root);
    root.querySelectorAll("[data-admin-mode]").forEach((button) => {
      button.addEventListener("click", () => {
        adminMode = button.dataset.adminMode;
        activeSectionId = adminMode === "site" ? "programs" : "sites";
        activeOperation = "";
        notice = "";
        error = "";
        if (adminMode === "inviteUser") {
          inviteUserMessage = "";
          inviteUserError = "";
          inviteUserResults = [];
        }
        if (adminMode === "site") {
          resetSiteWorkspaceData();
        }
        render();
        if (adminMode === "site" && selectedSite()) {
          loadPrograms();
          loadClasses();
        }
      });
    });
    root.querySelectorAll("[data-teacher-mode]").forEach((button) => {
      button.addEventListener("click", () => {
        teacherMode = button.dataset.teacherMode;
        if (teacherMode === "checkIn") {
          checkInFlowStage = "intro";
          checkInSelectionError = "";
        }
        if (teacherMode === "externalAttendance") {
          resetExternalAttendanceState();
        }
        render();
      });
    });
    root.querySelectorAll("[data-operation-action]").forEach((button) => {
      button.addEventListener("click", () => {
        const action = button.dataset.operationAction;
        if (role === "PARENT" && isBrowseClassesAction(action)) {
          activeSectionId = "classes";
          activeOperation = "";
          notice = "";
          error = "";
          render();
          loadClasses();
          return;
        }
        if (isLogOnSiteAction(action)) {
          if (!selectedSite()) {
            notice = "";
            error = "Select a site before logging on.";
            activeOperation = "";
            render();
            return;
          }
          adminMode = "site";
          activeSectionId = "programs";
          activeOperation = "";
          resetSiteWorkspaceData();
          notice = `Logged on to ${selectedSite().name}.`;
          error = "";
          render();
          loadPrograms();
          loadClasses();
          return;
        }
        if (isProgramOperation(action) && role === "SCHOOL_ADMIN" && adminMode === "site" && !selectedSite()) {
          notice = "";
          error = "Log on to a site before managing programs.";
          activeOperation = "";
          render();
          return;
        }
        if (isEditProgramOperation(action) && !selectedProgram()) {
          notice = "";
          error = "Select a program before editing.";
          activeOperation = "";
          render();
          return;
        }
        if (isClassOperation(action) && role === "SCHOOL_ADMIN" && adminMode === "site" && !selectedSite()) {
          notice = "";
          error = "Log on to a site before managing classes.";
          activeOperation = "";
          render();
          return;
        }
        if (isCreateClassOperation(action) && !programs.length) {
          notice = "";
          error = "Create a program before adding classes.";
          activeOperation = "";
          render();
          return;
        }
        if (isEditClassOperation(action) && !selectedClass()) {
          notice = "";
          error = "Select a class before editing.";
          activeOperation = "";
          render();
          return;
        }
        if (isPricingOperation(action) && !selectedClass()) {
          notice = "";
          error = "Select a class before configuring pricing.";
          activeOperation = "";
          render();
          return;
        }
        if (isEditChildOperation(action) && !selectedChild()) {
          notice = "";
          error = "Select a child before editing.";
          activeOperation = "";
          render();
          return;
        }
        if (isEditSiteOperation(action) && !selectedSite()) {
          notice = "";
          error = "Select a site before editing.";
          activeOperation = "";
          render();
          return;
        }
        activeOperation = action;
        notice = "";
        error = "";
        render();
        if (isPricingOperation(activeOperation)) {
          loadSelectedClassPricing();
        }
        if (isNotificationOperation(activeOperation)) {
          loadNotifications();
        }
      });
    });

    root.querySelector("[data-operation-cancel]")?.addEventListener("click", () => {
      activeOperation = "";
      notice = "";
      error = "";
      render();
    });
    root.querySelector("[data-notification-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        activeOperation = "";
        notice = "";
        error = "";
        render();
      }
    });
    root.querySelector("[data-enrollment-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeEnrollmentModal();
      }
    });
    root.querySelector("[data-enrollment-cancel]")?.addEventListener("click", closeEnrollmentModal);
    root.querySelector("[data-enrollment-form]")?.addEventListener("submit", handleEnrollmentSubmit);
    root.querySelectorAll("[data-parent-attendance-date]").forEach((button) => {
      button.addEventListener("click", async () => {
        await handleParentAttendanceCardClick(button);
      });
    });

    root.querySelector("[data-operation-form]")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await handleOperationSubmit(event.currentTarget, activeSection, activeOperation);
    });
    root.querySelector("[data-add-pricing-row]")?.addEventListener("click", () => {
      const rows = root.querySelector("[data-pricing-rows]");
      rows?.insertAdjacentHTML("beforeend", pricingRowFields(emptyPricingRecord()));
      updateDirtyForm(root.querySelector("[data-operation-form]"));
    });
    root.querySelector("[data-pricing-rows]")?.addEventListener("click", (event) => {
      const button = event.target.closest("[data-remove-pricing-row]");
      if (button) {
        const rows = root.querySelectorAll("[data-pricing-row]");
        if (rows.length > 1) {
          button.closest("[data-pricing-row]")?.remove();
          updateDirtyForm(root.querySelector("[data-operation-form]"));
        }
      }
    });
    initializeClassTypeControls(root);
    initializeNotificationAudienceControls(root);
    initializeEmlTemplateUpload(root);
    initializeNotificationWizard(root);
    initializeEnrollmentWizard(root);
    initializeDirtyForms(root);
    root.querySelector("[data-send-test-notification]")?.addEventListener("click", async (event) => {
      await handleTestNotification(event.currentTarget);
    });

    root.querySelectorAll("[data-site-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedSiteId = button.dataset.siteId;
        notice = "";
        error = "";
        render();
      });
    });
    root.querySelectorAll("[data-site-login-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedSiteId = button.dataset.siteLoginId;
        adminMode = "site";
        activeSectionId = "programs";
        activeOperation = "";
        resetSiteWorkspaceData();
        notice = `Logged on to ${selectedSite()?.name || "site"}.`;
        error = "";
        render();
        loadPrograms();
        loadClasses();
      });
    });
    root.querySelectorAll("[data-program-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedProgramId = button.dataset.programId;
        notice = "";
        error = "";
        render();
      });
    });
    root.querySelectorAll("[data-program-edit-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedProgramId = button.dataset.programEditId;
        activeOperation = "Edit selected program";
        notice = "";
        error = "";
        render();
      });
    });
    root.querySelectorAll("[data-site-edit-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedSiteId = button.dataset.siteEditId;
        activeOperation = "Edit selected site";
        notice = "";
        error = "";
        render();
      });
    });
    root.querySelectorAll("[data-class-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedClassId = button.dataset.classId;
        selectedClassPricing = null;
        classTeachers = [];
        notice = "";
        error = "";
        render();
        loadClassTeachers();
        if (isPricingOperation(activeOperation)) {
          loadSelectedClassPricing();
        }
      });
    });
    root.querySelector("[data-class-detail-back]")?.addEventListener("click", () => {
      selectedClassId = "";
      selectedClassPricing = null;
      classTeachers = [];
      activeOperation = "";
      notice = "";
      error = "";
      render();
    });
    root.querySelectorAll("[data-class-edit-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedClassId = button.dataset.classEditId;
        selectedClassPricing = null;
        activeOperation = "Edit selected class";
        notice = "";
        error = "";
        render();
      });
    });
    root.querySelectorAll("[data-class-pricing-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedClassId = button.dataset.classPricingId;
        selectedClassPricing = null;
        activeOperation = "Configure pricing";
        notice = "";
        error = "";
        render();
        loadSelectedClassPricing();
      });
    });
    root.querySelector("[data-class-assign-teacher]")?.addEventListener("click", () => {
      activeOperation = "Assign teacher";
      notice = "";
      error = "";
      render();
    });
    root.querySelectorAll("[data-class-public-link-id]").forEach((button) => {
      button.addEventListener("click", async () => {
        selectedClassId = button.dataset.classPublicLinkId;
        await copyPublicClassLink(button.dataset.classPublicLinkId);
      });
    });
    root.querySelectorAll("[data-class-close-enrollment-id]").forEach((button) => {
      button.addEventListener("click", async () => {
        await closeEnrollmentForClass(button.dataset.classCloseEnrollmentId);
      });
    });
    root.querySelectorAll("[data-class-stop-id]").forEach((button) => {
      button.addEventListener("click", async () => {
        await stopSelectedClass(button.dataset.classStopId);
      });
    });
    root.querySelector("[data-student-class-filter]")?.addEventListener("change", (event) => {
      selectedStudentClassId = event.currentTarget.value;
      students = [];
      render();
      loadStudents();
    });
    root.querySelector("[data-attendance-class-filter]")?.addEventListener("change", (event) => {
      selectedAttendanceClassId = event.currentTarget.value;
      attendanceGrid = null;
      render();
      loadAttendanceGrid();
    });
    root.querySelector("[data-attendance-refresh]")?.addEventListener("click", () => {
      attendanceGrid = null;
      render();
      loadAttendanceGrid();
    });
    root.querySelectorAll("[data-attendance-view]").forEach((button) => {
      button.addEventListener("click", () => {
        adminAttendanceView = button.dataset.attendanceView === "calendar" ? "calendar" : "table";
        render();
      });
    });
    root.querySelectorAll("[data-class-enroll-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedClassId = button.dataset.classEnrollId;
        openEnrollmentModal();
      });
    });
    root.querySelectorAll("[data-family-register-child-id]").forEach((button) => {
      button.addEventListener("click", async () => {
        await handleFamilyRegisterClick(button);
      });
    });
    root.querySelectorAll("[data-child-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedChildId = button.dataset.childId;
        notice = "";
        error = "";
        render();
      });
    });
    root.querySelectorAll("[data-child-edit-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedChildId = button.dataset.childEditId;
        activeOperation = "Edit child";
        notice = "";
        error = "";
        render();
      });
    });
    root.querySelectorAll("[data-enrollment-detail-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedEnrollmentId = button.dataset.enrollmentDetailId;
        notice = "";
        error = "";
        render();
        loadAttendance();
      });
    });
    root.querySelector("[data-enrollment-detail-close]")?.addEventListener("click", () => {
      selectedEnrollmentId = "";
      notice = "";
      error = "";
      render();
    });

    initializeGooglePlacesAutocomplete(root);
    initializeAttendanceGridTable(root);
    root.querySelector("[data-gmail-connect]")?.addEventListener("click", connectGmail);
  }

  function scheduleNoticeDismissal() {
    if (noticeTimer) {
      clearTimeout(noticeTimer);
      noticeTimer = null;
    }
    if (!notice) {
      return;
    }
    noticeTimer = window.setTimeout(() => {
      notice = "";
      noticeTimer = null;
      render();
    }, 3200);
  }

  function renderSchoolAdminLanding() {
    root.innerHTML = `
      <main class="admin-choice-page">
        <section class="admin-choice-shell">
          <header class="app-header">
            <div>
              <h2>${escapeHtml(school.name)} admin</h2>
              <p>Choose whether you want to manage the school setup or work inside a specific site.</p>
            </div>
            ${profileMenu(user)}
          </header>

          ${error ? `<p class="message error" role="alert">${escapeHtml(error)}</p>` : ""}

          <div class="admin-choice-grid">
            <button class="admin-choice-card" data-admin-mode="manage" type="button">
              <span>Manage school setup</span>
              <strong>Manage sites and teachers</strong>
              <small>Create or edit sites, choose a site to log on, and manage teacher invitations.</small>
            </button>
            <section class="admin-choice-card admin-site-card">
              <span>Site workspace</span>
              <strong>Choose a site and enter it</strong>
              <div class="admin-site-list" aria-label="Sites">
                ${
                  sites.length
                    ? sites
                        .map(
                          (site) => `
                            <button data-site-login-id="${escapeHtml(site.id)}" type="button">
                              <span>${escapeHtml(site.name)}</span>
                              <small>${escapeHtml(site.city && site.state ? `${site.city}, ${site.state}` : site.timezone)}</small>
                            </button>
                          `
                        )
                        .join("")
                    : `<p>${escapeHtml(loadingSites ? "Loading sites..." : "No sites have been created yet.")}</p>`
                }
              </div>
            </section>
            <button class="admin-choice-card" data-admin-mode="checkIn" type="button">
              <span>Check in</span>
              <strong>Open Camera Check-In</strong>
              <small>Open the check-in flow and start the camera from the next screen.</small>
            </button>
            <button class="admin-choice-card" data-admin-mode="inviteUser" type="button">
              <span>Invite New User</span>
              <strong>Open invite page</strong>
              <small>Invite school administrators, teachers, or parents from one place.</small>
            </button>
            <button class="admin-choice-card" data-admin-mode="externalAttendance" type="button">
              <span>External attendance</span>
              <strong>Check external student attendance</strong>
              <small>Choose a site and class, then open the attendance calendar.</small>
            </button>
          </div>
          ${profileOpen ? profilePanel(profile, user, loadingProfile) : ""}
          ${noticeToast(notice)}
        </section>
      </main>
    `;
    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-profile-menu-toggle]")?.addEventListener("click", () => {
      const menu = root.querySelector("[data-profile-menu]");
      menu?.toggleAttribute("hidden");
    });
    root.querySelector("[data-profile-action='profile']")?.addEventListener("click", () => {
      openProfilePanel();
    });
    root.querySelector("[data-profile-close]")?.addEventListener("click", closeProfilePanel);
    root.querySelector("[data-profile-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeProfilePanel();
      }
    });
    root.querySelector("[data-profile-form]")?.addEventListener("submit", handleProfileSubmit);
    root.querySelector("[data-password-form]")?.addEventListener("submit", handlePasswordSubmit);
    root.querySelectorAll("[data-admin-mode]").forEach((button) => {
      button.addEventListener("click", () => {
        adminMode = button.dataset.adminMode;
        activeSectionId = adminMode === "site" ? "programs" : "sites";
        activeOperation = "";
        notice = "";
        error = "";
        if (adminMode !== "checkIn") {
          checkInFlowStage = "intro";
          checkInImportFile = null;
          checkInImportMessage = "";
          checkInImportError = "";
          checkInImportSubmitting = false;
        }
        render();
        if (adminMode === "checkIn" && selectedSiteId && !loadingClasses) {
          loadClasses();
        }
      });
    });
    root.querySelectorAll("[data-site-login-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedSiteId = button.dataset.siteLoginId;
        adminMode = "site";
        activeSectionId = "programs";
        activeOperation = "";
        resetSiteWorkspaceData();
        notice = `Logged on to ${selectedSite()?.name || "site"}.`;
        error = "";
        render();
        loadPrograms();
        loadClasses();
      });
    });
  }

  function renderSchoolAdminInviteUser() {
    root.innerHTML = `
      <main class="admin-invite-page">
        <section class="admin-invite-shell" aria-labelledby="admin-invite-title">
          <header class="admin-invite-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2 id="admin-invite-title">Invite New User</h2>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-admin-mode="" type="button">Back</button>
              <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
            </div>
          </header>

          <section class="admin-invite-panel">
            <div class="workspace-heading">
              <h3>Invite user</h3>
              <p>Choose a role, enter one or more email addresses, and send invitations from this page.</p>
            </div>

            ${inviteUserError ? `<p class="message error" role="alert">${escapeHtml(inviteUserError)}</p>` : ""}
            ${inviteUserMessage ? `<p class="message success" role="status">${escapeHtml(inviteUserMessage)}</p>` : ""}

            <form class="invite-user-form" data-invite-user-form>
              <div class="invite-user-grid">
                <label>
                  <span>Role</span>
                  <select data-invite-user-role name="role">
                    <option value="SCHOOL_ADMIN" ${inviteUserRole === "SCHOOL_ADMIN" ? "selected" : ""}>School Administrator</option>
                    <option value="TEACHER" ${inviteUserRole === "TEACHER" ? "selected" : ""}>Teacher</option>
                    <option value="PARENT" ${inviteUserRole === "PARENT" ? "selected" : ""}>Parent</option>
                  </select>
                </label>

                <label class="invite-site-field" data-invite-site-field ${inviteUserRole === "TEACHER" ? "" : "hidden"}>
                  <span>Site</span>
                  <select data-invite-user-site name="siteId" ${inviteUserRole === "TEACHER" ? "" : "disabled"}>
                    <option value="">Choose a site</option>
                    ${sites.map((site) => `
                      <option value="${escapeHtml(site.id)}" ${inviteUserSiteId === site.id ? "selected" : ""}>
                        ${escapeHtml(site.name)}
                      </option>
                    `).join("")}
                  </select>
                </label>

                <label class="invite-class-field" data-invite-class-field ${inviteUserRole === "TEACHER" ? "" : "hidden"}>
                  <span>Class</span>
                  <select data-invite-user-class name="classId" ${inviteUserRole === "TEACHER" ? "" : "disabled"}>
                    <option value="">${loadingInviteUserClasses ? "Loading classes..." : "Choose a class"}</option>
                    ${inviteUserClasses.map((classRecord) => `
                      <option value="${escapeHtml(classRecord.id)}" ${inviteUserClassId === classRecord.id ? "selected" : ""}>
                        ${escapeHtml(classRecord.name)}
                      </option>
                    `).join("")}
                  </select>
                </label>
              </div>

              <label class="invite-email-field">
                <span>Email addresses</span>
                <textarea
                  data-invite-user-emails
                  name="emails"
                  placeholder="Enter one email per line"
                  rows="5"
                  required
                >${escapeHtml(inviteUserEmails)}</textarea>
              </label>

              <p class="invite-user-hint" data-invite-user-hint></p>

              <div class="operation-actions">
                <button data-invite-user-submit type="submit" ${inviteUserPasswordResetSubmitting ? "disabled" : ""}>${inviteUserSubmitting ? "Sending..." : "Send invitation"}</button>
                <button
                  class="secondary-button"
                  data-password-reset-submit
                  type="button"
                  ${inviteUserRole === "PARENT" || inviteUserSubmitting || inviteUserPasswordResetSubmitting ? "disabled" : ""}
                >${inviteUserPasswordResetSubmitting ? "Sending reset..." : "Send password reset email"}</button>
              </div>
            </form>

            <div class="invite-result-list" data-invite-user-results>
              ${
                inviteUserResults.length
                  ? inviteUserResults.map((result) => `
                    <div class="invite-result-row">
                      <strong>${escapeHtml(result.email)}</strong>
                      <span>${escapeHtml(inviteUserResultHeading(result))}</span>
                      <small>${escapeHtml(result.message)}</small>
                    </div>
                  `).join("")
                  : ""
              }
            </div>

            <div class="invite-delete-user-panel">
              <div class="workspace-heading">
                <h3>Delete user</h3>
                <p>Remove a user from this school by email. Their access is deactivated and school-specific assignments are cleared.</p>
              </div>

              ${deleteUserError ? `<p class="message error" role="alert">${escapeHtml(deleteUserError)}</p>` : ""}
              ${deleteUserMessage ? `<p class="message success" role="status">${escapeHtml(deleteUserMessage)}</p>` : ""}

              <form class="invite-delete-user-form" data-delete-user-form>
                <label class="invite-email-field">
                  <span>Email address</span>
                  <input
                    data-delete-user-email
                    name="email"
                    type="email"
                    value="${escapeHtml(deleteUserEmail)}"
                    placeholder="Enter the user email"
                    required
                  />
                </label>
                <div class="operation-actions">
                <button class="danger-button" data-delete-user-submit type="submit" ${currentUserEmail && deleteUserEmail.trim().toLowerCase() === currentUserEmail ? "disabled" : ""}>${deleteUserSubmitting ? "Deleting..." : "Delete user"}</button>
                </div>
              </form>
            </div>
          </section>
        </section>
      </main>
    `;

    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-admin-mode]")?.addEventListener("click", () => {
      adminMode = "";
      inviteUserMessage = "";
      inviteUserError = "";
      inviteUserResults = [];
      render();
    });
    root.querySelector("[data-invite-user-role]")?.addEventListener("change", (event) => {
      inviteUserRole = event.currentTarget.value;
      inviteUserMessage = "";
      inviteUserError = "";
      inviteUserResults = [];
      if (inviteUserRole === "TEACHER") {
        inviteUserSiteId = inviteUserSiteId || selectedSiteId || sites[0]?.id || "";
        loadInviteUserClasses();
      } else {
        inviteUserClassId = "";
        inviteUserClasses = [];
        inviteUserClassesLoadedForSiteId = "";
      }
      render();
    });
    root.querySelector("[data-invite-user-site]")?.addEventListener("change", (event) => {
      inviteUserSiteId = event.currentTarget.value;
      inviteUserClassId = "";
      inviteUserClasses = [];
      inviteUserClassesLoadedForSiteId = "";
      inviteUserMessage = "";
      inviteUserError = "";
      inviteUserResults = [];
      loadInviteUserClasses();
      render();
    });
    root.querySelector("[data-invite-user-class]")?.addEventListener("change", (event) => {
      inviteUserClassId = event.currentTarget.value;
      inviteUserMessage = "";
      inviteUserError = "";
      render();
    });
    root.querySelector("[data-invite-user-emails]")?.addEventListener("input", (event) => {
      inviteUserEmails = event.currentTarget.value;
      renderInviteUserHint();
    });
    root.querySelector("[data-invite-user-form]")?.addEventListener("submit", handleInviteUserSubmit);
    root.querySelector("[data-password-reset-submit]")?.addEventListener("click", handleInvitePasswordResetSubmit);
    root.querySelector("[data-delete-user-form]")?.addEventListener("submit", handleDeleteUserSubmit);
    root.querySelector("[data-delete-user-email]")?.addEventListener("input", (event) => {
      deleteUserEmail = event.currentTarget.value;
      deleteUserError = "";
      deleteUserMessage = "";
    });
    initializeInviteUserPage();
  }

  function renderSiteLoginScreen() {
    const rows = rowsFor({ id: "sites" });
    root.innerHTML = `
      <main class="admin-choice-page">
        <section class="admin-choice-shell">
          <header class="app-header">
            <div>
              <h2>Log on to a site</h2>
              <p>Select the site you want to operate. Site-level menus appear after this choice.</p>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-admin-mode="" type="button">Back</button>
              ${profileMenu(user)}
            </div>
          </header>

          ${error ? `<p class="message error" role="alert">${escapeHtml(error)}</p>` : ""}

          <section class="workspace-panel">
            <div class="workspace-heading">
              <h3>Available sites</h3>
              <p>${escapeHtml(loadingSites ? "Loading sites..." : "Choose a site to continue.")}</p>
            </div>
            ${sites.length ? siteLoginList() : dataList({ id: "site-login" }, rows)}
          </section>
          ${profileOpen ? profilePanel(profile, user, loadingProfile) : ""}
          ${noticeToast(notice)}
        </section>
      </main>
    `;
    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-profile-menu-toggle]")?.addEventListener("click", () => {
      const menu = root.querySelector("[data-profile-menu]");
      menu?.toggleAttribute("hidden");
    });
    root.querySelector("[data-profile-action='profile']")?.addEventListener("click", () => {
      openProfilePanel();
    });
    root.querySelector("[data-profile-close]")?.addEventListener("click", closeProfilePanel);
    root.querySelector("[data-profile-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeProfilePanel();
      }
    });
    root.querySelector("[data-profile-form]")?.addEventListener("submit", handleProfileSubmit);
    root.querySelector("[data-password-form]")?.addEventListener("submit", handlePasswordSubmit);
    initializeDirtyForms(root);
    root.querySelector("[data-admin-mode]")?.addEventListener("click", () => {
      adminMode = "";
      activeSectionId = "sites";
      activeOperation = "";
      notice = "";
      error = "";
      render();
    });
    root.querySelectorAll("[data-site-login-id]").forEach((button) => {
      button.addEventListener("click", () => {
        selectedSiteId = button.dataset.siteLoginId;
        activeSectionId = "programs";
        resetSiteWorkspaceData();
        notice = `Logged on to ${selectedSite()?.name || "site"}.`;
        error = "";
        render();
        loadPrograms();
        loadClasses();
      });
    });
  }

  function renderSchoolAdminCheckIn() {
    const currentSite = selectedSite();
    const currentClass = selectedClass();
    root.innerHTML = `
      <main class="admin-check-in-page">
        <section class="admin-check-in-shell" aria-labelledby="admin-check-in-title">
          <header class="admin-check-in-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2 id="admin-check-in-title">Check in</h2>
              <p class="check-in-context-line">
                ${escapeHtml(currentSite ? `${currentSite.name}` : "Choose a site")}
                ${currentClass ? ` / ${escapeHtml(currentClass.name)}` : ""}
              </p>
            </div>
            <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
          </header>

          <section class="check-in-camera-panel">
            <aside class="check-in-result-panel" aria-live="polite">
              <p class="check-in-result-site">${escapeHtml(currentSite ? `Site: ${currentSite.name}` : "No site selected")}${currentClass ? ` - Class: ${currentClass.name}` : ""}</p>
              <div class="check-in-status-block">
                <span class="check-in-result-label">Barcode status</span>
                <p class="check-in-status" data-check-in-status>${escapeHtml(checkInScannerStatus)}</p>
              </div>
              <div class="check-in-status-block">
                <span class="check-in-result-label">Manual check-in</span>
                <form class="check-in-manual-form" data-check-in-manual-form novalidate>
                  <input
                    class="text-input"
                    data-check-in-manual-input
                    type="text"
                    inputmode="numeric"
                    autocomplete="off"
                    placeholder="Student ID"
                    value="${escapeHtml(checkInManualStudentId)}"
                  >
                  <button type="submit" class="secondary-button compact-button" data-check-in-manual-submit>Submit</button>
                </form>
              </div>
              <div class="check-in-status-block">
                <span class="check-in-result-label">Today's check-ins</span>
                <p class="check-in-status">${escapeHtml(renderTodayCheckInCountText())}</p>
              </div>
            </aside>
            <div class="check-in-video-frame">
              <video autoplay muted playsinline data-check-in-video></video>
              <div class="check-in-target" aria-hidden="true"></div>
            </div>
          </section>
        </section>
      </main>
    `;

    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    bindManualCheckInForm();
    startCheckInScanner();
  }

  function renderSchoolAdminCheckInIntro() {
    if (!loadingSites && !sites.length) {
      loadSites();
    }
    const selectedSiteRecord = selectedSite();
    const selectedClassRecord = selectedClass();
    const todayValue = formatLocalDateForTimezone(new Date(), selectedSiteRecord?.timezone);
    const classIsScheduledToday = Boolean(selectedClassRecord && isScheduledClassDate(selectedClassRecord, todayValue));
    root.innerHTML = `
      <main class="admin-choice-page check-in-intro-page">
        <section class="admin-choice-shell" aria-labelledby="admin-check-in-title">
          <header class="app-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2 id="admin-check-in-title">Check in</h2>
              <p class="context-note check-in-context-note">
                ${escapeHtml(selectedSiteRecord ? `Site: ${selectedSiteRecord.name}` : "Select a site and class before starting the camera check-in flow.")}
                ${selectedClassRecord ? ` Current class: ${selectedClassRecord.name}.` : ""}
              </p>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-admin-mode="" type="button">Back</button>
              <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
            </div>
          </header>

          <section class="standalone-panel check-in-launch-panel">
            <div class="check-in-action-group check-in-camera-card">
              <div class="check-in-action-group-header">
                <h3>Camera check-in</h3>
                <p>Select a site and class, then start the camera or check students in from the list.</p>
              </div>
              <div class="check-in-selector-grid">
                <label class="check-in-selector-field">
                  <span>Site</span>
                  <select data-check-in-site-select ${loadingSites ? "disabled" : ""}>
                    <option value="">Select site</option>
                    ${sites.map((site) => `<option value="${escapeHtml(site.id)}"${site.id === selectedSiteId ? " selected" : ""}>${escapeHtml(site.name)}</option>`).join("")}
                  </select>
                </label>
                <label class="check-in-selector-field">
                  <span>Class</span>
                  <select data-check-in-class-select ${!selectedSiteId || loadingClasses ? "disabled" : ""}>
                    <option value="">Select class</option>
                    ${classes.map((classRecord) => `<option value="${escapeHtml(classRecord.id)}"${classRecord.id === selectedClassId ? " selected" : ""}>${escapeHtml(classRecord.name)}</option>`).join("")}
                  </select>
                </label>
              </div>
              ${checkInSelectionError ? `<p class="message error" role="alert">${escapeHtml(checkInSelectionError)}</p>` : ""}
              <p class="check-in-today-count">${escapeHtml(renderTodayCheckInCountText())}</p>
              ${selectedClassRecord && !classIsScheduledToday ? `<p class="message error" role="alert">No class is scheduled today for the selected class.</p>` : ""}
              <div class="check-in-launch-actions">
                <button class="check-in-launch-button" data-check-in-start type="button" ${!selectedSiteId || !selectedClassId || !classIsScheduledToday ? "disabled" : ""}>Check In</button>
                <button class="secondary-button compact-button" data-check-in-quick-toggle type="button" ${!selectedSiteId || !selectedClassId || !classIsScheduledToday ? "disabled" : ""}>
                  Quick check-in
                </button>
                <button class="secondary-button compact-button" data-check-in-today-list type="button" ${!selectedSiteId || !selectedClassId ? "disabled" : ""}>Today's check-in list</button>
                <button class="secondary-button compact-button" data-check-in-print-sheet type="button" ${!selectedSiteId || !selectedClassId ? "disabled" : ""}>Print check-out sheet</button>
              </div>
            </div>

            <div class="check-in-action-group">
              <div class="check-in-action-group-header">
                <h3>Import students</h3>
                <p>Upload CSV or Excel files into external students.</p>
              </div>
              <form class="check-in-import-form" data-check-in-import-form>
                <label class="check-in-import-field">
                  <span>File</span>
                  <input
                    accept=".csv,.xlsx,.xls,.xlsm,text/csv,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    data-check-in-import-file
                    type="file"
                  />
                </label>
                <p class="check-in-import-file-name" data-check-in-import-file-name>${escapeHtml(checkInImportFile?.name || "No file selected")}</p>
                ${checkInImportError ? `<p class="message error" role="alert">${escapeHtml(checkInImportError)}</p>` : ""}
                ${checkInImportMessage ? `<p class="message success" role="status">${escapeHtml(checkInImportMessage)}</p>` : ""}
                <div class="check-in-launch-actions">
                  <button class="secondary-button compact-button" data-check-in-import-submit type="submit">
                    ${checkInImportSubmitting ? "Importing..." : "Import file"}
                  </button>
                </div>
              </form>
            </div>

            <div class="check-in-action-group check-in-action-group-compact">
              <div class="check-in-action-group-header">
                <h3>Student list</h3>
                <p>Open the spreadsheet view of imported students.</p>
              </div>
              <div class="check-in-launch-actions">
                <button class="secondary-button compact-button" data-check-in-show-all type="button">
                  ${checkInStudentsLoading ? "Loading..." : "Show all"}
                </button>
              </div>
            </div>
          </section>
        </section>
      </main>
      ${checkInStudentsOpen ? renderCheckInStudentsModal() : ""}
      ${checkInQuickListOpen ? renderQuickCheckInList() : ""}
      ${checkInTodayListOpen ? renderCheckInTodayListModal() : ""}
    `;

    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-admin-mode]")?.addEventListener("click", () => {
      adminMode = "";
      checkInFlowStage = "intro";
      checkInSelectionError = "";
      resetQuickCheckInState();
      render();
    });
    root.querySelector("[data-check-in-site-select]")?.addEventListener("change", (event) => {
      selectedSiteId = event.currentTarget.value;
      selectedClassId = "";
      checkInSelectionError = "";
      checkInTodayListRows = [];
      checkInTodayListCount = 0;
      checkInTodayListQueryKey = "";
      checkInTodayListDate = "";
      resetQuickCheckInState();
      render();
      if (selectedSiteId) {
        loadClasses();
      }
    });
    root.querySelector("[data-check-in-class-select]")?.addEventListener("change", (event) => {
      selectedClassId = event.currentTarget.value;
      checkInSelectionError = "";
      checkInTodayListRows = [];
      checkInTodayListCount = 0;
      checkInTodayListQueryKey = "";
      checkInTodayListDate = "";
      resetQuickCheckInState();
      render();
      if (selectedSiteId && selectedClassId) {
        loadTodayCheckIns();
      }
    });
    root.querySelector("[data-check-in-start]")?.addEventListener("click", () => {
      if (!selectedSiteId || !selectedClassId) {
        checkInSelectionError = "Select a site and class before starting check-in.";
        render();
        return;
      }
      const classRecord = selectedClass();
      const todayValue = formatLocalDateForTimezone(new Date(), selectedSite()?.timezone);
      if (classRecord && !isScheduledClassDate(classRecord, todayValue)) {
        checkInSelectionError = "No class is scheduled today for the selected class.";
        render();
        return;
      }
      checkInManualStudentId = "";
      resetQuickCheckInState();
      unlockCheckInAudio();
      unlockCheckInSpeech();
      checkInFlowStage = "camera";
      render();
    });
    root.querySelector("[data-check-in-today-list]")?.addEventListener("click", handleShowTodayCheckIns);
    root.querySelector("[data-check-in-print-sheet]")?.addEventListener("click", handlePrintCheckOutSheet);
    root.querySelector("[data-check-in-show-all]")?.addEventListener("click", handleShowCheckInStudents);
    bindQuickCheckInControls();
    root.querySelector("[data-check-in-import-file]")?.addEventListener("change", (event) => {
      checkInImportFile = event.currentTarget.files?.[0] || null;
      checkInImportError = "";
      checkInImportMessage = "";
      updateCheckInImportFileLabel();
    });
    root.querySelector("[data-check-in-import-form]")?.addEventListener("submit", handleCheckInImportSubmit);
    root.querySelector("[data-check-in-students-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeCheckInStudentsModal();
      }
    });
    root.querySelector("[data-check-in-students-close]")?.addEventListener("click", closeCheckInStudentsModal);
    root.querySelector("[data-check-in-students-print]")?.addEventListener("click", handlePrintSelectedStudentCards);
    root.querySelector("[data-check-in-student-qr-modal]")?.addEventListener("click", closeCheckInStudentQrModal);
    root.querySelector("[data-check-in-student-qr-close]")?.addEventListener("click", closeCheckInStudentQrModal);
    root.querySelector("[data-check-in-today-list-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeTodayCheckInsModal();
      }
    });
    root.querySelector("[data-check-in-today-list-close]")?.addEventListener("click", closeTodayCheckInsModal);
    root.querySelector("[data-check-in-quick-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeQuickCheckInList();
      }
    });
    initializeCheckInStudentsTabulator();
    initializeQuickCheckInTabulator();
  }

  function handleLogout() {
    stopCheckInScanner();
    stopCheckInPeriodicRefresh();
    onLogout();
  }

  async function handleCheckInImportSubmit(event) {
    event.preventDefault();
    if (!checkInImportFile) {
      checkInImportError = "Choose a CSV or Excel file first.";
      checkInImportMessage = "";
      render();
      return;
    }

    checkInImportSubmitting = true;
    checkInImportError = "";
    checkInImportMessage = "";
    render();

    try {
      const response = await importExternalStudents({
        tenantId: school.tenantId,
        file: checkInImportFile,
      });
      const summaryParts = [
        `${response.importedCount || 0} imported`,
        `${response.updatedCount || 0} updated`,
        `${response.skippedCount || 0} skipped`,
      ];
      checkInImportMessage = `Import complete: ${summaryParts.join(", ")}.`;
      checkInImportFile = null;
      updateCheckInImportFileLabel();
      checkInStudents = [];
      if (checkInStudentsOpen) {
        await loadCheckInStudents();
      }
    } catch (importError) {
      checkInImportMessage = "";
      checkInImportError = importError instanceof Error ? importError.message : "Import could not be completed.";
    } finally {
      checkInImportSubmitting = false;
      render();
    }
  }

  async function handleShowCheckInStudents() {
    resetQuickCheckInState();
    checkInStudentsOpen = true;
    checkInStudentsError = "";
    checkInStudentsMessage = "";
    checkInStudentsPage = 1;
    render();
    await loadCheckInStudents();
  }

  function closeCheckInStudentsModal() {
    checkInStudentsOpen = false;
    checkInStudentsError = "";
    checkInStudentsMessage = "";
    checkInStudentQrModal = null;
    destroyCheckInStudentsTabulator();
    render();
  }

  async function handleQuickCheckInToggle() {
    if (checkInQuickListOpen) {
      closeQuickCheckInList();
      return;
    }
    checkInQuickListOpen = true;
    checkInQuickListError = "";
    checkInQuickListMessage = "";
    checkInStudentsError = "";
    checkInStudentsOpen = false;
    checkInStudentQrModal = null;
    destroyCheckInStudentsTabulator();
    render();
    const loaders = [];
    if (!checkInStudents.length && !checkInStudentsLoading) {
      loaders.push(loadCheckInStudents());
    }
    if (selectedClassId) {
      checkInTodayListDate = quickCheckInDate();
      loaders.push(loadTodayCheckIns({ force: true }));
    }
    await Promise.all(loaders);
  }

  function resetQuickCheckInState() {
    checkInQuickListOpen = false;
    checkInQuickListMessage = "";
    checkInQuickListError = "";
    checkInQuickSubmittingStudentId = "";
    destroyQuickCheckInTabulator();
  }

  function closeQuickCheckInList() {
    resetQuickCheckInState();
    render();
  }

  function syncCheckInPeriodicRefresh() {
    const shouldRefresh = Boolean(checkInQuickListOpen && selectedClassId);
    if (!shouldRefresh) {
      stopCheckInPeriodicRefresh();
      return;
    }
    if (checkInPeriodicRefreshTimer) {
      return;
    }
    checkInPeriodicRefreshTimer = window.setInterval(() => {
      if (!checkInQuickListOpen || !selectedClassId) {
        stopCheckInPeriodicRefresh();
        return;
      }
      checkInTodayListDate = quickCheckInDate();
      void loadTodayCheckIns({ force: true, background: true });
    }, CHECK_IN_PERIODIC_REFRESH_MS);
  }

  function stopCheckInPeriodicRefresh() {
    if (!checkInPeriodicRefreshTimer) {
      return;
    }
    clearInterval(checkInPeriodicRefreshTimer);
    checkInPeriodicRefreshTimer = null;
  }

  async function refreshQuickCheckInList() {
    checkInQuickListMessage = "";
    checkInQuickListError = "";
    checkInStudentsError = "";
    checkInStudents = [];
    checkInStudentsTotalRows = 0;
    checkInStudentsTotalPages = 1;
    checkInStudentsPage = 1;
    checkInTodayListRows = [];
    checkInTodayListCount = 0;
    checkInTodayListQueryKey = "";
    checkInTodayListDate = quickCheckInDate();
    render();
    await Promise.all([
      loadCheckInStudents(),
      selectedClassId ? loadTodayCheckIns({ force: true }) : Promise.resolve(),
    ]);
  }

  function renderQuickCheckInList() {
    const rowCount = checkInStudentsTotalRows || checkInStudents.length;
    const statusText = checkInStudentsLoading
      ? "Loading students..."
      : `${rowCount} student${rowCount === 1 ? "" : "s"} available.`;
    return `
      <div class="modal-backdrop" data-check-in-quick-modal>
        <section class="check-in-students-panel check-in-quick-panel" role="dialog" aria-modal="true" aria-labelledby="check-in-quick-title">
          <div class="workspace-heading workspace-heading-row">
            <div>
              <h3 id="check-in-quick-title">Quick check-in</h3>
              <p>${escapeHtml(statusText)}</p>
              <p class="check-in-selection-count">${escapeHtml(selectedClass() ? `Class: ${selectedClass().name}` : "Selected class")}</p>
            </div>
            <div class="check-in-students-actions">
              <button class="secondary-button compact-button" data-check-in-quick-refresh type="button" ${checkInStudentsLoading ? "disabled" : ""}>Refresh</button>
              <button class="secondary-button compact-button" data-check-in-quick-close type="button">Close</button>
            </div>
          </div>
          ${checkInQuickListError || checkInStudentsError ? `<p class="message error" role="alert">${escapeHtml(checkInQuickListError || checkInStudentsError)}</p>` : ""}
          ${checkInQuickListMessage ? `<p class="message success" role="status">${escapeHtml(checkInQuickListMessage)}</p>` : ""}
          <div class="check-in-students-table-shell">
            <div data-check-in-quick-tabulator class="check-in-students-tabulator"></div>
          </div>
        </section>
      </div>
    `;
  }

  function bindQuickCheckInControls() {
    root.querySelector("[data-check-in-quick-toggle]")?.addEventListener("click", () => {
      void handleQuickCheckInToggle();
    });
    root.querySelector("[data-check-in-quick-close]")?.addEventListener("click", closeQuickCheckInList);
    root.querySelector("[data-check-in-quick-refresh]")?.addEventListener("click", () => {
      void refreshQuickCheckInList();
    });
  }

  function quickCheckInDate() {
    return formatLocalDateForTimezone(new Date(), selectedSite()?.timezone);
  }

  function quickCheckInQueryKey() {
    return [selectedClassId || "", quickCheckInDate()].join(":");
  }

  function quickCheckInStatusLoaded() {
    return Boolean(selectedClassId && checkInTodayListQueryKey === quickCheckInQueryKey() && !checkInTodayListLoading);
  }

  function quickCheckedInStudentIds() {
    if (!quickCheckInStatusLoaded()) {
      return new Set();
    }
    return new Set(
      checkInTodayListRows
        .map((row) => String(row.externalStudentId || "").trim())
        .filter(Boolean)
    );
  }

  function applyLocalExternalCheckIn(response = {}, fallback = {}) {
    const externalStudentId = String(response.externalStudentId || fallback.externalStudentId || "").trim();
    if (!externalStudentId || !selectedClassId) {
      return;
    }
    const checkDate = String(response.checkDate || fallback.checkDate || quickCheckInDate());
    const classId = String(response.classId || selectedClassId);
    if (classId !== String(selectedClassId)) {
      return;
    }
    const row = {
      id: response.id || `local-${classId}-${checkDate}-${externalStudentId}`,
      seqId: response.seqId || null,
      externalStudentId,
      studentName: fallback.studentName || response.studentName || "",
      gender: fallback.gender || response.gender || "",
      classId,
      className: response.className || selectedClass()?.name || "",
      checkDate,
      checkInTime: response.checkInTime || new Date().toISOString(),
      checkedInByUserId: response.checkedInByUserId || "",
      checkedInByRole: response.checkedInByRole || role,
      status: response.status || "checked_in",
      barcodeValue: fallback.barcodeValue || response.barcodeValue || externalStudentId,
      createdAt: response.createdAt || new Date().toISOString(),
      updatedAt: response.updatedAt || new Date().toISOString(),
    };

    checkInTodayListDate = checkDate;
    checkInTodayListQueryKey = [selectedClassId, checkDate].join(":");
    checkInTodayListRows = [
      row,
      ...checkInTodayListRows.filter(
        (existing) => String(existing.externalStudentId || "").trim() !== externalStudentId
      ),
    ];
    checkInTodayListCount = checkInTodayListRows.length;
    checkInTodayListError = "";
  }

  async function handleQuickCheckInStudent(studentId) {
    const normalizedStudentId = String(studentId || "").trim();
    if (!normalizedStudentId) {
      checkInQuickListError = "Student ID is missing for this row.";
      checkInQuickListMessage = "";
      render();
      return;
    }
    if (!selectedClassId) {
      checkInQuickListError = role === "TEACHER"
        ? "Select a class before checking in external students."
        : "Select a site and class before checking in external students.";
      checkInQuickListMessage = "";
      render();
      return;
    }
    if (checkInExternalCheckInSubmitting) {
      return;
    }
    if (!quickCheckInStatusLoaded()) {
      checkInQuickListError = "Loading today's check-ins. Try again in a moment.";
      checkInQuickListMessage = "";
      render();
      return;
    }
    if (quickCheckedInStudentIds().has(normalizedStudentId)) {
      checkInQuickListError = "";
      checkInQuickListMessage = "This student is already checked in for this class today.";
      render();
      return;
    }

    const student = checkInStudents.find((row) => String(row.externalId || "").trim() === normalizedStudentId) || null;
    if (!student) {
      checkInQuickListError = "Student could not be found in the quick list.";
      checkInQuickListMessage = "";
      render();
      return;
    }

    const displayName = externalStudentDisplayName(student);
    const displayValue = formatExternalStudentBarcode({
      studentId: normalizedStudentId,
      name: displayName,
    });
    checkInQuickSubmittingStudentId = normalizedStudentId;
    checkInQuickListError = "";
    checkInQuickListMessage = "";
    render();

    const checkedIn = await submitExternalStudentCheckIn({
      externalStudentId: normalizedStudentId,
      displayValue,
      studentName: displayName,
      gender: student.genderCode || null,
      barcodeValue: normalizedStudentId,
    });

    checkInQuickSubmittingStudentId = "";
    if (checkedIn) {
      checkInQuickListMessage = `Checked in ${displayValue}.`;
      checkInQuickListError = "";
    } else {
      checkInQuickListMessage = "";
      checkInQuickListError = checkInScannerStatus || "Student could not be checked in.";
    }
    render();
  }

  function externalStudentDisplayName(student) {
    return student?.studentName
      || [student?.firstName, student?.lastName].filter(Boolean).join(" ").trim()
      || "Student";
  }

  async function handleShowTodayCheckIns() {
    if (!selectedClassId) {
      checkInSelectionError = role === "TEACHER"
        ? "Select a class before opening today's check-in list."
        : "Select a site and class before opening today's check-in list.";
      render();
      return;
    }
    checkInTodayListOpen = true;
    checkInTodayListError = "";
    checkInTodayListDate = formatLocalDateForTimezone(new Date(), selectedSite()?.timezone);
    render();
    window.setTimeout(() => {
      void loadTodayCheckIns();
    }, 0);
  }

  function closeTodayCheckInsModal() {
    checkInTodayListOpen = false;
    checkInTodayListLoading = false;
    checkInTodayListError = "";
    render();
  }

  function todayCheckInQueryKey() {
    return [selectedClassId || "", checkInTodayListDate || ""].join(":");
  }

  async function loadCheckInStudents() {
    if (checkInStudentsLoading) {
      return;
    }
    checkInStudentsLoading = true;
    render();
    try {
      const response = await listExternalStudents({ tenantId: school.tenantId });
      checkInStudents = response.students || [];
      checkInStudentsTotalRows = response.totalRows || 0;
      checkInStudentsPage = 1;
      checkInStudentsPageSize = checkInStudents.length || 25;
      checkInStudentsTotalPages = 1;
    } catch (loadError) {
      checkInStudentsError = loadError instanceof Error ? loadError.message : "Students could not be loaded.";
    } finally {
      checkInStudentsLoading = false;
      render();
      if (checkInStudentsOpen && !checkInStudentsError) {
        await initializeCheckInStudentsTabulator();
      }
      if (checkInQuickListOpen && !checkInStudentsError) {
        await initializeQuickCheckInTabulator();
      }
    }
  }

  async function loadTodayCheckIns({ force = false, background = false } = {}) {
    if (checkInTodayListLoading || !selectedClassId) {
      return;
    }
    const queryDate = checkInTodayListDate || formatLocalDateForTimezone(new Date(), selectedSite()?.timezone);
    checkInTodayListDate = queryDate;
    const classRecord = selectedClass();
    if (classRecord && !isScheduledClassDate(classRecord, queryDate)) {
      checkInTodayListRows = [];
      checkInTodayListCount = 0;
      checkInTodayListQueryKey = [selectedClassId, queryDate].join(":");
      checkInTodayListError = "";
      if (!background) {
        render();
      }
      return;
    }
    const currentQueryKey = [selectedClassId, queryDate].join(":");
    if (!force && checkInTodayListQueryKey === currentQueryKey && checkInTodayListRows.length && !checkInTodayListLoading) {
      return;
    }
    checkInTodayListLoading = true;
    checkInTodayListError = "";
    if (!background) {
      render();
    }
    try {
      const response = await listExternalCheckIns({
        tenantId: school.tenantId,
        classId: selectedClassId,
        checkDate: queryDate,
      });
      checkInTodayListRows = response.checkIns || [];
      checkInTodayListCount = checkInTodayListRows.length;
      checkInTodayListQueryKey = currentQueryKey;
    } catch (loadError) {
      checkInTodayListError = loadError instanceof Error ? loadError.message : "Today's check-in list could not be loaded.";
    } finally {
      checkInTodayListLoading = false;
      render();
    }
  }

  function updateCheckInImportFileLabel() {
    const label = root.querySelector("[data-check-in-import-file-name]");
    if (label) {
      label.textContent = checkInImportFile?.name || "No file selected";
    }
  }

  function updateCheckInSelectionCount(count) {
    checkInStudentsSelectedCount = count;
    const label = root.querySelector("[data-check-in-selection-count]");
    if (label) {
      label.textContent = `${count} student${count === 1 ? "" : "s"} selected.`;
    }
  }

  function renderCheckInStudentsModal() {
    return `
      <div class="modal-backdrop" data-check-in-students-modal>
        <section class="check-in-students-panel" role="dialog" aria-modal="true" aria-labelledby="check-in-students-title">
          <div class="workspace-heading workspace-heading-row">
            <div>
              <h3 id="check-in-students-title">External students</h3>
              <p>${escapeHtml(checkInStudentsLoading ? "Loading imported students..." : `${(checkInStudentsTotalRows || checkInStudents.length)} student${(checkInStudentsTotalRows || checkInStudents.length) === 1 ? "" : "s"}.`)}</p>
              <p class="check-in-selection-count" data-check-in-selection-count>${escapeHtml(`${checkInStudentsSelectedCount} student${checkInStudentsSelectedCount === 1 ? "" : "s"} selected.`)}</p>
            </div>
            <div class="check-in-students-actions">
              <button class="secondary-button compact-button" data-check-in-students-print type="button" ${checkInStudentsLoading || !checkInStudentsTabulator ? "disabled" : ""}>Print student card</button>
              <button class="secondary-button compact-button" data-check-in-students-close type="button">Close</button>
            </div>
          </div>
          ${checkInStudentsError ? `<p class="message error" role="alert">${escapeHtml(checkInStudentsError)}</p>` : ""}
          ${checkInStudentsMessage ? `<p class="message success" role="status">${escapeHtml(checkInStudentsMessage)}</p>` : ""}
          <div class="check-in-students-table-shell">
            <div data-check-in-students-tabulator class="check-in-students-tabulator"></div>
          </div>
        </section>
      </div>
      ${checkInStudentQrModal ? renderCheckInStudentQrModal() : ""}
    `;
  }

  function renderCheckInStudentQrModal() {
    const card = checkInStudentQrModal;
    return `
      <div class="modal-backdrop check-in-qr-backdrop" data-check-in-student-qr-modal>
        <section class="check-in-qr-panel" role="dialog" aria-modal="true" aria-labelledby="check-in-qr-title">
          <div class="workspace-heading workspace-heading-row">
            <div>
              <h3 id="check-in-qr-title">${escapeHtml(card.displayName)}</h3>
              <p>Student ID: ${escapeHtml(card.studentId || "-")}</p>
            </div>
            <button class="secondary-button compact-button" data-check-in-student-qr-close type="button">Close</button>
          </div>
          <div class="check-in-qr-code">
            <img alt="QR code for ${escapeHtml(card.displayName)}" src="${card.qrDataUrl}">
          </div>
          <p class="check-in-qr-detail">Gender: ${escapeHtml(card.gender || "-")}</p>
        </section>
      </div>
    `;
  }

  function renderCheckInTodayListModal() {
    const currentSite = selectedSite();
    const currentClass = selectedClass();
    return `
      <div class="modal-backdrop" data-check-in-today-list-modal>
        <section class="check-in-today-list-panel" role="dialog" aria-modal="true" aria-labelledby="check-in-today-list-title">
          <div class="workspace-heading workspace-heading-row">
            <div>
              <h3 id="check-in-today-list-title">Today's check-in list</h3>
              <p>${escapeHtml(currentClass ? currentClass.name : "Selected class")}${checkInTodayListDate ? ` - ${checkInTodayListDate}` : ""}</p>
              <p class="check-in-selection-count">${escapeHtml(currentSite ? `Site: ${currentSite.name}` : "")}</p>
            </div>
            <div class="check-in-students-actions">
              <button class="secondary-button compact-button" data-check-in-today-list-close type="button">Close</button>
            </div>
          </div>
          ${checkInTodayListError ? `<p class="message error" role="alert">${escapeHtml(checkInTodayListError)}</p>` : ""}
          <p class="check-in-today-count">${escapeHtml(renderTodayCheckInCountText())}</p>
          <div class="check-in-today-list-shell">
            ${checkInTodayListLoading ? `<p class="check-in-today-list-empty">Loading check-ins...</p>` : ""}
            ${!checkInTodayListLoading && !checkInTodayListRows.length ? `<p class="check-in-today-list-empty">No check-ins yet for this class today.</p>` : ""}
            ${checkInTodayListRows.length ? `
              <table class="check-in-today-list-table">
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Student</th>
                    <th>ID</th>
                    <th>Gender</th>
                  </tr>
                </thead>
                <tbody>
                  ${checkInTodayListRows.map((row) => `
                    <tr>
                      <td>${escapeHtml(formatCheckInTime(row.checkInTime))}</td>
                      <td>${escapeHtml(row.studentName || "-")}</td>
                      <td>${escapeHtml(row.externalStudentId || "-")}</td>
                      <td>${escapeHtml(row.gender || "-")}</td>
                    </tr>
                  `).join("")}
                </tbody>
              </table>
            ` : ""}
          </div>
        </section>
      </div>
    `;
  }

  function destroyCheckInStudentsTabulator() {
    if (checkInStudentsTabulator) {
      checkInStudentsTabulator.destroy();
      checkInStudentsTabulator = null;
    }
    updateCheckInSelectionCount(0);
  }

  function destroyQuickCheckInTabulator() {
    if (checkInQuickTabulator) {
      checkInQuickTabulator.destroy();
      checkInQuickTabulator = null;
    }
  }

  async function initializeCheckInStudentsTabulator() {
    const element = root.querySelector("[data-check-in-students-tabulator]");
    if (!element || !checkInStudentsOpen) {
      return;
    }

    const Tabulator = await loadTabulator();
    if (!element.isConnected || !checkInStudentsOpen) {
      return;
    }

    destroyCheckInStudentsTabulator();
    checkInStudentsTabulator = new Tabulator(element, {
      data: checkInStudents.map((student) => ({
        externalId: student.externalId || "",
        lastName: student.lastName || "",
        firstName: student.firstName || "",
        studentName: student.studentName || "",
        birthDate: student.birthDate || "",
        gradeLevelCode: student.gradeLevelCode || "",
        genderCode: student.genderCode || "",
      })),
      columns: [
        {
          formatter: "rowSelection",
          titleFormatter: "rowSelection",
          titleFormatterParams: { rowRange: "active" },
          hozAlign: "center",
          headerSort: false,
          width: 56,
        },
        {
          title: "QR",
          formatter: () => `<button class="secondary-button compact-button check-in-row-action" type="button">Show QR</button>`,
          headerSort: false,
          hozAlign: "center",
          width: 120,
          cellClick: (event, cell) => {
            event.stopPropagation();
            void handleShowStudentQrCode(cell.getRow().getData());
          },
        },
        { title: "StudentID", field: "externalId", headerFilter: "input", width: 150 },
        { title: "LastName", field: "lastName", headerFilter: "input", width: 150 },
        { title: "FirstName", field: "firstName", headerFilter: "input", width: 150 },
        { title: "StudentName", field: "studentName", headerFilter: "input", width: 220 },
        { title: "DOB", field: "birthDate", headerFilter: "input", width: 120 },
        { title: "GradeLevelCode", field: "gradeLevelCode", headerFilter: "input", width: 160 },
        { title: "GenderCode", field: "genderCode", headerFilter: "input", width: 140 },
      ],
      height: "100%",
      layout: "fitDataFill",
      placeholder: checkInStudentsLoading ? "Loading..." : "No external students imported yet.",
      movableColumns: true,
      selectableRows: true,
      columnDefaults: {
        vertAlign: "middle",
      },
    });
    checkInStudentsTabulator.on("rowSelectionChanged", (selectedData) => {
      updateCheckInSelectionCount(Array.isArray(selectedData) ? selectedData.length : 0);
    });
    updateCheckInSelectionCount(checkInStudentsTabulator.getSelectedData?.().length || 0);
  }

  async function initializeQuickCheckInTabulator() {
    const element = root.querySelector("[data-check-in-quick-tabulator]");
    if (!element || !checkInQuickListOpen) {
      return;
    }

    const Tabulator = await loadTabulator();
    if (!element.isConnected || !checkInQuickListOpen) {
      return;
    }

    destroyQuickCheckInTabulator();
    const statusLoaded = quickCheckInStatusLoaded();
    const checkedInStudentIds = quickCheckedInStudentIds();
    checkInQuickTabulator = new Tabulator(element, {
      data: checkInStudents.map((student) => ({
        externalId: student.externalId || "",
        lastName: student.lastName || "",
        firstName: student.firstName || "",
        studentName: student.studentName || "",
        birthDate: student.birthDate || "",
        gradeLevelCode: student.gradeLevelCode || "",
        genderCode: student.genderCode || "",
      })),
      columns: [
        {
          title: "Action",
          formatter: (cell) => {
            const row = cell.getRow().getData();
            const studentId = String(row.externalId || "").trim();
            const isSubmitting = Boolean(checkInQuickSubmittingStudentId && checkInQuickSubmittingStudentId === studentId);
            const alreadyCheckedIn = Boolean(studentId && checkedInStudentIds.has(studentId));
            const waitingForStatus = !statusLoaded;
            const disabled = !studentId || waitingForStatus || alreadyCheckedIn || isSubmitting || checkInExternalCheckInSubmitting;
            const label = alreadyCheckedIn
              ? "Checked in"
              : isSubmitting
                ? "Checking..."
                : waitingForStatus
                  ? "Loading..."
                  : "Check in";
            return `<button class="secondary-button compact-button check-in-row-action" type="button" ${disabled ? "disabled" : ""}>${label}</button>`;
          },
          headerSort: false,
          hozAlign: "center",
          width: 130,
          cellClick: (event, cell) => {
            event.stopPropagation();
            const button = event.target?.closest?.("button");
            if (!button || button.disabled) {
              return;
            }
            void handleQuickCheckInStudent(cell.getRow().getData().externalId || "");
          },
        },
        { title: "StudentID", field: "externalId", headerFilter: "input", width: 150 },
        { title: "LastName", field: "lastName", headerFilter: "input", width: 150 },
        { title: "FirstName", field: "firstName", headerFilter: "input", width: 150 },
        { title: "StudentName", field: "studentName", headerFilter: "input", width: 220 },
        { title: "DOB", field: "birthDate", headerFilter: "input", width: 120 },
        { title: "GradeLevelCode", field: "gradeLevelCode", headerFilter: "input", width: 160 },
        { title: "GenderCode", field: "genderCode", headerFilter: "input", width: 140 },
      ],
      height: "100%",
      layout: "fitDataFill",
      placeholder: checkInStudentsLoading ? "Loading..." : "No external students imported yet.",
      movableColumns: true,
      columnDefaults: {
        vertAlign: "middle",
      },
    });
  }

  async function handlePrintSelectedStudentCards() {
    if (!checkInStudentsTabulator) {
      checkInStudentsError = "Load the student list before printing cards.";
      render();
      return;
    }

    const selectedStudents = checkInStudentsTabulator.getSelectedData?.() || [];
    checkInStudentsSelectedCount = selectedStudents.length;
    if (!selectedStudents.length) {
      checkInStudentsError = "Select at least one student in the spreadsheet before printing.";
      checkInStudentsMessage = "";
      render();
      return;
    }

    checkInStudentsError = "";
    checkInStudentsMessage = `Preparing ${selectedStudents.length} card${selectedStudents.length === 1 ? "" : "s"} for printing...`;
    render();

    let printFrame;
    try {
      const cards = await Promise.all(
        selectedStudents.map((student) => createExternalStudentQrCard(student, 220)),
      );
      const cardsPerPage = 6;
      const cardPages = [];
      for (let index = 0; index < cards.length; index += cardsPerPage) {
        cardPages.push(cards.slice(index, index + cardsPerPage));
      }

      const styles = `
        <style>
          @page { size: auto; margin: 12mm; }
          :root { color: #172033; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
          body { margin: 0; padding: 0; }
          .sheet-page {
            break-after: page;
            display: grid;
            gap: 14px;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            grid-template-rows: repeat(3, minmax(0, 1fr));
            min-height: calc(100vh - 24mm);
            page-break-after: always;
          }
          .sheet-page:last-child { break-after: auto; page-break-after: auto; }
          .card { border: 1px solid #172033; border-radius: 10px; display: grid; gap: 12px; padding: 14px; break-inside: avoid; page-break-inside: avoid; }
          .title { font-size: 1.1rem; font-weight: 900; margin: 0; }
          .details { display: grid; gap: 6px; }
          .detail { font-size: 0.95rem; line-height: 1.35; }
          .detail strong { display: inline-block; min-width: 82px; }
          .qr { align-items: center; display: flex; justify-content: center; }
          .qr img { height: 160px; width: 160px; }
        </style>
      `;

      const html = `
        <!doctype html>
        <html>
          <head>
            <title>Student cards</title>
            ${styles}
          </head>
          <body>
            ${cardPages
              .map((pageCards, pageIndex) => `
                <div class="sheet-page">
                  ${pageCards
                    .map(
                      (card) => `
                        <section class="card">
                          <h1 class="title">${escapeHtml(card.displayName)}</h1>
                          <div class="details">
                            <div class="detail"><strong>Student ID</strong> ${escapeHtml(card.studentId || "-")}</div>
                            <div class="detail"><strong>Gender</strong> ${escapeHtml(card.gender || "-")}</div>
                          </div>
                          <div class="qr"><img alt="QR code for ${escapeHtml(card.displayName)}" src="${card.qrDataUrl}" /></div>
                        </section>
                      `,
                    )
                    .join("")}
                </div>
              `)
              .join("")}
          </body>
        </html>
      `;

      printFrame = document.createElement("iframe");
      printFrame.setAttribute("aria-hidden", "true");
      printFrame.style.position = "fixed";
      printFrame.style.width = "1px";
      printFrame.style.height = "1px";
      printFrame.style.border = "0";
      printFrame.style.left = "-9999px";
      printFrame.style.top = "0";
      printFrame.style.opacity = "0";
      document.body.appendChild(printFrame);

      const frameWindow = printFrame.contentWindow;
      const frameDocument = printFrame.contentDocument;
      if (!frameWindow || !frameDocument) {
        throw new Error("Student cards could not be prepared for printing.");
      }

      frameDocument.open();
      frameDocument.write(html);
      frameDocument.close();

      await new Promise((resolve, reject) => {
        const finish = async () => {
          try {
            const images = Array.from(frameDocument.images || []);
            await Promise.all(
              images.map((image) =>
                image.complete
                  ? Promise.resolve()
                  : new Promise((imageResolve, imageReject) => {
                      image.addEventListener("load", imageResolve, { once: true });
                      image.addEventListener("error", imageReject, { once: true });
                    }),
              ),
            );
            await new Promise((animationResolve) => frameWindow.requestAnimationFrame(() => frameWindow.requestAnimationFrame(animationResolve)));
            const cleanup = () => {
              frameWindow.removeEventListener("afterprint", cleanup);
              if (printFrame && printFrame.parentNode) {
                printFrame.parentNode.removeChild(printFrame);
              }
            };
            frameWindow.addEventListener("afterprint", cleanup, { once: true });
            frameWindow.focus();
            frameWindow.print();
            resolve();
          } catch (error) {
            reject(error);
          }
        };

        if (frameDocument.readyState === "complete") {
          finish();
        } else {
          printFrame.addEventListener("load", finish, { once: true });
        }
      });

      checkInStudentsMessage = `${selectedStudents.length} student card${selectedStudents.length === 1 ? "" : "s"} ready to print.`;
    } catch (printError) {
      checkInStudentsMessage = "";
      checkInStudentsError = printError instanceof Error ? printError.message : "Student cards could not be printed.";
    } finally {
      render();
    }
  }

  async function handleShowStudentQrCode(student) {
    try {
      checkInStudentQrModal = await createExternalStudentQrCard(student, 320);
      checkInStudentsError = "";
      render();
    } catch (qrError) {
      checkInStudentsError = qrError instanceof Error ? qrError.message : "QR code could not be generated.";
      checkInStudentQrModal = null;
      render();
    }
  }

  function closeCheckInStudentQrModal() {
    if (!checkInStudentQrModal) {
      return;
    }
    checkInStudentQrModal = null;
    render();
  }

  async function createExternalStudentQrCard(student, width) {
    const displayName = student.studentName || [student.firstName, student.lastName].filter(Boolean).join(" ") || "Student";
    const studentId = student.externalId || "";
    const gender = student.genderCode || "";
    const qrPayload = JSON.stringify({
      studentId,
      name: displayName,
      gender,
    });
    const qrDataUrl = await QRCode.toDataURL(qrPayload, {
      errorCorrectionLevel: "M",
      margin: 1,
      width,
    });
    return { displayName, studentId, gender, qrDataUrl };
  }

  async function handlePrintCheckOutSheet() {
    const classRecord = selectedClass();
    if (!classRecord || !selectedClassId) {
      checkInSelectionError = "Select a class before printing the check-out sheet.";
      render();
      return;
    }

    checkInSelectionError = "";
    checkInTodayListError = "";
    const printWindow = window.open("", "schooldays-checkout-print", "width=1100,height=900");
    const printWindowBlocked = !printWindow;
    if (!checkInTodayListRows.length || checkInTodayListQueryKey !== [selectedClassId, checkInTodayListDate || formatLocalDateForTimezone(new Date(), selectedSite()?.timezone)].join(":")) {
      await loadTodayCheckIns({ force: true });
    }

    const rows = [...checkInTodayListRows];
    if (!rows.length) {
      if (printWindow) {
        printWindow.close();
      }
      checkInTodayListError = "No students have checked in for this class today.";
      render();
      return;
    }

    const classDate = checkInTodayListDate || formatLocalDateForTimezone(new Date(), selectedSite()?.timezone);
    const siteName = selectedSite()?.name || "";
    const className = classRecord.name || "Selected class";
    const sheetRows = rows.map((row, index) => `
      <tr>
        <td>${index + 1}</td>
        <td>${escapeHtml(row.studentName || "-")}</td>
        <td>${escapeHtml(row.externalStudentId || "-")}</td>
        <td>${escapeHtml(row.gender || "-")}</td>
        <td>${escapeHtml(formatCheckInTime(row.checkInTime))}</td>
        <td><span class="signature-line"></span></td>
      </tr>
    `).join("");

    let printFrame;
    try {
      const html = `
        <!doctype html>
        <html>
          <head>
            <title>Check-out sheet</title>
            <style>
              @page { size: auto; margin: 12mm; }
              :root { color: #172033; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
              body { margin: 0; padding: 0; }
              .sheet { display: grid; gap: 14px; }
              .header { display: flex; justify-content: space-between; gap: 18px; align-items: flex-start; }
              .title { font-size: 1.35rem; font-weight: 900; margin: 0 0 6px; }
              .meta { display: grid; gap: 4px; font-size: 0.95rem; }
              .meta strong { font-weight: 800; }
              table { border-collapse: collapse; width: 100%; }
              th, td { border: 1px solid #172033; padding: 8px 10px; text-align: left; vertical-align: top; }
              th { background: #f4f7fb; font-size: 0.88rem; }
              td { font-size: 0.94rem; }
              .signature-cell { width: 24%; }
              .signature-line { border-bottom: 1px solid #172033; display: block; height: 18px; width: 100%; }
              .note { font-size: 0.84rem; margin: 0; }
            </style>
          </head>
          <body>
            <div class="sheet">
              <div class="header">
                <div>
                  <h1 class="title">${escapeHtml(school.name)} check-out sheet</h1>
                  <div class="meta">
                    <div><strong>Site:</strong> ${escapeHtml(siteName || "All sites")}</div>
                    <div><strong>Class:</strong> ${escapeHtml(className)}</div>
                    <div><strong>Date:</strong> ${escapeHtml(classDate)}</div>
                  </div>
                </div>
                <p class="note">Parents sign on the right after pickup.</p>
              </div>
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Student</th>
                    <th>ID</th>
                    <th>Gender</th>
                    <th>Check-in time</th>
                    <th class="signature-cell">Parent signature</th>
                  </tr>
                </thead>
                <tbody>
                  ${sheetRows}
                </tbody>
              </table>
            </div>
          </body>
        </html>
      `;

      let frameWindow = printWindowBlocked ? null : printWindow;
      let frameDocument = null;
      if (frameWindow) {
        frameDocument = frameWindow.document;
        frameDocument.open();
        frameDocument.write(html);
        frameDocument.close();
      } else {
        printFrame = document.createElement("iframe");
        printFrame.setAttribute("aria-hidden", "true");
        printFrame.style.position = "fixed";
        printFrame.style.width = "1px";
        printFrame.style.height = "1px";
        printFrame.style.border = "0";
        printFrame.style.left = "-9999px";
        printFrame.style.top = "0";
        printFrame.style.opacity = "0";
        document.body.appendChild(printFrame);

        frameWindow = printFrame.contentWindow;
        frameDocument = printFrame.contentDocument;
        if (!frameWindow || !frameDocument) {
          throw new Error("Check-out sheet could not be prepared for printing.");
        }

        frameDocument.open();
        frameDocument.write(html);
        frameDocument.close();
      }

      await new Promise((resolve, reject) => {
        const finish = async () => {
          try {
            await new Promise((animationResolve) => frameWindow.requestAnimationFrame(() => frameWindow.requestAnimationFrame(animationResolve)));
            const cleanup = () => {
              frameWindow.removeEventListener("afterprint", cleanup);
              if (printFrame && printFrame.parentNode) {
                printFrame.parentNode.removeChild(printFrame);
              }
            };
            frameWindow.addEventListener("afterprint", cleanup, { once: true });
            frameWindow.focus();
            frameWindow.print();
            resolve();
          } catch (error) {
            reject(error);
          }
        };

        if (frameDocument.readyState === "complete") {
          finish();
        } else {
          if (printFrame) {
            printFrame.addEventListener("load", finish, { once: true });
          } else {
            frameWindow.addEventListener("load", finish, { once: true });
          }
        }
      });

      checkInTodayListMessage = `${rows.length} student${rows.length === 1 ? "" : "s"} ready for parent sign-out.`;
      checkInTodayListError = "";
      render();
    } catch (printError) {
      checkInTodayListMessage = "";
      checkInTodayListError = printError instanceof Error ? printError.message : "Check-out sheet could not be printed.";
      render();
    }
  }

  function initializeInviteUserPage() {
    if (inviteUserRole === "TEACHER") {
      inviteUserSiteId = inviteUserSiteId || selectedSiteId || sites[0]?.id || "";
    } else if (!inviteUserSiteId) {
      inviteUserSiteId = selectedSiteId || sites[0]?.id || "";
    }
    if (inviteUserRole === "TEACHER" && inviteUserSiteId && inviteUserClassesLoadedForSiteId !== inviteUserSiteId) {
      loadInviteUserClasses();
    }
    renderInviteUserHint();
  }

  async function loadInviteUserClasses() {
    if (loadingInviteUserClasses || !school?.tenantId || !inviteUserSiteId) {
      inviteUserClasses = [];
      renderInviteUserHint();
      return;
    }
    loadingInviteUserClasses = true;
    renderInviteUserHint();
    try {
      const response = await listClasses(school.tenantId, inviteUserSiteId);
      inviteUserClasses = response.classes || [];
      inviteUserClassesLoadedForSiteId = inviteUserSiteId;
      if (inviteUserClassId && !inviteUserClasses.some((classRecord) => classRecord.id === inviteUserClassId)) {
        inviteUserClassId = "";
      }
      if (!inviteUserClassId) {
        inviteUserClassId = inviteUserClasses[0]?.id || "";
      }
    } catch (loadError) {
      inviteUserClasses = [];
      inviteUserError = loadError instanceof Error ? loadError.message : "Classes could not be loaded.";
    } finally {
      loadingInviteUserClasses = false;
      render();
    }
  }

  function renderInviteUserHint() {
    const hint = root.querySelector("[data-invite-user-hint]");
    if (!hint) {
      return;
    }
    hint.textContent = inviteUserHintText();
  }

  function inviteUserHintText() {
    if (inviteUserRole === "SCHOOL_ADMIN") {
      return "Invite one school administrator by email. Existing users are updated automatically.";
    }
    if (inviteUserRole === "TEACHER") {
      if (!inviteUserSiteId) {
        return "Choose a site before loading the class list.";
      }
      if (loadingInviteUserClasses) {
        return "Loading classes for the selected site...";
      }
      return "Invite up to 5 teachers and assign them to the selected class, or send reset links to existing teachers.";
    }
    if (inviteUserRole === "SCHOOL_ADMIN") {
      return "Invite one school administrator, or send reset links to existing school administrators.";
    }
    return "Invite any number of parents by email. Password reset from this screen is for teachers and school administrators.";
  }

  function inviteUserResultHeading(result) {
    if (!result) {
      return "";
    }
    return `${result.role || "USER"} - ${result.outcome || "processed"}`;
  }

  async function handleInviteUserSubmit(event) {
    event.preventDefault();

    const emails = splitEmails(inviteUserEmails);
    inviteUserMessage = "";
    inviteUserError = "";
    inviteUserResults = [];

    if (!emails.length) {
      inviteUserError = "Enter at least one email address.";
      render();
      return;
    }
    if (inviteUserRole === "SCHOOL_ADMIN" && emails.length > 1) {
      inviteUserError = "School administrator invitations accept one email address per request.";
      render();
      return;
    }
    if (inviteUserRole === "TEACHER") {
      if (emails.length > 5) {
        inviteUserError = "Teacher invitations are limited to 5 email addresses per request.";
        render();
        return;
      }
      if (!inviteUserSiteId) {
        inviteUserError = "Choose a site before inviting teachers.";
        render();
        return;
      }
      if (!inviteUserClassId) {
        inviteUserError = "Choose a class before inviting teachers.";
        render();
        return;
      }
    }

    inviteUserSubmitting = true;
    render();

    try {
      const response = await inviteUsers({
        tenantId: school.tenantId,
        role: inviteUserRole,
        emails,
        classId: inviteUserRole === "TEACHER" ? inviteUserClassId : null,
      });
      inviteUserResults = response.results || [];
      inviteUserMessage = inviteUserResults.length
        ? "Invitation request processed."
        : "Invitation request completed.";
      inviteUserEmails = "";
      if (inviteUserRole === "TEACHER" && inviteUserResults.length) {
        loadInviteUserClasses();
      }
    } catch (inviteError) {
      inviteUserMessage = "";
      inviteUserError = inviteError instanceof Error ? inviteError.message : "Invitation could not be sent.";
    } finally {
      inviteUserSubmitting = false;
      render();
    }
  }

  async function handleInvitePasswordResetSubmit() {
    const emails = splitEmails(inviteUserEmails);
    inviteUserMessage = "";
    inviteUserError = "";
    inviteUserResults = [];

    if (!["SCHOOL_ADMIN", "TEACHER"].includes(inviteUserRole)) {
      inviteUserError = "Password reset emails can only be sent for school administrators or teachers.";
      render();
      return;
    }
    if (!emails.length) {
      inviteUserError = "Enter at least one email address.";
      render();
      return;
    }
    if (emails.length > 5) {
      inviteUserError = "Password reset emails are limited to 5 email addresses per request.";
      render();
      return;
    }

    inviteUserPasswordResetSubmitting = true;
    render();

    try {
      const response = await sendPasswordResetLinks({
        tenantId: school.tenantId,
        role: inviteUserRole,
        emails,
      });
      inviteUserResults = response.results || [];
      inviteUserMessage = inviteUserResults.some((result) => result.outcome === "sent")
        ? "Password reset request processed."
        : "Password reset request completed.";
    } catch (resetError) {
      inviteUserMessage = "";
      inviteUserError = resetError instanceof Error ? resetError.message : "Password reset email could not be sent.";
    } finally {
      inviteUserPasswordResetSubmitting = false;
      render();
    }
  }

  async function handleDeleteUserSubmit(event) {
    event.preventDefault();

    const email = String(deleteUserEmail || "").trim();
    deleteUserError = "";
    deleteUserMessage = "";

    if (!email) {
      deleteUserError = "Enter an email address.";
      render();
      return;
    }
    if (currentUserEmail && email.toLowerCase() === currentUserEmail) {
      deleteUserError = "You cannot delete your own account.";
      render();
      return;
    }

    if (!window.confirm(`Delete ${email} from this school?`)) {
      return;
    }

    deleteUserSubmitting = true;
    render();

    try {
      const response = await deleteUser({
        tenantId: school.tenantId,
        email,
      });
      deleteUserMessage = response?.message || `${email} was removed.`;
      deleteUserEmail = "";
    } catch (deleteError) {
      deleteUserMessage = "";
      deleteUserError = deleteError instanceof Error ? deleteError.message : "User could not be deleted.";
    } finally {
      deleteUserSubmitting = false;
      render();
    }
  }

  async function startCheckInScanner() {
    if (checkInCameraStream) {
      attachCheckInCameraStream();
      if (checkInDetector && !checkInScanning) {
        checkInScanning = true;
        scheduleCheckInScan();
      }
      return;
    }
    if (checkInScannerStarting) {
      return;
    }

    if (!navigator.mediaDevices?.getUserMedia) {
      updateCheckInStatus("Camera access is not available in this browser.", true);
      return;
    }

    checkInScannerStarting = true;
    checkInBarcodeValue = "";
    checkInLastRawBarcodeValue = "";
    updateCheckInBarcodeValue("");
    updateCheckInStatus("Starting camera...");

    try {
      checkInCameraStream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          facingMode: { ideal: "environment" },
        },
      });
      attachCheckInCameraStream();
    } catch (cameraError) {
      stopCheckInScanner();
      updateCheckInStatus(scannerErrorMessage(cameraError), true);
      return;
    } finally {
      checkInScannerStarting = false;
    }

    if ("BarcodeDetector" in window) {
      try {
        checkInDetector = await createCheckInBarcodeDetector();
        checkInScannerMode = "native";
      } catch (detectorError) {
        checkInDetector = null;
        checkInScannerMode = "fallback";
      }
    } else {
      checkInDetector = null;
      checkInScannerMode = "fallback";
    }

    checkInScanning = true;
    updateCheckInStatus(checkInScannerMode === "fallback" ? "Scanning for QR codes..." : "Scanning for barcodes...");
    scheduleCheckInScan();
  }

  function attachCheckInCameraStream() {
    const video = root.querySelector("[data-check-in-video]");
    if (!video || !checkInCameraStream) {
      return;
    }
    if (video.srcObject !== checkInCameraStream) {
      video.srcObject = checkInCameraStream;
    }
    video.play().catch(() => {
      updateCheckInStatus("Camera video is ready but playback was blocked.", true);
    });
  }

  async function createCheckInBarcodeDetector() {
    const Detector = window.BarcodeDetector;
    const requestedFormats = [
      "aztec",
      "codabar",
      "code_39",
      "code_93",
      "code_128",
      "data_matrix",
      "ean_8",
      "ean_13",
      "itf",
      "pdf417",
      "qr_code",
      "upc_a",
      "upc_e",
    ];
    if (typeof Detector.getSupportedFormats !== "function") {
      return barcodeDetectorWithFormats(Detector, requestedFormats);
    }

    const supportedFormats = await Detector.getSupportedFormats();
    const formats = requestedFormats.filter((format) => supportedFormats.includes(format));
    return barcodeDetectorWithFormats(Detector, formats);
  }

  function barcodeDetectorWithFormats(Detector, formats) {
    try {
      return formats.length ? new Detector({ formats }) : new Detector();
    } catch (formatError) {
      return new Detector();
    }
  }

  async function scanCheckInFrame() {
    if (!checkInScanning) {
      return;
    }

    const video = root.querySelector("[data-check-in-video]");
    if (!video || video.readyState < 2) {
      scheduleCheckInScan();
      return;
    }

    try {
      let value = "";
      let format = "";
      if (checkInDetector) {
        const barcodes = await checkInDetector.detect(video);
        const barcode = barcodes[0] || null;
        value = barcode?.rawValue || "";
        format = barcode?.format || "";
      } else {
        value = detectQrFromVideoFrame(video);
        format = value ? "qr_code" : "";
      }
      if (value && value !== checkInLastRawBarcodeValue) {
        checkInLastRawBarcodeValue = value;
        if (format === "qr_code" || checkInScannerMode === "fallback") {
          await handleExternalStudentQrScan(value);
        } else {
          checkInBarcodeValue = value;
          updateCheckInBarcodeValue(value);
          updateCheckInStatus("Barcode detected.");
        }
      } else if (!checkInLastRawBarcodeValue) {
        updateCheckInStatus(checkInScannerMode === "fallback" ? "Scanning for QR codes..." : "Scanning for barcodes...");
      }
    } catch (scanError) {
      updateCheckInStatus("Barcode scanner could not read the camera frame.", true);
    }

    scheduleCheckInScan();
  }

  function scheduleCheckInScan() {
    if (!checkInScanning) {
      return;
    }
    if (checkInScannerTimer) {
      clearTimeout(checkInScannerTimer);
    }
    checkInScannerTimer = window.setTimeout(() => {
      checkInScannerTimer = null;
      scanCheckInFrame();
    }, 250);
  }

  function stopCheckInScanner() {
    checkInScanning = false;
    checkInScannerStarting = false;
    checkInDetector = null;
    checkInScannerMode = "native";
    checkInFallbackCanvas = null;
    checkInFallbackContext = null;
    checkInExternalCheckInSubmitting = false;
    if (checkInScannerTimer) {
      clearTimeout(checkInScannerTimer);
      checkInScannerTimer = null;
    }
    if (checkInCameraStream) {
      checkInCameraStream.getTracks().forEach((track) => track.stop());
      checkInCameraStream = null;
    }
    const video = root.querySelector("[data-check-in-video]");
    if (video) {
      video.srcObject = null;
    }
  }

  function detectQrFromVideoFrame(video) {
    const width = video.videoWidth || video.clientWidth;
    const height = video.videoHeight || video.clientHeight;
    if (!width || !height) {
      return "";
    }
    if (!checkInFallbackCanvas) {
      checkInFallbackCanvas = document.createElement("canvas");
      checkInFallbackContext = checkInFallbackCanvas.getContext("2d", { willReadFrequently: true });
    }
    if (!checkInFallbackCanvas || !checkInFallbackContext) {
      return "";
    }
    checkInFallbackCanvas.width = width;
    checkInFallbackCanvas.height = height;
    checkInFallbackContext.drawImage(video, 0, 0, width, height);
    const imageData = checkInFallbackContext.getImageData(0, 0, width, height);
    const code = jsQR(imageData.data, width, height, { inversionAttempts: "attemptBoth" });
    return code?.data || "";
  }

  function playCheckInSuccessSound() {
    const audioContext = ensureCheckInAudioContext();
    if (!audioContext) {
      return;
    }

    const now = audioContext.currentTime;
    const gain = audioContext.createGain();
    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(0.24, now + 0.04);
    gain.gain.exponentialRampToValueAtTime(0.18, now + 0.18);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.48);
    gain.connect(audioContext.destination);

    [880, 1175].forEach((frequency, index) => {
      const oscillator = audioContext.createOscillator();
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(frequency, now + index * 0.2);
      oscillator.connect(gain);
      oscillator.start(now + index * 0.2);
      oscillator.stop(now + 0.5);
      oscillator.onended = () => {
        oscillator.disconnect();
      };
    });
    window.setTimeout(() => {
      gain.disconnect();
    }, 600);
  }

  function playCheckInFailureSound() {
    const audioContext = ensureCheckInAudioContext();
    if (!audioContext) {
      return;
    }

    const now = audioContext.currentTime;
    const gain = audioContext.createGain();
    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(0.3, now + 0.03);
    gain.gain.exponentialRampToValueAtTime(0.22, now + 0.14);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.42);
    gain.connect(audioContext.destination);

    [220, 160].forEach((frequency, index) => {
      const oscillator = audioContext.createOscillator();
      oscillator.type = "square";
      oscillator.frequency.setValueAtTime(frequency, now + index * 0.16);
      oscillator.connect(gain);
      oscillator.start(now + index * 0.16);
      oscillator.stop(now + 0.38);
      oscillator.onended = () => {
        oscillator.disconnect();
      };
    });
    window.setTimeout(() => {
      gain.disconnect();
    }, 520);
  }

  function ensureCheckInAudioContext() {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) {
      return null;
    }
    if (!checkInAudioContext) {
      checkInAudioContext = new AudioContextClass();
    }
    if (checkInAudioContext.state === "suspended") {
      checkInAudioContext.resume().catch(() => {});
    }
    return checkInAudioContext;
  }

  function unlockCheckInAudio() {
    const audioContext = ensureCheckInAudioContext();
    if (!audioContext) {
      return;
    }
    const buffer = audioContext.createBuffer(1, 1, 22050);
    const source = audioContext.createBufferSource();
    source.buffer = buffer;
    source.connect(audioContext.destination);
    source.start(0);
    source.stop(0.001);
  }

  function unlockCheckInSpeech() {
    const speechSynthesis = window.speechSynthesis;
    const SpeechSynthesisUtteranceClass = window.SpeechSynthesisUtterance;
    if (!speechSynthesis || !SpeechSynthesisUtteranceClass || checkInSpeechUnlocked) {
      return;
    }

    try {
      speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtteranceClass("\u200B");
      utterance.lang = "en-US";
      utterance.volume = 0;
      speechSynthesis.speak(utterance);
      window.setTimeout(() => {
        speechSynthesis.cancel();
      }, 50);
      checkInSpeechUnlocked = true;
    } catch (_error) {
      // Ignore speech priming failures; the visible check-in flow still works.
    }
  }

  function speakCheckInSuccess(name) {
    const speechSynthesis = window.speechSynthesis;
    const SpeechSynthesisUtteranceClass = window.SpeechSynthesisUtterance;
    if (!speechSynthesis || !SpeechSynthesisUtteranceClass) {
      return;
    }

    try {
      speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtteranceClass(`${name}. Check in successfully.`);
      utterance.lang = "en-US";
      utterance.rate = 0.95;
      utterance.pitch = 1;
      utterance.volume = 1;
      speechSynthesis.speak(utterance);
    } catch (_error) {
      // Ignore speech synthesis failures; audio feedback still remains.
    }
  }

  function updateCheckInBarcodeValue(value) {
    const label = root.querySelector("[data-check-in-barcode-value]");
    if (label) {
      label.textContent = value || "Waiting for barcode";
    }
  }

  function formatExternalStudentBarcode(payload) {
    const studentId = String(payload?.studentId || payload?.externalStudentId || "").trim();
    const studentName = String(payload?.name || payload?.studentName || "").trim();
    if (studentName && studentId) {
      return `${studentName} (${studentId})`;
    }
    return studentName || studentId || "";
  }

  function parseExternalStudentBarcode(rawValue) {
    if (!rawValue) {
      return null;
    }
    try {
      const parsed = JSON.parse(rawValue);
      if (!parsed || typeof parsed !== "object") {
        return null;
      }
      const studentId = String(parsed.studentId || parsed.externalStudentId || "").trim();
      if (!studentId) {
        return null;
      }
      return {
        studentId,
        name: String(parsed.name || parsed.studentName || "").trim(),
        gender: String(parsed.gender || "").trim(),
      };
    } catch (_error) {
      return null;
    }
  }

  function formatLocalDateForTimezone(date, timeZone) {
    const formatter = new Intl.DateTimeFormat("en-CA", {
      timeZone: timeZone || undefined,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    });
    const parts = formatter.formatToParts(date);
    const year = parts.find((part) => part.type === "year")?.value || String(date.getFullYear());
    const month = parts.find((part) => part.type === "month")?.value || String(date.getMonth() + 1).padStart(2, "0");
    const day = parts.find((part) => part.type === "day")?.value || String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function formatCheckInTime(value) {
    if (!value) {
      return "-";
    }
    try {
      return new Date(value).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    } catch (_error) {
      return String(value);
    }
  }

  function renderTodayCheckInCountText() {
    if (!selectedClassId) {
      return role === "TEACHER"
        ? "Select a class to see today's check-in count."
        : "Select a site and class to see today's check-in count.";
    }
    const currentDate = checkInTodayListDate || formatLocalDateForTimezone(new Date(), selectedSite()?.timezone);
    const classRecord = selectedClass();
    if (classRecord && !isScheduledClassDate(classRecord, currentDate)) {
      return "No class is scheduled today.";
    }
    const currentQueryKey = [selectedClassId, currentDate].join(":");
    if (checkInTodayListLoading && checkInTodayListQueryKey === currentQueryKey) {
      return "Loading today's check-in count...";
    }
    if (!checkInTodayListQueryKey || checkInTodayListQueryKey !== currentQueryKey) {
      return "Today's check-in count will load after the class is selected.";
    }
    return `${checkInTodayListCount} check-in${checkInTodayListCount === 1 ? "" : "s"} today.`;
  }

  async function handleExternalStudentQrScan(rawValue) {
    const payload = parseExternalStudentBarcode(rawValue);
    if (!payload) {
      checkInBarcodeValue = rawValue;
      updateCheckInBarcodeValue(rawValue);
      updateCheckInStatus("QR code detected.");
      return;
    }

    await submitExternalStudentCheckIn({
      externalStudentId: payload.studentId,
      displayValue: formatExternalStudentBarcode(payload),
      studentName: payload.name || null,
      gender: payload.gender || null,
      barcodeValue: rawValue,
    });
  }

  async function handleManualCheckInSubmit() {
    const manualInput = root.querySelector("[data-check-in-manual-input]");
    const studentId = String((manualInput && "value" in manualInput ? manualInput.value : checkInManualStudentId) || "").trim();
    checkInManualStudentId = studentId;
    if (!studentId) {
      updateCheckInStatus("Enter a student ID.", true);
      return;
    }
    if (!selectedClassId) {
      updateCheckInStatus(role === "TEACHER"
        ? "Select a class before checking in external students."
        : "Select a site and class before checking in external students.", true);
      return;
    }
    if (checkInExternalCheckInSubmitting) {
      return;
    }

    const matchedStudent = checkInStudents.find((student) => String(student.externalId || "").trim() === studentId) || null;
    await submitExternalStudentCheckIn({
      externalStudentId: studentId,
      displayValue: matchedStudent?.studentName
        ? formatExternalStudentBarcode({
          studentId,
          name: matchedStudent.studentName,
        })
        : studentId,
      studentName: matchedStudent?.studentName || null,
      gender: matchedStudent?.genderCode || null,
      barcodeValue: studentId,
      clearManualInput: true,
      manualInput,
    });
  }

  function bindManualCheckInForm() {
    const form = root.querySelector("[data-check-in-manual-form]");
    const input = root.querySelector("[data-check-in-manual-input]");
    if (!form || !input) {
      return;
    }

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      void handleManualCheckInSubmit();
    });
    input.addEventListener("input", (event) => {
      checkInManualStudentId = event.currentTarget.value;
    });
    input.addEventListener("keydown", (event) => {
      if (event.key !== "Enter") {
        return;
      }
      event.preventDefault();
      if (typeof form.requestSubmit === "function") {
        form.requestSubmit();
      } else {
        void handleManualCheckInSubmit();
      }
    });
  }

  async function submitExternalStudentCheckIn({
    externalStudentId,
    displayValue,
    studentName,
    gender,
    barcodeValue,
    clearManualInput = false,
    manualInput = null,
  }) {
    if (!selectedClassId) {
      updateCheckInStatus(role === "TEACHER"
        ? "Select a class before checking in external students."
        : "Select a site and class before checking in external students.", true);
      return false;
    }
    if (checkInExternalCheckInSubmitting) {
      return false;
    }

    checkInExternalCheckInSubmitting = true;
    updateCheckInStatus("Saving external check-in...");

    try {
      const site = selectedSite();
      const checkDate = formatLocalDateForTimezone(new Date(), site?.timezone);
      const response = await checkInExternalStudent({
        tenantId: school.tenantId,
        externalStudentId,
        classId: selectedClassId,
        checkDate,
        studentName: studentName || null,
        gender: gender || null,
        barcodeValue: barcodeValue || externalStudentId,
      });
      const confirmation = displayValue || response.externalStudentId;
      if (clearManualInput) {
        checkInManualStudentId = "";
        if (manualInput && "value" in manualInput) {
          manualInput.value = "";
        }
      }
      updateCheckInBarcodeValue(confirmation);
      updateCheckInStatus(`Checked in ${confirmation}.`);
      playCheckInSuccessSound();
      speakCheckInSuccess(confirmation);
      applyLocalExternalCheckIn(response, {
        externalStudentId,
        studentName,
        gender,
        barcodeValue: barcodeValue || externalStudentId,
        checkDate,
      });
      return true;
    } catch (checkInError) {
      const alreadyCheckedIn = checkInError instanceof Error && checkInError.status === 409;
      const message = alreadyCheckedIn
        ? "This student has already checked in."
        : checkInError instanceof Error
          ? checkInError.message
          : "External check-in could not be saved.";
      playCheckInFailureSound();
      updateCheckInStatus(message, true);
      if (alreadyCheckedIn) {
        applyLocalExternalCheckIn({}, {
          externalStudentId,
          studentName,
          gender,
          barcodeValue: barcodeValue || externalStudentId,
          checkDate: formatLocalDateForTimezone(new Date(), selectedSite()?.timezone),
        });
      }
      return false;
    } finally {
      checkInExternalCheckInSubmitting = false;
    }
  }

  function updateCheckInStatus(message, isError = false) {
    checkInScannerStatus = message;
    const status = root.querySelector("[data-check-in-status]");
    if (status) {
      status.textContent = message;
      status.classList.toggle("is-error", isError);
    }
  }

  function scannerErrorMessage(cameraError) {
    if (cameraError?.name === "NotAllowedError") {
      return "Camera permission was denied.";
    }
    if (cameraError?.name === "NotFoundError") {
      return "No camera was found on this device.";
    }
    return "Camera could not be started.";
  }

  async function loadSites() {
    if (loadingSites || !school?.tenantId) {
      return;
    }
    loadingSites = true;
    try {
      const response = await listSites(school.tenantId);
      sites = response.sites || [];
      siteQuota = response.quota || null;
      if (selectedSiteId && !sites.some((site) => site.id === selectedSiteId)) {
        selectedSiteId = "";
      }
      siteRows = sites.length
        ? sites.map((site) => `${site.name} - ${site.timezone} - ${site.status}`)
        : ["No sites have been created yet."];
    } catch (loadError) {
      siteRows = ["Sites could not be loaded."];
      error = loadError instanceof Error ? loadError.message : "Sites could not be loaded.";
    } finally {
      loadingSites = false;
      render();
    }
  }

  async function handleOperationSubmit(form, activeSection, action) {
    if (!validateRequiredCheckboxGroups(form)) {
      return;
    }
    const submitButton = form.querySelector("button[type='submit']");
    submitButton.disabled = true;
    submitButton.textContent = "Saving";

    try {
      if (isCreateSiteOperation(action)) {
        const formData = new FormData(form);
        await createSite(school.tenantId, sitePayload(formData, null, user));
        notice = "Site saved to the database.";
        error = "";
        activeOperation = "";
        siteRows = null;
        sites = [];
        selectedSiteId = "";
        siteQuota = null;
        render();
        await loadSites();
        return;
      }

      if (isEditSiteOperation(action)) {
        const site = selectedSite();
        if (!site) {
          throw new Error("Select a site before editing.");
        }
        const formData = new FormData(form);
        await updateSite(school.tenantId, site.id, sitePayload(formData, site, user));
        notice = "Site updated in the database.";
        error = "";
        activeOperation = "";
        siteRows = null;
        siteQuota = null;
        render();
        await loadSites();
        return;
      }

      if (isCreateProgramOperation(action)) {
        const site = selectedSite();
        if (!site) {
          throw new Error("Log on to a site before adding a program.");
        }
        const formData = new FormData(form);
        await createProgram(school.tenantId, programPayload(formData, site));
        notice = "Program saved to the database.";
        error = "";
        activeOperation = "";
        resetPrograms();
        render();
        await loadPrograms();
        return;
      }

      if (isEditProgramOperation(action)) {
        const program = selectedProgram();
        if (!program) {
          throw new Error("Select a program before editing.");
        }
        const formData = new FormData(form);
        await updateProgram(school.tenantId, program.id, programPayload(formData, null, program));
        notice = "Program updated in the database.";
        error = "";
        activeOperation = "";
        resetPrograms();
        render();
        await loadPrograms();
        return;
      }

      if (isCreateClassOperation(action)) {
        const formData = new FormData(form);
        await createClass(school.tenantId, classPayload(formData, null));
        notice = "Class saved to the database.";
        error = "";
        activeOperation = "";
        resetClasses();
        render();
        await loadClasses();
        return;
      }

      if (isEditClassOperation(action)) {
        const classRecord = selectedClass();
        if (!classRecord) {
          throw new Error("Select a class before editing.");
        }
        const formData = new FormData(form);
        await updateClass(school.tenantId, classRecord.id, classPayload(formData, classRecord));
        notice = "Class updated in the database.";
        error = "";
        activeOperation = "";
        resetClasses();
        render();
        await loadClasses();
        return;
      }

      if (isPricingOperation(action)) {
        const classRecord = selectedClass();
        if (!classRecord) {
          throw new Error("Select a class before configuring pricing.");
        }
        const formData = new FormData(form);
        selectedClassPricing = await saveClassPricing(school.tenantId, classRecord.id, pricingPayload(formData));
        notice = "Class pricing saved to the database.";
        error = "";
        activeOperation = "";
        render();
        return;
      }

      if (isAssignTeacherOperation(action)) {
        const classRecord = selectedClass();
        if (!classRecord) {
          throw new Error("Select a class before assigning a teacher.");
        }
        const formData = new FormData(form);
        await assignClassTeacher(school.tenantId, classRecord.id, { email: formData.get("email") });
        notice = "Teacher assigned to the class.";
        error = "";
        activeOperation = "";
        render();
        await loadClassTeachers();
        return;
      }

      if (isNotificationOperation(action)) {
        const formData = new FormData(form);
        if (!window.confirm("Send this email to every BCC recipient?")) {
          submitButton.disabled = false;
          submitButton.textContent = "Send";
          return;
        }
        const sent = await sendNotification(school.tenantId, notificationPayload(formData));
        notificationHistory = [sent, ...notificationHistory];
        notice = "Email notification sent.";
        error = "";
        activeOperation = "";
        render();
        await loadNotifications();
        return;
      }

      if (isCreateChildOperation(action)) {
        const formData = new FormData(form);
        await createChild(childPayload(formData, school.tenantId));
        notice = "Child saved to the database.";
        error = "";
        activeOperation = "";
        childRows = null;
        render();
        await loadChildren();
        return;
      }

      if (isEditChildOperation(action)) {
        const child = selectedChild();
        if (!child) {
          throw new Error("Select a child before editing.");
        }
        const formData = new FormData(form);
        await updateChild(child.id, childPayload(formData, school.tenantId, child));
        notice = "Child updated in the database.";
        error = "";
        activeOperation = "";
        childRows = null;
        render();
        await loadChildren();
        return;
      }

      notice = `${action} is ready for backend integration at ${endpointFor(activeSection, action)}.`;
      error = "";
      activeOperation = "";
      render();
    } catch (submitError) {
      notice = "";
      error = friendlyOperationError(action, submitError);
      render();
    }
  }

  function rowsFor(section) {
    if (section.id === "overview" && role === "PARENT") {
      if (loadingChildren || loadingClasses || loadingEnrollments) {
        return ["Loading family overview..."];
      }
      return children.length ? [] : ["No child records loaded yet."];
    }
    if (section.id === "overview" && role === "TEACHER") {
      if (loadingClasses && classRows === null) {
        return ["Loading teacher classes..."];
      }
      return classRows || ["No assigned classes loaded yet."];
    }
    if (section.id === "sites") {
      if (loadingSites && siteRows === null) {
        return ["Loading sites..."];
      }
      return siteRows || ["No sites have been created yet."];
    }
    if (section.id === "programs") {
      if (loadingPrograms && programRows === null) {
        return ["Loading programs..."];
      }
      return programRows || ["No programs have been created for this site yet."];
    }
    if (section.id === "classes") {
      if (loadingClasses && classRows === null) {
        return ["Loading classes..."];
      }
      return classRows || ["No classes have been created for this site yet."];
    }
    if (section.id === "students" && role === "SCHOOL_ADMIN") {
      if (loadingStudents) {
        return ["Loading students..."];
      }
      return students.length ? [] : ["No students are enrolled in active classes yet."];
    }
    if (section.id === "children") {
      if (loadingChildren && childRows === null) {
        return ["Loading children..."];
      }
      return childRows || ["No child records loaded yet."];
    }
    if (section.id === "enrollments" && role === "PARENT") {
      if (loadingEnrollments) {
        return ["Loading enrollments..."];
      }
      return enrollments.length ? [] : ["No enrollments yet."];
    }
    if (section.id === "attendance" && role === "PARENT") {
      if (loadingAttendance) {
        return ["Loading attendance..."];
      }
      return attendanceRecords.length ? [] : ["No attendance records yet."];
    }
    if (section.id === "attendance" && role === "SCHOOL_ADMIN") {
      if (loadingClasses || loadingAttendance) {
        return ["Loading class attendance..."];
      }
      if (!classes.length) {
        return ["No classes have been created for this site yet."];
      }
      return attendanceGrid ? [] : ["Choose a class to load attendance."];
    }
    if (section.id === "payments" && role === "PARENT") {
      if (loadingEnrollments) {
        return ["Loading payments..."];
      }
      return pendingPaymentEnrollments().length ? [] : ["No pending payments."];
    }
    if (section.id === "notifications") {
      if (loadingNotifications) {
        return ["Loading notification history..."];
      }
      if (!notificationHistory.length) {
        return ["No notification history loaded yet."];
      }
      return notificationHistory.map((entry) => `${entry.subject || "Email"} - ${entry.status} - ${entry.bccRecipientCount} BCC recipients`);
    }
    return section.rows;
  }

  function dataList(section, rows) {
    if (section.id === "overview" && role === "PARENT") {
      return parentOverview(rows);
    }
    if (section.id === "sites" && sites.length) {
      return `
        <div class="data-list" role="listbox" aria-label="Sites">
          ${sites
            .map(
              (site) => `
                <div class="data-row site-management-row ${site.id === selectedSiteId ? "is-selected" : ""}">
                  <button class="site-row-main" data-site-id="${escapeHtml(site.id)}" type="button">
                    <strong>${escapeHtml(site.name)}</strong>
                    <span>${escapeHtml(site.city && site.state ? `${site.city}, ${site.state}` : site.timezone)}</span>
                  </button>
                  <span>${escapeHtml(site.status)}</span>
                  <div class="row-actions">
                    <button
                      aria-label="Edit ${escapeHtml(site.name)}"
                      class="icon-button subtle-icon-button"
                      data-site-edit-id="${escapeHtml(site.id)}"
                      title="Edit site"
                      type="button"
                    >
                      ✎
                    </button>
                    <button
                      aria-label="Enter ${escapeHtml(site.name)}"
                      class="icon-button subtle-icon-button"
                      data-site-login-id="${escapeHtml(site.id)}"
                      title="Enter site"
                      type="button"
                    >
                      →
                    </button>
                  </div>
                </div>
              `
            )
            .join("")}
        </div>
      `;
    }
    if (section.id === "programs" && programs.length) {
      return `
        <div class="data-list" role="listbox" aria-label="Programs">
          ${programs
            .map(
              (program) => `
                <div class="data-row program-row ${program.id === selectedProgramId ? "is-selected" : ""}">
                  <button
                    class="program-row-main"
                    data-program-id="${escapeHtml(program.id)}"
                    type="button"
                  >
                    <span>${escapeHtml(program.name)}</span>
                    <span>${escapeHtml(program.description || `${program.startDate || ""} to ${program.endDate || ""}`)}</span>
                    <span>${escapeHtml(program.status)}</span>
                  </button>
                  <button
                    aria-label="Edit ${escapeHtml(program.name)}"
                    class="icon-button subtle-icon-button"
                    data-program-edit-id="${escapeHtml(program.id)}"
                    title="Edit program"
                    type="button"
                  >
                    ✎
                  </button>
                </div>
              `
            )
            .join("")}
        </div>
      `;
    }
    if (section.id === "classes" && classes.length) {
      if (role === "SCHOOL_ADMIN" && selectedClass()) {
        return classManagementView(selectedClass());
      }
      return `
        <div class="data-list" role="listbox" aria-label="Classes">
          ${classes
            .map(
              (classRecord) => `
                <div class="data-row class-row ${classRecord.id === selectedClassId ? "is-selected" : ""}">
                  <button
                    class="class-row-main"
                    data-class-id="${escapeHtml(classRecord.id)}"
                    type="button"
                  >
                    <span>${escapeHtml(classRecord.name)}</span>
                    <span>${escapeHtml(`${programName(classRecord.programId)} - ${classScheduleText(classRecord)}`)}</span>
                    <span>${escapeHtml(classRecord.status)}</span>
                  </button>
                  ${role === "PARENT" ? `
                    <div class="row-actions">
                      ${isEnrollmentClosed(classRecord) ? `<span class="row-muted-label">Enrollment closed</span>` : ""}
                      <button
                        aria-label="Enroll in ${escapeHtml(classRecord.name)}"
                        class="icon-button subtle-icon-button"
                        data-class-enroll-id="${escapeHtml(classRecord.id)}"
                        title="${isEnrollmentClosed(classRecord) ? "Enrollment closed" : "Enroll"}"
                        type="button"
                        ${isEnrollmentClosed(classRecord) ? "disabled" : ""}
                      >
                        +
                      </button>
                    </div>
                  ` : `
                    <span class="row-muted-label">Manage</span>
                  `}
                </div>
              `
            )
            .join("")}
        </div>
      `;
    }
    if (section.id === "students" && role === "SCHOOL_ADMIN") {
      return studentRosterList(rows);
    }
    if (section.id === "notifications") {
      return notificationList(rows);
    }
    if (section.id === "children" && children.length) {
      return childProfileGrid();
    }
    if (section.id === "enrollments" && role === "PARENT") {
      return `
        ${enrollmentSummary()}
        ${enrollmentList(rows)}
      `;
    }
    if (section.id === "attendance" && role === "PARENT") {
      return attendanceList();
    }
    if (section.id === "attendance" && role === "SCHOOL_ADMIN") {
      return adminAttendanceGrid(rows);
    }
    if (section.id === "payments" && role === "PARENT") {
      return parentPaymentList(rows);
    }
    return `
      <div class="data-list">
        ${rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")}
      </div>
    `;
  }

  function parentOverview(rows) {
    if (loadingChildren || loadingClasses || loadingEnrollments) {
      return `
        <div class="data-list">
          ${rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")}
        </div>
      `;
    }
    if (!children.length) {
      return `
        <div class="data-list">
          ${rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")}
        </div>
      `;
    }
    const activeEnrollments = activeParentEnrollments();
    const openClasses = classes.filter((classRecord) => !isEnrollmentClosed(classRecord));
    return `
      <section class="family-overview" aria-label="Family overview">
        <div class="family-overview-metrics" aria-label="Family summary">
          ${familyMetric("Children", children.length)}
          ${familyMetric("Current registrations", activeEnrollments.length)}
          ${familyMetric("Open classes", openClasses.length)}
          ${familyMetric("Pending payments", pendingPaymentEnrollments().length)}
        </div>
        <div class="family-child-grid">
          ${children.map((child) => familyChildCard(child)).join("")}
        </div>
      </section>
    `;
  }

  function familyMetric(label, value) {
    return `
      <div class="family-metric">
        <span>${escapeHtml(label)}</span>
        <strong>${escapeHtml(String(value))}</strong>
      </div>
    `;
  }

  function familyChildCard(child) {
    const childEnrollments = activeParentEnrollments().filter((enrollment) => enrollment.childId === child.id);
    const availableClasses = availableClassesForChild(child.id);
    const details = [
      child.dateOfBirth ? `DOB ${formatDate(child.dateOfBirth)}` : "",
      child.grade ? `Grade ${child.grade}` : "",
      child.school || "",
      child.status ? statusLabel(child.status) : "",
    ].filter(Boolean);
    return `
      <article class="family-child-card">
        <header>
          <div>
            <h4>${escapeHtml(`${child.firstName} ${child.lastName}`.trim() || "Child")}</h4>
            <p>${escapeHtml(details.join(" - ") || "Student profile")}</p>
          </div>
        </header>
        ${familyClassList("Registered classes", childEnrollments.map((enrollment) => enrolledClassSummary(enrollment)), "No current registrations.")}
        ${familyClassList("Open classes", availableClasses.map((classRecord) => availableClassSummary(classRecord, child.id)), "No additional open classes right now.")}
      </article>
    `;
  }

  function childProfileGrid() {
    return `
      <div class="child-profile-grid" role="list" aria-label="Children">
        ${children.map((child) => childProfileCard(child)).join("")}
      </div>
    `;
  }

  function childProfileCard(child) {
    const name = childFullName(child);
    const summary = [child.grade ? `Grade ${child.grade}` : "", child.school || ""]
      .filter(Boolean)
      .join(" - ");
    const details = [
      child.dateOfBirth ? childProfileDetail("Date of birth", formatDate(child.dateOfBirth)) : "",
      child.gender ? childProfileDetail("Gender", child.gender) : "",
      child.grade ? childProfileDetail("Grade", child.grade) : "",
      child.school ? childProfileDetail("School", child.school) : "",
      Array.isArray(child.race) && child.race.length ? childProfileDetail("Race", child.race.join(", ")) : "",
      childProfileDetail("Status", statusLabel(child.status || "active")),
    ].filter(Boolean);
    return `
      <article class="child-profile-card ${child.id === selectedChildId ? "is-selected" : ""}" role="listitem">
        <button
          aria-pressed="${child.id === selectedChildId ? "true" : "false"}"
          class="child-profile-main"
          data-child-id="${escapeHtml(child.id)}"
          type="button"
        >
          <span>${escapeHtml(name)}</span>
          <small>${escapeHtml(summary || "Student profile")}</small>
        </button>

        <dl class="child-profile-details">
          ${details.join("")}
        </dl>

        ${child.note ? `<p class="child-profile-note">${escapeHtml(child.note)}</p>` : ""}

        <div class="child-profile-actions">
          <button
            aria-label="Edit ${escapeHtml(name)}"
            class="secondary-button compact-button"
            data-child-edit-id="${escapeHtml(child.id)}"
            type="button"
          >
            Edit
          </button>
        </div>
      </article>
    `;
  }

  function childProfileDetail(label, value) {
    return `
      <div>
        <dt>${escapeHtml(label)}</dt>
        <dd>${escapeHtml(value || "Unavailable")}</dd>
      </div>
    `;
  }

  function familyClassList(title, items, emptyText) {
    return `
      <section class="family-class-section">
        <h5>${escapeHtml(title)}</h5>
        ${
          items.length
            ? `<div class="family-class-list">${items.join("")}</div>`
            : `<p>${escapeHtml(emptyText)}</p>`
        }
      </section>
    `;
  }

  function enrolledClassSummary(enrollment) {
    const classRecord = classes.find((item) => item.id === enrollment.classId);
    return `
      <div class="family-class-row">
        <strong>${escapeHtml(classRecord?.name || "Class")}</strong>
        <span>${escapeHtml(classRecord ? `${enrollmentDateRange(classRecord)} - ${classScheduleText(classRecord)}` : "Class details unavailable")}</span>
        <small>${escapeHtml(statusLabel(enrollment.status || "enrolled"))}</small>
      </div>
    `;
  }

  function availableClassSummary(classRecord, childId) {
    return `
      <div class="family-open-class-row">
        <div>
          <strong>${escapeHtml(classRecord.name || "Class")}</strong>
          <span>${escapeHtml(`${enrollmentDateRange(classRecord)} - ${classScheduleText(classRecord)}`)}</span>
          <small>${escapeHtml(classRecord.registrationClosesAt ? `Registration closes ${new Date(classRecord.registrationClosesAt).toLocaleString()}` : "Registration open")}</small>
        </div>
        <button
          class="secondary-button compact-button family-register-button"
          data-family-register-child-id="${escapeHtml(childId)}"
          data-family-register-class-id="${escapeHtml(classRecord.id)}"
          type="button"
        >
          Register
        </button>
      </div>
    `;
  }

  function availableClassesForChild(childId) {
    const registeredClassIds = new Set(
      activeParentEnrollments()
        .filter((enrollment) => enrollment.childId === childId)
        .map((enrollment) => enrollment.classId)
    );
    return classes
      .filter((classRecord) => !registeredClassIds.has(classRecord.id))
      .filter((classRecord) => !isEnrollmentClosed(classRecord));
  }

  function activeParentEnrollments() {
    return enrollments.filter((enrollment) =>
      !["cancelled", "rejected"].includes(String(enrollment.status || "").toLowerCase())
    );
  }

  function notificationList(rows) {
    const gmail = notificationProviders.find((provider) => provider.providerType === "gmail_oauth");
    return `
      <div class="provider-strip">
        <div>
          <strong>${gmail ? "Gmail connected" : "Gmail not connected"}</strong>
          <span>${escapeHtml(gmail?.fromEmail || "Connect Gmail before sending email notifications.")}</span>
        </div>
        <button class="secondary-button compact-button" data-gmail-connect type="button">
          ${gmail ? "Reconnect Gmail" : "Connect Gmail"}
        </button>
      </div>
      <div class="data-list notification-history-list">
        ${
          notificationHistory.length
            ? notificationHistory
                .map(
                  (entry) => `
                    <div class="data-row notification-history-row">
                      <strong>${escapeHtml(entry.subject || "Email notification")}</strong>
                      <span>${escapeHtml(`${entry.status} - ${entry.bccRecipientCount} BCC recipients`)}</span>
                      <span>${escapeHtml(entry.sentAt ? new Date(entry.sentAt).toLocaleString() : "")}</span>
                    </div>
                  `
                )
                .join("")
            : rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")
        }
      </div>
    `;
  }

  function studentRosterList(rows) {
    const activeClasses = classes.filter((classRecord) => String(classRecord.status || "").toLowerCase() === "active");
    return `
      <div class="list-filter-bar">
        <label>
          <span>Class</span>
          <select data-student-class-filter>
            <option value="">All active classes</option>
            ${activeClasses.map((classRecord) => `
              <option value="${escapeHtml(classRecord.id)}" ${selectedStudentClassId === classRecord.id ? "selected" : ""}>
                ${escapeHtml(classRecord.name)}
              </option>
            `).join("")}
          </select>
        </label>
      </div>
      <div class="data-list student-roster-list" aria-label="Students">
        ${
          students.length
            ? students.map((student) => {
                const classCount = Number(student.classCount || 1);
                const enrollmentSummary = classCount > 1
                  ? `${classCount} active enrollments`
                  : `${statusLabel(student.enrollmentStatus || "enrolled")} enrollment`;
                const classStatus = classCount > 1 ? "Active classes" : (student.classStatus || "active");
                const enrolledAt = student.enrolledAt ? formatDate(student.enrolledAt) : "";
                const enrollmentDate = enrolledAt
                  ? `${classCount > 1 ? "Latest enrollment" : "Enrolled"} ${enrolledAt}`
                  : "Enrollment date unavailable";
                return `
                <div class="data-row student-roster-row">
                  <div>
                    <strong>${escapeHtml(student.childName || "Student")}</strong>
                    <span>${escapeHtml(student.dateOfBirth ? `DOB ${formatDate(student.dateOfBirth)}` : "Student profile")}</span>
                  </div>
                  <div>
                    <strong>${escapeHtml(student.className || "Class")}</strong>
                    <span>${escapeHtml(enrollmentSummary)}</span>
                  </div>
                  <div>
                    <strong>${escapeHtml(student.parentEmail || "Parent email unavailable")}</strong>
                    <span>${escapeHtml(student.parentPhone || "Phone unavailable")}</span>
                  </div>
                  <div>
                    <strong>${escapeHtml(classStatus)}</strong>
                    <span>${escapeHtml(enrollmentDate)}</span>
                  </div>
                </div>
              `;
              }).join("")
            : rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")
        }
      </div>
    `;
  }

  function classManagementView(classRecord) {
    const stopped = String(classRecord.status || "").toLowerCase() === "stopped";
    const enrollmentClosed = isEnrollmentClosed(classRecord);
    const registrationStatus = classRecord.registrationClosesAt
      ? `Closed ${new Date(classRecord.registrationClosesAt).toLocaleString()}`
      : "Open";
    return `
      <section class="class-management-panel" aria-label="${escapeHtml(classRecord.name)} management">
        <div class="class-management-header">
          <div>
            <button class="secondary-button compact-button back-button" data-class-detail-back type="button">Back to classes</button>
            <h3>${escapeHtml(classRecord.name)}</h3>
            <p>${escapeHtml(`${programName(classRecord.programId)} - ${classScheduleText(classRecord)}`)}</p>
          </div>
          <span class="status-pill">${escapeHtml(statusLabel(classRecord.status || "active"))}</span>
        </div>

        <div class="class-management-actions" aria-label="Class management actions">
          <button class="secondary-button compact-button" data-class-edit-id="${escapeHtml(classRecord.id)}" type="button">Modify</button>
          <button class="secondary-button compact-button" data-class-pricing-id="${escapeHtml(classRecord.id)}" type="button">Configure price</button>
          <button class="secondary-button compact-button" data-class-public-link-id="${escapeHtml(classRecord.id)}" type="button">Copy public link</button>
          <button class="secondary-button compact-button" data-class-assign-teacher type="button">Assign teacher</button>
          <button
            class="secondary-button compact-button"
            data-class-close-enrollment-id="${escapeHtml(classRecord.id)}"
            type="button"
            ${enrollmentClosed ? "disabled" : ""}
          >
            Close enrollment
          </button>
          <button
            class="secondary-button compact-button danger-text-button"
            data-class-stop-id="${escapeHtml(classRecord.id)}"
            type="button"
            ${stopped ? "disabled" : ""}
          >
            Stop class
          </button>
        </div>

        <dl class="class-detail-grid">
          ${classDetailItem("Program", programName(classRecord.programId))}
          ${classDetailItem("Date range", enrollmentDateRange(classRecord))}
          ${classDetailItem("Schedule", classScheduleText(classRecord))}
          ${classDetailItem("Capacity", classRecord.capacity ? String(classRecord.capacity) : "No capacity set")}
          ${classDetailItem("Enrollment", registrationStatus)}
          ${classDetailItem("Class type", classRecord.classType === "time_range" ? "Time range" : "Weekly")}
          ${classRecord.description ? classDetailItem("Description", classRecord.description, true) : ""}
        </dl>

        <section class="assigned-teachers-section">
          <div class="workspace-heading">
            <h3>Assigned teachers</h3>
            <p>${escapeHtml(loadingClassTeachers ? "Loading teachers..." : `${classTeachers.length} assigned teacher${classTeachers.length === 1 ? "" : "s"}.`)}</p>
          </div>
          ${assignedTeachersList()}
        </section>
      </section>
    `;
  }

  function classDetailItem(label, value, wide = false) {
    return `
      <div class="${wide ? "is-wide" : ""}">
        <dt>${escapeHtml(label)}</dt>
        <dd>${escapeHtml(value || "Unavailable")}</dd>
      </div>
    `;
  }

  function assignedTeachersList() {
    if (loadingClassTeachers) {
      return `<div class="data-list"><div class="data-row">Loading assigned teachers...</div></div>`;
    }
    if (!classTeachers.length) {
      return `<div class="data-list"><div class="data-row">No teachers assigned yet.</div></div>`;
    }
    return `
      <div class="data-list assigned-teachers-list" aria-label="Assigned teachers">
        ${classTeachers.map((teacher) => `
          <div class="data-row assigned-teacher-row">
            <strong>${escapeHtml(teacherName(teacher))}</strong>
            <span>${escapeHtml(teacher.email || "Email unavailable")}</span>
            <span>${escapeHtml(teacher.phone || "Phone unavailable")}</span>
            <span>${escapeHtml(statusLabel(teacher.status || "active"))}</span>
          </div>
        `).join("")}
      </div>
    `;
  }

  function siteLoginList() {
    return `
      <div class="data-list" role="listbox" aria-label="Sites">
        ${sites
          .map(
            (site) => `
              <button
                class="data-row selectable-row"
                data-site-login-id="${escapeHtml(site.id)}"
                type="button"
              >
                <span>${escapeHtml(site.name)}</span>
                <span>${escapeHtml(site.city && site.state ? `${site.city}, ${site.state}` : site.timezone)}</span>
                <span>Log on</span>
              </button>
            `
          )
          .join("")}
      </div>
    `;
  }

  function selectedSite() {
    return sites.find((site) => site.id === selectedSiteId);
  }

  function selectedProgram() {
    return programs.find((program) => program.id === selectedProgramId);
  }

  function selectedClass() {
    return classes.find((classRecord) => classRecord.id === selectedClassId);
  }

  function selectedChild() {
    return children.find((child) => child.id === selectedChildId);
  }

  function enrollmentSummary() {
    if (!enrollments.length) {
      return "";
    }
    const activeCount = enrollments.filter((enrollment) => !["cancelled", "rejected"].includes(String(enrollment.status || "").toLowerCase())).length;
    return `
      <p class="context-note">
        ${escapeHtml(`${activeCount} active registration${activeCount === 1 ? "" : "s"} across ${enrollments.length} total enrollment record${enrollments.length === 1 ? "" : "s"}.`)}
      </p>
    `;
  }

  function enrollmentList(rows) {
    return `
      ${selectedEnrollmentCalendar()}
      <div class="data-list enrollment-list" aria-label="Enrollments">
        ${
          enrollments.length
            ? enrollments.map((enrollment) => {
                const classRecord = classes.find((item) => item.id === enrollment.classId);
                const selectedCount = enrollment.selectedOptionalFeeItemIds?.length || 0;
                return `
                  <button class="data-row enrollment-row enrollment-detail-row ${selectedEnrollmentId === enrollment.id ? "is-selected" : ""}" data-enrollment-detail-id="${escapeHtml(enrollment.id)}" type="button">
                    <div>
                      <strong>${escapeHtml(classRecord?.name || "Class")}</strong>
                      <span>${escapeHtml(childName(enrollment.childId))}</span>
                    </div>
                    <div>
                      <span>${escapeHtml(enrollmentDateRange(classRecord))}</span>
                      <span>${escapeHtml(classRecord ? classScheduleText(classRecord) : "Schedule unavailable")}</span>
                    </div>
                    <div>
                      <strong>${escapeHtml(statusLabel(enrollment.status || "pending"))}</strong>
                      <span>${escapeHtml(`Registered ${formatDate(enrollment.createdAt) || "date unavailable"}`)}</span>
                    </div>
                    <div>
                      <span>${escapeHtml(selectedCount ? `${selectedCount} optional add-on${selectedCount === 1 ? "" : "s"}` : "No optional add-ons")}</span>
                      <span>View attendance calendar</span>
                    </div>
                  </button>
                `;
              }).join("")
            : rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")
        }
      </div>
    `;
  }

  function selectedEnrollmentCalendar() {
    const enrollment = selectedEnrollment();
    if (!enrollment) {
      return "";
    }
    const classRecord = classes.find((item) => item.id === enrollment.classId);
    if (!classRecord) {
      return `
        <section class="attendance-calendar-panel">
          <div class="workspace-heading workspace-heading-row">
            <div>
              <h3>Attendance calendar</h3>
              <p>Class details are still loading.</p>
            </div>
            <button class="secondary-button compact-button" data-enrollment-detail-close type="button">Back</button>
          </div>
        </section>
      `;
    }
    const records = attendanceRecords.filter((record) =>
      record.childId === enrollment.childId && record.classId === enrollment.classId
    );
    return `
      <section class="attendance-calendar-panel" aria-label="Enrollment attendance calendar">
        <div class="workspace-heading workspace-heading-row">
          <div>
            <h3>${escapeHtml(classRecord.name)} attendance</h3>
            <p>${escapeHtml(`${childName(enrollment.childId)} - ${enrollmentDateRange(classRecord)} - ${classScheduleText(classRecord)}`)}</p>
          </div>
          <button class="secondary-button compact-button" data-enrollment-detail-close type="button">Back</button>
        </div>
        ${attendanceCalendar(classRecord, records)}
      </section>
    `;
  }

  function selectedEnrollment() {
    if (!selectedEnrollmentId) {
      return null;
    }
    return enrollments.find((enrollment) => enrollment.id === selectedEnrollmentId) || null;
  }

  function attendanceCalendar(classRecord, records) {
    const months = calendarMonths(classRecord.startDate, classRecord.endDate);
    if (!months.length) {
      return `<p class="context-note">Class schedule dates are unavailable.</p>`;
    }
    const checkedInDates = new Set(records.map((record) => record.classDate));
    return `
      <div class="attendance-calendar-legend">
        <span><i class="calendar-key checked"></i>Checked in</span>
        <span><i class="calendar-key scheduled"></i>Scheduled</span>
        <span><i class="calendar-key missed"></i>No check-in</span>
      </div>
      <div class="attendance-calendar-months">
        ${months.map((month) => attendanceCalendarMonth(classRecord, month, checkedInDates)).join("")}
      </div>
    `;
  }

  function attendanceCalendarMonth(classRecord, monthStart, checkedInDates) {
    const monthLabel = monthStart.toLocaleDateString(undefined, { month: "long", year: "numeric" });
    const blanks = Array.from({ length: monthStart.getDay() }, () => `<span class="calendar-day is-empty" aria-hidden="true"></span>`).join("");
    const days = [];
    const cursor = new Date(monthStart);
    while (cursor.getMonth() === monthStart.getMonth()) {
      const value = localDateValue(cursor);
      const scheduled = isScheduledClassDate(classRecord, value);
      const checked = checkedInDates.has(value);
      const statusClass = checked ? "is-checked" : scheduled ? "is-missed" : "is-unscheduled";
      const statusLabel = checked ? "Checked in" : scheduled ? "No check-in" : "No class";
      days.push(`
        <span class="calendar-day ${statusClass}" title="${escapeHtml(`${formatDate(value)} - ${statusLabel}`)}">
          <strong>${cursor.getDate()}</strong>
          <small>${scheduled ? (checked ? "In" : "-") : ""}</small>
        </span>
      `);
      cursor.setDate(cursor.getDate() + 1);
    }
    return `
      <section class="attendance-calendar-month">
        <h4>${escapeHtml(monthLabel)}</h4>
        <div class="calendar-weekdays" aria-hidden="true">
          ${["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => `<span>${day}</span>`).join("")}
        </div>
        <div class="calendar-grid">${blanks}${days.join("")}</div>
      </section>
    `;
  }

  function calendarMonths(startDate, endDate) {
    if (!startDate || !endDate) {
      return [];
    }
    const start = dateFromLocalValue(startDate);
    const end = dateFromLocalValue(endDate);
    const months = [];
    const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    const last = new Date(end.getFullYear(), end.getMonth(), 1);
    while (cursor <= last) {
      months.push(new Date(cursor));
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return months;
  }

  function externalAttendanceCalendarMonth(monthStart, classRecord) {
    const monthLabel = monthStart.toLocaleDateString(undefined, { month: "long", year: "numeric" });
    const blanks = Array.from({ length: monthStart.getDay() }, () => `<span class="calendar-day is-empty" aria-hidden="true"></span>`).join("");
    const days = [];
    const cursor = new Date(monthStart);
    while (cursor.getMonth() === monthStart.getMonth()) {
      const value = localDateValue(cursor);
      const inRange = Boolean(classRecord?.startDate && classRecord?.endDate && value >= classRecord.startDate && value <= classRecord.endDate);
      const scheduled = inRange && isScheduledClassDate(classRecord, value);
      const isSelected = externalAttendanceDate === value;
      const rowsForDay = isSelected ? externalAttendanceDetailRows : [];
      const countForDay = externalAttendanceCountRows.find((row) => row.checkDate === value)?.count || 0;
      const statusClass = !inRange
        ? "is-unscheduled"
        : !scheduled
          ? "is-no-class"
          : countForDay > 0
            ? "is-checked"
            : isSelected
              ? rowsForDay.length ? "is-checked" : "is-missed"
              : "is-scheduled";
      const statusLabel = !inRange
        ? "No class"
        : !scheduled
          ? "No class"
        : isSelected
          ? rowsForDay.length ? `${rowsForDay.length} check-ins` : "No check-ins"
          : "Class day";
      days.push(`
        <button
          class="calendar-day ${statusClass} ${isSelected ? "is-selected" : ""}"
          data-external-attendance-day="${escapeHtml(value)}"
          title="${escapeHtml(`${formatDate(value)} - ${statusLabel}`)}"
          type="button"
          ${!inRange ? "disabled" : ""}
        >
          <strong>${cursor.getDate()}</strong>
          <small>${escapeHtml(scheduled ? String(countForDay) : "")}</small>
        </button>
      `);
      cursor.setDate(cursor.getDate() + 1);
    }
    return `
      <section class="attendance-calendar-month">
        <h4>${escapeHtml(monthLabel)}</h4>
        <div class="calendar-weekdays" aria-hidden="true">
          ${["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => `<span>${day}</span>`).join("")}
        </div>
        <div class="calendar-grid">${blanks}${days.join("")}</div>
      </section>
    `;
  }

  function isScheduledClassDate(classRecord, dateValue) {
    if (!classRecord?.startDate || !classRecord?.endDate || dateValue < classRecord.startDate || dateValue > classRecord.endDate) {
      return false;
    }
    if (classRecord.classType === "weekly" && classRecord.weekdays?.length) {
      const weekday = dateFromLocalValue(dateValue).toLocaleDateString("en-US", { weekday: "long" }).toUpperCase();
      return classRecord.weekdays.includes(weekday);
    }
    return true;
  }

  function attendanceDateMessage(classRecord, dateValue) {
    if (!classRecord) {
      return "";
    }
    if (classRecord.startDate && classRecord.endDate && (dateValue < classRecord.startDate || dateValue > classRecord.endDate)) {
      return `${classRecord.name} runs from ${formatDate(classRecord.startDate)} to ${formatDate(classRecord.endDate)}. Choose a date in that range.`;
    }
    if (classRecord.classType === "weekly" && classRecord.weekdays?.length && !isScheduledClassDate(classRecord, dateValue)) {
      const weekday = dateFromLocalValue(dateValue).toLocaleDateString("en-US", { weekday: "long" });
      return `${classRecord.name} does not meet on ${weekday}. Scheduled days are ${formatWeekdayList(classRecord.weekdays)}.`;
    }
    return "";
  }

  async function loadExternalAttendanceDetails(dateValue) {
    const classRecord = selectedClass();
    if (!classRecord || !selectedClassId || (role === "SCHOOL_ADMIN" && !selectedSiteId)) {
      return;
    }
    externalAttendanceDate = dateValue;
    externalAttendanceDetailOpen = true;
    const queryKey = [selectedClassId, dateValue].join(":");
    if (externalAttendanceDetailQueryKey === queryKey && externalAttendanceDetailRows.length) {
      externalAttendanceStage = "calendar";
      render();
      return;
    }
    externalAttendanceDetailLoading = true;
    externalAttendanceDetailError = "";
    externalAttendanceDetailRows = [];
    externalAttendanceStage = "calendar";
    render();
    try {
      const response = await listExternalCheckIns({
        tenantId: school.tenantId,
        classId: selectedClassId,
        checkDate: dateValue,
      });
      externalAttendanceDetailRows = response.checkIns || [];
      externalAttendanceDetailQueryKey = queryKey;
    } catch (loadError) {
      externalAttendanceDetailError = loadError instanceof Error ? loadError.message : "Attendance details could not be loaded.";
    } finally {
      externalAttendanceDetailLoading = false;
      render();
    }
  }

  function closeExternalAttendanceDetailModal() {
    externalAttendanceDetailOpen = false;
    externalAttendanceDetailLoading = false;
    externalAttendanceDetailError = "";
    destroyExternalAttendanceDetailTabulator();
    render();
  }

  function renderExternalAttendanceDetailModal() {
    const classRecord = selectedClass();
    const rowCount = externalAttendanceDetailRows.length;
    return `
      <div class="modal-backdrop" data-external-attendance-detail-modal>
        <section class="check-in-today-list-panel external-attendance-detail-panel" role="dialog" aria-modal="true" aria-labelledby="external-attendance-detail-title">
          <div class="workspace-heading workspace-heading-row">
            <div>
              <h3 id="external-attendance-detail-title">${escapeHtml(externalAttendanceDate ? formatDate(externalAttendanceDate) : "Attendance list")}</h3>
              <p>${escapeHtml(classRecord ? classRecord.name : "Class unavailable")}</p>
              <p class="check-in-selection-count">${escapeHtml(`${rowCount} attendance record${rowCount === 1 ? "" : "s"}.`)}</p>
            </div>
            <div class="check-in-students-actions">
              <button class="secondary-button compact-button" data-external-attendance-detail-close type="button">Close</button>
            </div>
          </div>
          ${externalAttendanceDetailError ? `<p class="message error" role="alert">${escapeHtml(externalAttendanceDetailError)}</p>` : ""}
          ${externalAttendanceDetailLoading ? `<p class="context-note">Loading attendance details...</p>` : ""}
          <div class="check-in-students-table-shell external-attendance-detail-table-shell">
            <div data-external-attendance-detail-tabulator class="check-in-students-tabulator external-attendance-detail-tabulator"></div>
          </div>
        </section>
      </div>
    `;
  }

  async function loadExternalAttendanceCounts() {
    const classRecord = selectedClass();
    if (!classRecord || !selectedClassId || !classRecord.startDate || !classRecord.endDate || externalAttendanceCountLoading) {
      return;
    }
    const queryKey = [selectedClassId, classRecord.startDate, classRecord.endDate].join(":");
    if (externalAttendanceCountQueryKey === queryKey && externalAttendanceCountRows.length) {
      return;
    }
    externalAttendanceCountLoading = true;
    externalAttendanceCountError = "";
    render();
    try {
      const response = await listExternalCheckInCounts({
        tenantId: school.tenantId,
        classId: selectedClassId,
        startDate: classRecord.startDate,
        endDate: classRecord.endDate,
      });
      externalAttendanceCountRows = response || [];
      externalAttendanceCountQueryKey = queryKey;
    } catch (loadError) {
      externalAttendanceCountError = loadError instanceof Error ? loadError.message : "Attendance counts could not be loaded.";
      externalAttendanceCountRows = [];
      externalAttendanceCountQueryKey = queryKey;
    } finally {
      externalAttendanceCountLoading = false;
      render();
    }
  }

  function destroyExternalAttendanceDetailTabulator() {
    if (externalAttendanceDetailTabulator) {
      externalAttendanceDetailTabulator.destroy();
      externalAttendanceDetailTabulator = null;
    }
  }

  async function initializeExternalAttendanceDetailTabulator() {
    const element = root.querySelector("[data-external-attendance-detail-tabulator]");
    if (!element || !externalAttendanceDetailOpen) {
      return;
    }

    const Tabulator = await loadTabulator();
    if (!element.isConnected || !externalAttendanceDetailOpen) {
      return;
    }

    destroyExternalAttendanceDetailTabulator();
    externalAttendanceDetailTabulator = new Tabulator(element, {
      data: externalAttendanceDetailRows.map((row) => ({
        checkedInAt: row.checkInTime || "",
        studentName: row.studentName || "-",
        externalStudentId: row.externalStudentId || "-",
        gender: row.gender || "-",
        checkedInByRole: row.checkedInByRole || "-",
        barcodeValue: row.barcodeValue || "",
      })),
      columns: [
        { title: "Time", field: "checkedInAt", width: 130, formatter: (cell) => formatCheckInTime(cell.getValue()) },
        { title: "Student", field: "studentName", headerFilter: "input", minWidth: 180 },
        { title: "StudentID", field: "externalStudentId", headerFilter: "input", width: 150 },
        { title: "Gender", field: "gender", headerFilter: "input", width: 110 },
        { title: "Checked by", field: "checkedInByRole", headerFilter: "input", width: 150 },
      ],
      height: "100%",
      layout: "fitColumns",
      placeholder: externalAttendanceDetailLoading ? "Loading..." : "No attendance records for this day.",
      movableColumns: true,
      selectableRows: false,
      columnDefaults: {
        vertAlign: "middle",
      },
    });
  }

  function parentPaymentList(rows) {
    const pending = pendingPaymentEnrollments();
    return `
      <div class="data-list enrollment-list" aria-label="Pending payments">
        ${
          pending.length
            ? pending.map((enrollment) => {
                const classRecord = classes.find((item) => item.id === enrollment.classId);
                return `
                  <div class="data-row enrollment-row">
                    <div>
                      <strong>${escapeHtml(classRecord?.name || "Class")}</strong>
                      <span>${escapeHtml(childName(enrollment.childId))}</span>
                    </div>
                    <div>
                      <span>${escapeHtml(classRecord ? enrollmentDateRange(classRecord) : "Date range unavailable")}</span>
                      <span>${escapeHtml(classRecord ? classScheduleText(classRecord) : "Schedule unavailable")}</span>
                    </div>
                    <div>
                      <strong>${escapeHtml(statusLabel(enrollment.status))}</strong>
                      <span>${escapeHtml(`Registered ${formatDate(enrollment.createdAt) || "date unavailable"}`)}</span>
                    </div>
                    <div>
                      <button class="secondary-button compact-button" disabled type="button">Pay online</button>
                      <span>Payment processor setup pending</span>
                    </div>
                  </div>
                `;
              }).join("")
            : rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")
        }
      </div>
    `;
  }

  function pendingPaymentEnrollments() {
    return enrollments.filter((enrollment) => String(enrollment.status || "").toLowerCase() === "pending_payment");
  }

  function adminAttendanceGrid(rows) {
    const currentView = adminAttendanceView === "calendar" ? "calendar" : "table";
    return `
      <div class="list-filter-bar attendance-grid-filter">
        <label>
          <span>Class</span>
          <select data-attendance-class-filter ${classes.length ? "" : "disabled"}>
            ${
              classes.length
                ? classes.map((classRecord) => `
                  <option value="${escapeHtml(classRecord.id)}" ${selectedAttendanceClassId === classRecord.id ? "selected" : ""}>
                    ${escapeHtml(classRecord.name)}
                  </option>
                `).join("")
                : `<option value="">No classes available</option>`
            }
          </select>
        </label>
        ${attendanceGrid ? attendanceGridSummary(attendanceGrid) : ""}
        <button
          class="secondary-button compact-button attendance-refresh-button"
          data-attendance-refresh
          type="button"
          ${selectedAttendanceClassId && !loadingAttendance ? "" : "disabled"}
        >
          Refresh
        </button>
        ${
          attendanceGrid
            ? `<div class="attendance-view-switch" role="group" aria-label="Attendance view">
                <button
                  aria-pressed="${currentView === "table"}"
                  class="${currentView === "table" ? "is-active" : ""}"
                  data-attendance-view="table"
                  type="button"
                >
                  Table
                </button>
                <button
                  aria-pressed="${currentView === "calendar"}"
                  class="${currentView === "calendar" ? "is-active" : ""}"
                  data-attendance-view="calendar"
                  type="button"
                >
                  Calendar
                </button>
              </div>`
            : ""
        }
      </div>
      ${
        attendanceGrid
          ? currentView === "calendar"
            ? adminAttendanceCalendar(attendanceGrid)
            : `<div class="attendance-grid-shell">
                <div data-attendance-tabulator></div>
              </div>`
          : `<div class="data-list">${rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")}</div>`
      }
    `;
  }

  function attendanceGridSummary(grid) {
    const scheduledCount = (grid.dates || []).filter((date) => date.scheduled).length;
    const studentCount = (grid.students || []).length;
    const attendanceCount = (grid.students || [])
      .flatMap((student) => student.attendance || [])
      .filter((cell) => cell.checkedIn).length;
    return `
      <span class="attendance-grid-summary">
        ${escapeHtml(`${studentCount} student${studentCount === 1 ? "" : "s"} - ${scheduledCount} scheduled day${scheduledCount === 1 ? "" : "s"} - ${attendanceCount} check-in${attendanceCount === 1 ? "" : "s"}`)}
      </span>
    `;
  }

  function adminAttendanceCalendar(grid) {
    const dates = grid.dates || [];
    if (!dates.length) {
      return `<div class="data-list"><div class="data-row">No class dates are available.</div></div>`;
    }
    const summariesByDate = adminAttendanceSummariesByDate(grid);
    const firstDate = dates[0]?.classDate || grid.classRecord?.startDate;
    const lastDate = dates[dates.length - 1]?.classDate || grid.classRecord?.endDate;
    const months = calendarMonths(firstDate, lastDate);
    if (!months.length) {
      return `<div class="data-list"><div class="data-row">Class schedule dates are unavailable.</div></div>`;
    }
    return `
      <section class="attendance-calendar-panel admin-attendance-calendar" aria-label="Class attendance calendar">
        <div class="attendance-calendar-legend">
          <span><i class="calendar-key checked"></i>Attendance</span>
          <span><i class="calendar-key missed"></i>Missing check-ins</span>
          <span><i class="calendar-key scheduled"></i>Scheduled</span>
          <span><i class="calendar-key unscheduled"></i>No class</span>
        </div>
        <div class="attendance-calendar-months">
          ${months.map((month) => adminAttendanceCalendarMonth(month, summariesByDate)).join("")}
        </div>
      </section>
    `;
  }

  function adminAttendanceSummariesByDate(grid) {
    const summariesByDate = new Map();
    (grid.dates || []).forEach((date) => {
      summariesByDate.set(date.classDate, {
        classDate: date.classDate,
        scheduled: Boolean(date.scheduled),
        checkedCount: 0,
        scheduledCount: 0,
      });
    });
    (grid.students || []).forEach((student) => {
      (student.attendance || []).forEach((cell) => {
        if (!summariesByDate.has(cell.classDate)) {
          summariesByDate.set(cell.classDate, {
            classDate: cell.classDate,
            scheduled: Boolean(cell.scheduled),
            checkedCount: 0,
            scheduledCount: 0,
          });
        }
        const summary = summariesByDate.get(cell.classDate);
        summary.scheduled = summary.scheduled || Boolean(cell.scheduled);
        if (cell.scheduled) {
          summary.scheduledCount += 1;
        }
        if (cell.checkedIn) {
          summary.checkedCount += 1;
        }
      });
    });
    return summariesByDate;
  }

  function adminAttendanceCalendarMonth(monthStart, summariesByDate) {
    const monthLabel = monthStart.toLocaleDateString(undefined, { month: "long", year: "numeric" });
    const blanks = Array.from({ length: monthStart.getDay() }, () => `<span class="calendar-day is-empty" aria-hidden="true"></span>`).join("");
    const days = [];
    const cursor = new Date(monthStart);
    while (cursor.getMonth() === monthStart.getMonth()) {
      const value = localDateValue(cursor);
      const summary = summariesByDate.get(value);
      const status = adminAttendanceCalendarDayStatus(summary);
      days.push(`
        <span class="calendar-day ${status.className}" title="${escapeHtml(adminAttendanceCalendarDayTitle(value, summary))}">
          <strong>${cursor.getDate()}</strong>
          <small>${escapeHtml(status.label)}</small>
        </span>
      `);
      cursor.setDate(cursor.getDate() + 1);
    }
    return `
      <section class="attendance-calendar-month">
        <h4>${escapeHtml(monthLabel)}</h4>
        <div class="calendar-weekdays" aria-hidden="true">
          ${["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => `<span>${day}</span>`).join("")}
        </div>
        <div class="calendar-grid">${blanks}${days.join("")}</div>
      </section>
    `;
  }

  function adminAttendanceCalendarDayStatus(summary) {
    if (!summary?.scheduled) {
      return { className: "is-unscheduled", label: "" };
    }
    if (!summary.scheduledCount) {
      return { className: "is-scheduled", label: "0" };
    }
    if (summary.checkedCount > 0) {
      return { className: "is-checked", label: `${summary.checkedCount}/${summary.scheduledCount}` };
    }
    return { className: "is-missed", label: `${summary.checkedCount}/${summary.scheduledCount}` };
  }

  function adminAttendanceCalendarDayTitle(dateValue, summary) {
    if (!summary?.scheduled) {
      return `${formatDate(dateValue)} - No class`;
    }
    if (!summary.scheduledCount) {
      return `${formatDate(dateValue)} - Scheduled, no enrolled students`;
    }
    const missingCount = summary.scheduledCount - summary.checkedCount;
    return `${formatDate(dateValue)} - ${summary.checkedCount} of ${summary.scheduledCount} checked in${missingCount ? `, ${missingCount} missing` : ""}`;
  }

  async function initializeAttendanceGridTable(renderRoot) {
    const element = renderRoot.querySelector("[data-attendance-tabulator]");
    if (!element || !attendanceGrid) {
      return;
    }
    const Tabulator = await loadTabulator();
    if (!element.isConnected) {
      return;
    }
    const dates = attendanceGrid.dates || [];
    const rows = (attendanceGrid.students || []).map((student) => {
      const row = {
        childId: student.childId,
        childName: student.childName || "Student",
        parentEmail: student.parentEmail || "",
        parentPhone: student.parentPhone || "",
      };
      (student.attendance || []).forEach((cell) => {
        row[attendanceDateField(cell.classDate)] = cell;
      });
      return row;
    });
    const columns = [
      { title: "Student", field: "childName", frozen: true, headerFilter: "input", width: 180 },
      { title: "Parent email", field: "parentEmail", frozen: true, headerFilter: "input", width: 220 },
      { title: "Phone", field: "parentPhone", headerFilter: "input", width: 140 },
      ...dates.map((date) => ({
        title: attendanceDateHeader(date.classDate),
        field: attendanceDateField(date.classDate),
        headerSort: false,
        hozAlign: "center",
        width: 94,
        formatter: attendanceGridCellFormatter,
      })),
    ];
    new Tabulator(element, {
      data: rows,
      columns,
      height: "520px",
      index: "childId",
      layout: "fitData",
      placeholder: "No enrolled students for this class.",
      columnDefaults: {
        vertAlign: "middle",
      },
    });
  }

  function loadTabulator() {
    if (!tabulatorPromise) {
      tabulatorPromise = Promise.all([
        import("tabulator-tables"),
        import("tabulator-tables/dist/css/tabulator_simple.min.css"),
      ]).then(([module]) => module.TabulatorFull);
    }
    return tabulatorPromise;
  }

  function attendanceGridCellFormatter(cell) {
    const value = cell.getValue();
    const element = cell.getElement();
    element.classList.remove("is-checked", "is-missed", "is-unscheduled");
    if (!value) {
      element.title = "";
      return "";
    }
    const label = value.checkedIn ? "Checked in" : value.scheduled ? "No check-in" : "No class";
    const statusClass = value.checkedIn ? "is-checked" : value.scheduled ? "is-missed" : "is-unscheduled";
    const time = value.checkedInAt ? ` at ${new Date(value.checkedInAt).toLocaleString()}` : "";
    element.classList.add(statusClass);
    element.title = `${cell.getRow().getData().childName} - ${formatDate(value.classDate)} - ${label}${time}`;
    return `<span class="attendance-table-status ${statusClass}">${escapeHtml(value.checkedIn ? "In" : value.scheduled ? "-" : "")}</span>`;
  }

  function attendanceDateField(dateValue) {
    return `date_${String(dateValue || "").replace(/-/g, "_")}`;
  }

  function attendanceDateHeader(dateValue) {
    const date = dateFromLocalValue(dateValue);
    return `${date.getMonth() + 1}/${date.getDate()} ${date.toLocaleDateString("en-US", { weekday: "short" })}`;
  }

  function pendingCheckInTasks() {
    if (role === "PARENT") {
      return [];
    }
    if (!parentCheckInTaskDataReady()) {
      return [];
    }
    return eligibleAttendanceEnrollments()
      .flatMap((enrollment) => {
        const classRecord = classes.find((item) => item.id === enrollment.classId);
        if (!classRecord) {
          return [];
        }
        return parentAttendanceDateOptions()
          .filter((option) => option.key === "today")
          .filter((option) => parentAttendanceDateState(option.dateValue, enrollment, classRecord).kind === "valid")
          .map((option) => ({
            childId: enrollment.childId,
            classId: enrollment.classId,
            classRecord,
            dateKey: option.key,
            dateLabel: option.label,
            dateValue: option.dateValue,
          }));
      })
      .sort((left, right) =>
        left.dateValue.localeCompare(right.dateValue)
        || childName(left.childId).localeCompare(childName(right.childId))
        || className(left.classId).localeCompare(className(right.classId))
      );
  }

  function parentCheckInTaskDataReady() {
    return !loadingChildren && !loadingClasses && !loadingEnrollments && !loadingAttendance;
  }

  function parentCheckInTaskButton(tasks) {
    if (role !== "PARENT" || !tasks.length) {
      return "";
    }
    return `
      <button class="check-in-task-button" data-check-in-taskbar type="button">
        <span>Check-in tasks</span>
        <strong>${escapeHtml(String(tasks.length))}</strong>
      </button>
    `;
  }

  function checkInReminderModal(tasks) {
    return `
      <div class="modal-backdrop" data-check-in-reminder-modal>
        <section class="check-in-reminder-panel" role="dialog" aria-modal="true" aria-labelledby="check-in-reminder-title">
          <div class="workspace-heading workspace-heading-row">
            <div>
              <h3 id="check-in-reminder-title">Check-in tasks</h3>
              <p>${escapeHtml(`${tasks.length} pending check-in${tasks.length === 1 ? "" : "s"} for eligible class dates.`)}</p>
            </div>
            <button aria-label="Close check-in tasks" class="secondary-button compact-button" data-check-in-reminder-close type="button">Close</button>
          </div>
          <div class="check-in-task-grid" aria-label="Pending check-in tasks">
            ${tasks.map((task) => checkInTaskCard(task)).join("")}
          </div>
        </section>
      </div>
    `;
  }

  function checkInTaskCard(task) {
    return `
      <button
        class="check-in-task-card"
        data-parent-attendance-date="${escapeHtml(task.dateValue)}"
        data-attendance-child-id="${escapeHtml(task.childId)}"
        data-attendance-class-id="${escapeHtml(task.classId)}"
        type="button"
      >
        <span class="attendance-card-label">${escapeHtml(task.dateLabel)}</span>
        <strong>${escapeHtml(childName(task.childId))}</strong>
        <span>${escapeHtml(className(task.classId))}</span>
        <small>${escapeHtml(`${formatDate(task.dateValue)} - ${classScheduleText(task.classRecord)}`)}</small>
      </button>
    `;
  }

  function closeCheckInReminder() {
    checkInReminderOpen = false;
    checkInReminderDismissed = true;
    render();
  }

  function attendanceList() {
    return parentAttendancePanel();
  }

  function parentAttendancePanel() {
    const eligibleEnrollments = eligibleAttendanceEnrollments();
    return `
      <section class="parent-attendance-panel" aria-label="Parent attendance check-in">
        <div class="workspace-heading workspace-heading-row">
          <div>
            <h3>Check in</h3>
            <p>${escapeHtml(parentAttendancePanelSummary(eligibleEnrollments))}</p>
          </div>
          <span class="attendance-window-label">Yesterday / Today / Tomorrow</span>
        </div>

        ${parentAttendanceEnrollmentGrid(eligibleEnrollments)}
      </section>
    `;
  }

  function parentAttendancePanelSummary(eligibleEnrollments) {
    if (loadingChildren || loadingClasses || loadingEnrollments || loadingAttendance) {
      return "Loading attendance options.";
    }
    if (!eligibleEnrollments.length) {
      return "Enroll a child in an active class before checking in.";
    }
    return `${eligibleEnrollments.length} child and class ${eligibleEnrollments.length === 1 ? "option is" : "options are"} ready for check-in.`;
  }

  function eligibleAttendanceEnrollments() {
    return enrollments
      .filter((enrollment) => !["cancelled", "rejected"].includes(String(enrollment.status || "").toLowerCase()))
      .filter((enrollment) => children.some((child) => child.id === enrollment.childId))
      .filter((enrollment) => classes.some((classRecord) => classRecord.id === enrollment.classId));
  }

  function parentAttendanceEnrollmentGrid(eligibleEnrollments) {
    if (loadingChildren || loadingClasses || loadingEnrollments || loadingAttendance) {
      return `<div class="data-list"><div class="data-row">Loading attendance options...</div></div>`;
    }
    if (!eligibleEnrollments.length) {
      return `<div class="data-list"><div class="data-row">No enrolled children are available for check-in.</div></div>`;
    }
    return `
      <div class="parent-attendance-group-grid" aria-label="Child and class check-ins">
        ${eligibleEnrollments.map((enrollment) => parentAttendanceEnrollmentGroup(enrollment)).join("")}
      </div>
    `;
  }

  function parentAttendanceEnrollmentGroup(enrollment) {
    const classRecord = classes.find((item) => item.id === enrollment.classId);
    return `
      <section class="parent-attendance-group" aria-label="${escapeHtml(`${childName(enrollment.childId)} - ${classRecord?.name || "Class"}`)}">
        <header>
          <div>
            <span>Child and class</span>
            <h4>${escapeHtml(childName(enrollment.childId))}</h4>
            <p>${escapeHtml(classRecord ? `${classRecord.name} - ${classScheduleText(classRecord)}` : "Class details unavailable")}</p>
          </div>
          <small>${escapeHtml(statusLabel(enrollment.status || "enrolled"))}</small>
        </header>

        <div class="parent-attendance-cards" aria-label="${escapeHtml(`${childName(enrollment.childId)} check-in dates`)}">
          ${parentAttendanceDateOptions()
            .map((option) => parentAttendanceDateCard(option, enrollment, classRecord))
            .join("")}
        </div>
      </section>
    `;
  }

  function parentAttendanceDateOptions() {
    const today = new Date();
    return [
      { key: "yesterday", label: "Yesterday", dateValue: localDateValue(addDays(today, -1)) },
      { key: "today", label: "Today", dateValue: localDateValue(today) },
      { key: "tomorrow", label: "Tomorrow", dateValue: localDateValue(addDays(today, 1)) },
    ];
  }

  function parentAttendanceDateCard(option, enrollment, classRecord) {
    const state = parentAttendanceDateState(option.dateValue, enrollment, classRecord);
    const disabled = state.kind !== "valid";
    return `
      <button
        class="attendance-date-card is-${escapeHtml(state.kind)}"
        data-parent-attendance-date="${escapeHtml(option.dateValue)}"
        data-attendance-child-id="${escapeHtml(enrollment?.childId || "")}"
        data-attendance-class-id="${escapeHtml(enrollment?.classId || "")}"
        title="${escapeHtml(state.message)}"
        type="button"
        ${disabled ? "disabled" : ""}
      >
        <span class="attendance-card-label">${escapeHtml(option.label)}</span>
        <strong>${escapeHtml(formatDate(option.dateValue))}</strong>
        <span class="attendance-card-status">${escapeHtml(state.label)}</span>
        <small>${escapeHtml(state.message)}</small>
      </button>
    `;
  }

  function parentAttendanceDateState(dateValue, enrollment, classRecord) {
    if (!enrollment || !classRecord) {
      return {
        kind: "invalid",
        label: "Unavailable",
        message: "No enrolled child and class is selected.",
      };
    }
    const attendanceMessage = attendanceDateMessage(classRecord, dateValue);
    if (attendanceMessage) {
      return {
        kind: "invalid",
        label: "Not valid",
        message: attendanceMessage,
      };
    }
    const checkedRecord = attendanceRecords.find((record) =>
      record.childId === enrollment.childId
      && record.classId === enrollment.classId
      && record.classDate === dateValue
      && String(record.status || "").toLowerCase() === "checked_in"
    );
    if (checkedRecord) {
      return {
        kind: "checked",
        label: "Checked in",
        message: checkedRecord.checkedInAt
          ? `Checked in ${new Date(checkedRecord.checkedInAt).toLocaleString()}`
          : "Attendance has already been checked in.",
      };
    }
    return {
      kind: "valid",
      label: "Ready",
      message: `Click to check in ${childName(enrollment.childId)}.`,
    };
  }

  async function copyPublicClassLink(classId) {
    const link = `${window.location.origin}/school/${encodeURIComponent(school.slug)}/classes/${encodeURIComponent(classId)}`;
    try {
      await navigator.clipboard.writeText(link);
      notice = "Public class link copied.";
      error = "";
    } catch {
      notice = link;
      error = "Copy was blocked by the browser. The public link is shown above.";
    }
    render();
  }

  function programName(programId) {
    return programs.find((program) => program.id === programId)?.name || "Program";
  }

  function className(classId) {
    return classes.find((classRecord) => classRecord.id === classId)?.name || "Class";
  }

  function teacherName(teacher) {
    return [teacher.firstName, teacher.lastName].filter(Boolean).join(" ").trim() || teacher.email || "Teacher";
  }

  function childName(childId) {
    const child = children.find((item) => item.id === childId);
    return child ? childFullName(child) : "Child";
  }

  function childFullName(child) {
    return [child?.firstName, child?.lastName].filter(Boolean).join(" ").trim() || "Child";
  }

  function formatDate(value) {
    if (!value) {
      return "";
    }
    const normalized = String(value).includes("T") ? String(value) : `${value}T00:00:00`;
    return new Date(normalized).toLocaleDateString();
  }

  function enrollmentDateRange(classRecord) {
    if (!classRecord) {
      return "Date range unavailable";
    }
    return `${formatDate(classRecord.startDate)} - ${formatDate(classRecord.endDate)}`;
  }

  function statusLabel(status) {
    return String(status || "")
      .replace(/_/g, " ")
      .replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  function classScheduleText(classRecord) {
    const time = [classRecord.startTime, classRecord.endTime].filter(Boolean).join("-");
    if (classRecord.classType === "weekly") {
      const days = formatWeekdayList(classRecord.weekdays || []);
      return [days, time].filter(Boolean).join(" ");
    }
    return time || "Time range";
  }

  function formatWeekdayList(weekdays) {
    const labels = (weekdays || []).map(formatWeekdayLabel);
    if (labels.length <= 1) {
      return labels[0] || "";
    }
    return `${labels.slice(0, -1).join(", ")} and ${labels[labels.length - 1]}`;
  }

  function formatWeekdayLabel(weekday) {
    return String(weekday || "")
      .toLowerCase()
      .replace(/_/g, " ")
      .replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  function isEnrollmentClosed(classRecord) {
    if (String(classRecord?.status || "").toLowerCase() !== "active") {
      return true;
    }
    if (!classRecord?.registrationClosesAt) {
      return false;
    }
    return new Date(classRecord.registrationClosesAt).getTime() <= Date.now();
  }

  function dateFromLocalValue(value) {
    const [year, month, day] = String(value).split("-").map(Number);
    return new Date(year, month - 1, day);
  }

  function addDays(date, amount) {
    const next = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    next.setDate(next.getDate() + amount);
    return next;
  }

  function resetSiteWorkspaceData() {
    resetPrograms();
    resetClasses();
    resetStudents();
    resetAttendanceGrid();
  }

  function resetPrograms() {
    programRows = null;
    programs = [];
    selectedProgramId = "";
  }

  function resetClasses() {
    classRows = null;
    classes = [];
    selectedClassId = "";
    selectedClassPricing = null;
    classTeachers = [];
    loadingClassTeachers = false;
    resetAttendanceGrid();
  }

  function resetStudents() {
    students = [];
    selectedStudentClassId = "";
    loadingStudents = false;
  }

  function resetAttendanceGrid() {
    attendanceGrid = null;
    selectedAttendanceClassId = "";
    loadingAttendance = false;
  }

  async function loadPrograms() {
    const site = selectedSite();
    if (loadingPrograms || !school?.tenantId || !site) {
      return;
    }
    loadingPrograms = true;
    try {
      const response = await listPrograms(school.tenantId, site.id);
      programs = response.programs || [];
      if (selectedProgramId && !programs.some((program) => program.id === selectedProgramId)) {
        selectedProgramId = "";
      }
      programRows = programs.length
        ? programs.map((program) => `${program.name} - ${program.description || `${program.startDate} to ${program.endDate}`}`)
        : ["No programs have been created for this site yet."];
    } catch (loadError) {
      programRows = ["Programs could not be loaded."];
      error = loadError instanceof Error ? loadError.message : "Programs could not be loaded.";
    } finally {
      loadingPrograms = false;
      render();
    }
  }

  async function loadClasses() {
    const site = selectedSite();
    if (loadingClasses || !school?.tenantId || (role !== "PARENT" && role !== "TEACHER" && !site)) {
      return;
    }
    const cacheKey = role === "PARENT"
      ? `PARENT:${school.tenantId}`
      : role === "TEACHER"
        ? `TEACHER:${school.tenantId}`
        : `${school.tenantId}:${site.id}`;
    const cachedClasses = classListCache.get(cacheKey);
    if (cachedClasses && Date.now() - cachedClasses.loadedAt < CLASS_LIST_CACHE_TTL_MS) {
      applyLoadedClasses(cachedClasses.response);
      if (role === "SCHOOL_ADMIN" && adminMode === "checkIn" && checkInFlowStage === "intro" && selectedSiteId && selectedClassId) {
        await loadTodayCheckIns();
      }
      if (role === "SCHOOL_ADMIN" && activeSectionId === "attendance" && selectedAttendanceClassId && !attendanceGrid) {
        await loadAttendanceGrid();
      }
      return;
    }
    if (cachedClasses) {
      classListCache.delete(cacheKey);
    }
    loadingClasses = true;
    try {
      const response = role === "PARENT"
        ? await listAvailableClasses(school.tenantId)
        : role === "TEACHER"
          ? await listTeacherClasses(school.tenantId)
          : await listClasses(school.tenantId, site.id);
      classListCache.set(cacheKey, {
        loadedAt: Date.now(),
        response,
      });
      applyLoadedClasses(response);
    } catch (loadError) {
      classRows = ["Classes could not be loaded."];
      error = loadError instanceof Error ? loadError.message : "Classes could not be loaded.";
    } finally {
      loadingClasses = false;
      render();
    }
    if (role === "SCHOOL_ADMIN" && adminMode === "checkIn" && checkInFlowStage === "intro" && selectedSiteId && selectedClassId) {
      await loadTodayCheckIns();
    }
    if (role === "SCHOOL_ADMIN" && activeSectionId === "attendance" && selectedAttendanceClassId && !attendanceGrid) {
      await loadAttendanceGrid();
    }
  }

  async function loadSelectedClassPricing() {
    const classRecord = selectedClass();
    if (loadingClassPricing || !classRecord) {
      return;
    }
    loadingClassPricing = true;
    try {
      selectedClassPricing = await getTenantClassPricing(school.tenantId, classRecord.id);
    } catch (loadError) {
      selectedClassPricing = emptyClassPricing();
      if (loadError?.status === 404) {
        notice = "No pricing has been configured for this class yet.";
        error = "";
      } else {
        error = loadError instanceof Error ? loadError.message : "Class pricing could not be loaded.";
      }
    } finally {
      loadingClassPricing = false;
      render();
    }
  }

  async function loadClassTeachers() {
    if (loadingClassTeachers || !school?.tenantId || role !== "SCHOOL_ADMIN" || !selectedClassId) {
      return;
    }
    const classId = selectedClassId;
    loadingClassTeachers = true;
    render();
    try {
      const response = await listClassTeachers(school.tenantId, classId);
      if (selectedClassId === classId) {
        classTeachers = response.teachers || [];
      }
      error = "";
    } catch (loadError) {
      if (selectedClassId === classId) {
        classTeachers = [];
      }
      error = loadError instanceof Error ? loadError.message : "Class teachers could not be loaded.";
    } finally {
      loadingClassTeachers = false;
      render();
    }
  }

  async function closeEnrollmentForClass(classId) {
    const classRecord = classes.find((item) => item.id === classId);
    if (!classRecord) {
      return;
    }
    if (!window.confirm(`Close enrollment for ${classRecord.name}? Parents will no longer be able to enroll children in this class.`)) {
      return;
    }
    try {
      await closeClassEnrollment(school.tenantId, classId);
      notice = "Enrollment closed for this class.";
      error = "";
      await loadClassesAfterMutation();
      if (activeSectionId === "students") {
        await loadStudents();
      }
    } catch (actionError) {
      notice = "";
      error = actionError instanceof Error ? actionError.message : "Enrollment could not be closed.";
      render();
    }
  }

  async function stopSelectedClass(classId) {
    const classRecord = classes.find((item) => item.id === classId);
    if (!classRecord) {
      return;
    }
    if (!window.confirm(`Stop ${classRecord.name}? Parents will no longer be able to check in children for this class.`)) {
      return;
    }
    try {
      await stopClass(school.tenantId, classId);
      notice = "Class stopped.";
      error = "";
      await loadClassesAfterMutation();
      if (activeSectionId === "students") {
        await loadStudents();
      }
    } catch (actionError) {
      notice = "";
      error = actionError instanceof Error ? actionError.message : "Class could not be stopped.";
      render();
    }
  }

  async function loadClassesAfterMutation() {
    loadingClasses = false;
    classRows = null;
    invalidateClassListCache();
    await loadClasses();
  }

  function applyLoadedClasses(response) {
    classes = response.classes || [];
    if (selectedClassId && !classes.some((classRecord) => classRecord.id === selectedClassId)) {
      selectedClassId = "";
      classTeachers = [];
    }
    if (!selectedClassId && classes.length) {
      selectedClassId = classes[0].id;
    }
    if (selectedStudentClassId && !classes.some((classRecord) => classRecord.id === selectedStudentClassId)) {
      selectedStudentClassId = "";
    }
    if (selectedAttendanceClassId && !classes.some((classRecord) => classRecord.id === selectedAttendanceClassId)) {
      selectedAttendanceClassId = "";
      attendanceGrid = null;
    }
    if (role === "SCHOOL_ADMIN" && activeSectionId === "attendance" && !selectedAttendanceClassId && classes.length) {
      selectedAttendanceClassId = classes[0].id;
    }
    classRows = classes.length
      ? classes.map((classRecord) => `${classRecord.name} - ${programName(classRecord.programId)} - ${classScheduleText(classRecord)}`)
      : [role === "PARENT" ? "No active classes are available yet." : "No classes have been created for this site yet."];
    if (role === "TEACHER" && !classes.length) {
      classRows = ["No assigned classes loaded yet."];
    }
  }

  function invalidateClassListCache(siteId = selectedSiteId) {
    if (!siteId) {
      return;
    }
    classListCache.delete(`${school.tenantId}:${siteId}`);
    classListCache.delete(`PARENT:${school.tenantId}`);
    classListCache.delete(`TEACHER:${school.tenantId}`);
  }

  async function loadTeacherClasses() {
    if (loadingClasses || !school?.tenantId || role !== "TEACHER") {
      return;
    }
    await loadClasses();
  }

  async function loadChildren() {
    if (loadingChildren || !school?.tenantId) {
      return;
    }
    loadingChildren = true;
    try {
      const response = await listChildren(school.tenantId);
      children = response.children || [];
      if (selectedChildId && !children.some((child) => child.id === selectedChildId)) {
        selectedChildId = "";
      }
      childRows = children.length
        ? children.map((child) => `${child.firstName} ${child.lastName}`)
        : ["No child records loaded yet."];
      error = "";
    } catch (loadError) {
      childRows = ["Children could not be loaded."];
      error = loadError instanceof Error ? loadError.message : "Children could not be loaded.";
    } finally {
      loadingChildren = false;
      render();
    }
  }

  async function loadStudents() {
    if (loadingStudents || !school?.tenantId || role !== "SCHOOL_ADMIN") {
      return;
    }
    loadingStudents = true;
    try {
      const response = await listStudents(school.tenantId, selectedStudentClassId);
      students = response.students || [];
      error = "";
    } catch (loadError) {
      students = [];
      error = loadError instanceof Error ? loadError.message : "Students could not be loaded.";
    } finally {
      loadingStudents = false;
      render();
    }
  }

  async function loadEnrollments() {
    if (loadingEnrollments || !school?.tenantId || role !== "PARENT") {
      return;
    }
    loadingEnrollments = true;
    try {
      const response = await listParentEnrollments(school.tenantId);
      enrollments = response.enrollments || [];
    } catch (loadError) {
      error = loadError instanceof Error ? loadError.message : "Enrollments could not be loaded.";
    } finally {
      loadingEnrollments = false;
      render();
    }
  }

  async function loadAttendance() {
    if (loadingAttendance || !school?.tenantId || role !== "PARENT") {
      return;
    }
    loadingAttendance = true;
    try {
      const response = await listParentAttendance(school.tenantId);
      attendanceRecords = response.attendance || [];
      error = "";
    } catch (loadError) {
      error = loadError instanceof Error ? loadError.message : "Attendance could not be loaded.";
    } finally {
      loadingAttendance = false;
      render();
    }
  }

  async function loadAttendanceGrid() {
    if (loadingAttendance || !school?.tenantId || role !== "SCHOOL_ADMIN" || !selectedAttendanceClassId) {
      return;
    }
    loadingAttendance = true;
    render();
    try {
      attendanceGrid = await getClassAttendanceGrid(school.tenantId, selectedAttendanceClassId);
      error = "";
    } catch (loadError) {
      attendanceGrid = null;
      error = loadError instanceof Error ? loadError.message : "Class attendance could not be loaded.";
    } finally {
      loadingAttendance = false;
      render();
    }
  }

  async function openEnrollmentModal() {
    const classRecord = selectedClass();
    if (!classRecord) {
      error = "Select a class before enrolling.";
      notice = "";
      render();
      return;
    }
    enrollmentModalOpen = true;
    enrollmentPricing = null;
    loadingEnrollmentPricing = true;
    error = "";
    notice = "";
    render();
    if (!children.length) {
      await loadChildren();
    }
    try {
      enrollmentPricing = await getClassPricing(classRecord.id);
    } catch (pricingError) {
      enrollmentPricing = emptyClassPricing();
      error = pricingError instanceof Error ? pricingError.message : "Class pricing could not be loaded.";
    } finally {
      loadingEnrollmentPricing = false;
      render();
    }
  }

  function closeEnrollmentModal() {
    enrollmentModalOpen = false;
    enrollmentPricing = null;
    loadingEnrollmentPricing = false;
    render();
  }

  async function handleEnrollmentSubmit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const step = Number(form.querySelector("[data-enrollment-step-value]")?.value || 0);
    if (step && step < 3) {
      showTransientToast("Review the final step before submitting.", "error");
      return;
    }
    const selectedChildren = formValues(new FormData(form), "childIds");
    if (!selectedChildren.length) {
      showTransientToast("Select at least one child.", "error");
      return;
    }
    const paymentPreference = formText(new FormData(form), "paymentPreference", "skip");
    const submitButton = form.querySelector("button[type='submit']");
    submitButton.disabled = true;
    submitButton.textContent = "Enrolling";
    try {
      const result = await createEnrollment({
        tenantId: school.tenantId,
        classId: selectedClassId,
        childIds: selectedChildren,
        optionalFeeItemIds: formValues(new FormData(form), "optionalFeeItemIds"),
      });
      const redirectToPayments = result.paymentRequired && paymentPreference === "online";
      notice = redirectToPayments
        ? "Enrollment saved. Choose a pending payment to continue."
        : result.paymentRequired
          ? "Enrollment saved. Payment is required before the enrollment is complete."
          : "Enrollment completed.";
      error = "";
      enrollmentModalOpen = false;
      enrollmentPricing = null;
      await loadEnrollments();
      await loadAttendance();
      if (redirectToPayments) {
        activeSectionId = "payments";
        activeOperation = "";
      }
      render();
    } catch (enrollmentError) {
      notice = "";
      error = enrollmentError instanceof Error ? enrollmentError.message : "Enrollment could not be completed.";
      render();
    }
  }

  async function handleParentAttendanceCardClick(button) {
    const childId = button.dataset.attendanceChildId;
    const classId = button.dataset.attendanceClassId;
    const classDate = button.dataset.parentAttendanceDate;
    const enrollment = eligibleAttendanceEnrollments().find((entry) =>
      entry.childId === childId && entry.classId === classId
    );
    const classRecord = classes.find((item) => item.id === classId);
    const state = parentAttendanceDateState(classDate, enrollment, classRecord);
    if (state.kind !== "valid") {
      notice = "";
      error = state.message;
      showTransientToast(state.message, "error");
      render();
      return;
    }

    button.disabled = true;
    try {
      await checkInAttendance({
        tenantId: school.tenantId,
        childId,
        classId,
        classDate,
      });
      const successMessage = `${childName(childId)} checked in for ${formatDate(classDate)}.`;
      notice = successMessage;
      showTransientToast(successMessage);
      error = "";
      activeOperation = "";
      await loadAttendance();
      render();
    } catch (attendanceError) {
      const errorMessage = attendanceError instanceof Error ? attendanceError.message : "Attendance check-in could not be saved.";
      notice = "";
      error = errorMessage;
      showTransientToast(errorMessage, "error");
      render();
    }
  }

  async function handleFamilyRegisterClick(button) {
    const childId = button.dataset.familyRegisterChildId;
    const classId = button.dataset.familyRegisterClassId;
    const child = children.find((item) => item.id === childId);
    const classRecord = classes.find((item) => item.id === classId);
    if (!child || !classRecord) {
      showTransientToast("Child or class could not be found.", "error");
      return;
    }

    button.disabled = true;
    button.textContent = "Registering";
    try {
      const result = await createEnrollment({
        tenantId: school.tenantId,
        classId,
        childIds: [childId],
        optionalFeeItemIds: [],
      });
      notice = result.paymentRequired
        ? `${childName(childId)} enrolled in ${classRecord.name}. Payment is required before the enrollment is complete.`
        : `${childName(childId)} enrolled in ${classRecord.name}.`;
      error = "";
      showTransientToast(notice);
      await loadEnrollments();
      await loadAttendance();
      render();
    } catch (enrollmentError) {
      const message = enrollmentError instanceof Error ? enrollmentError.message : "Enrollment could not be created.";
      notice = "";
      error = message;
      showTransientToast(message, "error");
      render();
    } finally {
      button.disabled = false;
      button.textContent = "Register";
    }
  }

  async function loadNotifications() {
    if (loadingNotifications || !school?.tenantId) {
      return;
    }
    loadingNotifications = true;
    try {
      const [providersResponse, historyResponse] = await Promise.all([
        listNotificationProviders(school.tenantId),
        listNotificationHistory(school.tenantId),
      ]);
      notificationProviders = providersResponse.providers || [];
      notificationHistory = historyResponse.history || [];
      error = "";
    } catch (loadError) {
      error = loadError instanceof Error ? loadError.message : "Notifications could not be loaded.";
    } finally {
      loadingNotifications = false;
      render();
    }
  }

  async function connectGmail() {
    try {
      const response = await startGmailConnection(school.tenantId);
      const popup = window.open(response.authorizationUrl, "schooldays-gmail-oauth", "width=560,height=720");
      if (!popup) {
        window.location.href = response.authorizationUrl;
        return;
      }
      notice = "Complete Gmail permission in the popup.";
      error = "";
      render();
    } catch (connectError) {
      notice = "";
      error = connectError instanceof Error ? connectError.message : "Gmail connection could not be started.";
      render();
    }
  }

  async function handleTestNotification(button) {
    const form = button.closest("form");
    if (!form) {
      return;
    }
    const formData = new FormData(form);
    const testEmail = formText(formData, "testBccEmail");
    if (!testEmail) {
      showTransientToast("Enter a test BCC email before sending a test.", "error");
      return;
    }

    button.disabled = true;
    button.textContent = "Sending test";
    try {
      const payload = notificationPayload(formData);
      payload.bccEmails = splitEmails(testEmail);
      delete payload.classId;
      const sent = await sendNotification(school.tenantId, payload);
      notificationHistory = [sent, ...notificationHistory];
      showTransientToast("Test email sent.");
    } catch (testError) {
      showTransientToast(testError instanceof Error ? testError.message : "Test email could not be sent.", "error");
    } finally {
      button.disabled = false;
      button.textContent = "Send test";
    }
  }

  async function openProfilePanel() {
    profileOpen = true;
    notice = "";
    error = "";
    render();
    if (!profile && !loadingProfile) {
      loadingProfile = true;
      try {
        profile = await getProfile();
      } catch (loadError) {
        profile = profileFromUser(user);
        if (loadError?.status === 404) {
          error = "Profile service was not found. Restart the backend with the latest code, then reopen My profile.";
        } else {
          error = loadError instanceof Error ? loadError.message : "Profile could not be loaded.";
        }
      } finally {
        loadingProfile = false;
        render();
      }
    }
  }

  function closeProfilePanel() {
    profileOpen = false;
    render();
  }

  async function handleProfileSubmit(event) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    try {
      profile = await updateProfile({
        firstName: formText(formData, "firstName"),
        lastName: formText(formData, "lastName"),
        phone: formText(formData, "phone"),
      });
      user.phone = profile.phone;
      notice = "Profile updated.";
      error = "";
      render();
    } catch (submitError) {
      notice = "";
      error = submitError instanceof Error ? submitError.message : "Profile could not be updated.";
      render();
    }
  }

  async function handlePasswordSubmit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const newPassword = formText(formData, "newPassword");
    const confirmPassword = formText(formData, "confirmPassword");
    if (newPassword !== confirmPassword) {
      notice = "";
      error = "New password and confirmation do not match.";
      render();
      return;
    }
    try {
      await changePassword({
        currentPassword: formText(formData, "currentPassword"),
        newPassword,
      });
      notice = "Password changed.";
      error = "";
      form.reset();
      render();
    } catch (submitError) {
      notice = "";
      error = submitError instanceof Error ? submitError.message : "Password could not be changed.";
      render();
    }
  }

  function currentSections() {
    if (role !== "SCHOOL_ADMIN") {
      return dashboard.sections;
    }
    return adminMode === "site" ? schoolAdminSiteSections : schoolAdminManagementSections;
  }

  function adminModeSwitcher() {
    return `
      <div class="app-mode-switcher">
        <button class="${adminMode === "manage" ? "is-active" : ""}" data-admin-mode="manage" type="button">School setup</button>
        <button class="${adminMode === "site" ? "is-active" : ""}" data-admin-mode="site" type="button">Site workspace</button>
        <button class="${adminMode === "checkIn" ? "is-active" : ""}" data-admin-mode="checkIn" type="button">Check in</button>
        <button class="${adminMode === "inviteUser" ? "is-active" : ""}" data-admin-mode="inviteUser" type="button">Invite New User</button>
      </div>
    `;
  }

  function teacherModeSwitcher() {
    return `
      <div class="app-mode-switcher">
        <button class="${teacherMode === "choice" ? "is-active" : ""}" data-teacher-mode="choice" type="button">Choice screen</button>
        <button class="${teacherMode === "main" ? "is-active" : ""}" type="button" disabled>Main UI</button>
        <button class="${teacherMode === "checkIn" ? "is-active" : ""}" data-teacher-mode="checkIn" type="button">Check in</button>
        <button class="${teacherMode === "externalAttendance" ? "is-active" : ""}" data-teacher-mode="externalAttendance" type="button">External attendance</button>
      </div>
    `;
  }

  function renderTeacherChoiceScreen() {
    root.innerHTML = `
      <main class="admin-choice-page">
        <section class="admin-choice-shell">
          <header class="app-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2>Teacher portal</h2>
              <p>Choose the main classroom workspace or go straight to check-in.</p>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
            </div>
          </header>

          <div class="admin-choice-grid">
            <button class="admin-choice-card is-disabled" type="button" disabled>
              <span>Main UI</span>
              <strong>Open classroom workspace</strong>
              <small>Review your assigned classes, schedules, attendance, and notifications.</small>
            </button>
            <button class="admin-choice-card" data-teacher-enter-check-in type="button">
              <span>Check in</span>
              <strong>Open camera check-in</strong>
              <small>Choose one of your classes and start the check-in camera flow.</small>
            </button>
            <button class="admin-choice-card" data-teacher-enter-external-attendance type="button">
              <span>External attendance</span>
              <strong>Open attendance calendar</strong>
              <small>Review external student attendance for one of your classes.</small>
            </button>
          </div>
        </section>
      </main>
    `;

    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-teacher-enter-check-in]")?.addEventListener("click", () => {
      teacherMode = "checkIn";
      checkInFlowStage = "intro";
      checkInSelectionError = "";
      render();
    });
    root.querySelector("[data-teacher-enter-external-attendance]")?.addEventListener("click", () => {
      teacherMode = "externalAttendance";
      resetExternalAttendanceState();
      render();
    });
  }

  function renderTeacherCheckInIntro() {
    const selectedClassRecord = selectedClass();
    const todayValue = localDateValue(new Date());
    const classIsScheduledToday = Boolean(selectedClassRecord && isScheduledClassDate(selectedClassRecord, todayValue));
    root.innerHTML = `
      <main class="admin-choice-page check-in-intro-page">
        <section class="admin-choice-shell" aria-labelledby="teacher-check-in-title">
          <header class="app-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2 id="teacher-check-in-title">Teacher check-in</h2>
              <p class="context-note check-in-context-note">
                ${escapeHtml(selectedClassRecord ? `Current class: ${selectedClassRecord.name}.` : "Select a class before starting the camera check-in flow.")}
              </p>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-teacher-back-choice type="button">Back</button>
              <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
            </div>
          </header>

          <div class="admin-choice-grid">
            <section class="admin-choice-card teacher-check-in-card">
              <span>Camera check-in</span>
              <strong>Choose a class and start</strong>
              <small>Pick one of your assigned classes, then open the camera or check students in from the list.</small>
              <div class="teacher-check-in-card-body">
                <label class="check-in-selector-field">
                  <span>Class</span>
                  <select data-teacher-check-in-class-select ${loadingClasses ? "disabled" : ""}>
                    <option value="">Select class</option>
                    ${classes.map((classRecord) => `<option value="${escapeHtml(classRecord.id)}"${classRecord.id === selectedClassId ? " selected" : ""}>${escapeHtml(classRecord.name)}</option>`).join("")}
                  </select>
                </label>
                ${checkInSelectionError ? `<p class="message error" role="alert">${escapeHtml(checkInSelectionError)}</p>` : ""}
                <p class="check-in-today-count">${escapeHtml(renderTodayCheckInCountText())}</p>
                ${selectedClassRecord && !classIsScheduledToday ? `<p class="message error" role="alert">No class is scheduled today for the selected class.</p>` : ""}
              <div class="check-in-launch-actions">
                  <button class="check-in-launch-button" data-teacher-check-in-start type="button" ${!selectedClassId || !classIsScheduledToday ? "disabled" : ""}>Check In</button>
                  <button class="secondary-button compact-button" data-check-in-quick-toggle type="button" ${!selectedClassId || !classIsScheduledToday ? "disabled" : ""}>
                    Quick check-in
                  </button>
                  <button class="secondary-button compact-button" data-check-in-today-list type="button" ${!selectedClassId ? "disabled" : ""}>Today's check-in list</button>
                  <button class="secondary-button compact-button" data-check-in-print-sheet type="button" ${!selectedClassId ? "disabled" : ""}>Print check-out sheet</button>
                </div>
              </div>
            </section>
          </div>
        </section>
      </main>
      ${checkInQuickListOpen ? renderQuickCheckInList() : ""}
      ${checkInTodayListOpen ? renderCheckInTodayListModal() : ""}
    `;

    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-teacher-back-choice]")?.addEventListener("click", () => {
      teacherMode = "choice";
      checkInFlowStage = "intro";
      checkInSelectionError = "";
      resetQuickCheckInState();
      render();
    });
    root.querySelector("[data-teacher-check-in-class-select]")?.addEventListener("change", (event) => {
      selectedClassId = event.currentTarget.value;
      checkInSelectionError = "";
      checkInTodayListRows = [];
      checkInTodayListCount = 0;
      checkInTodayListQueryKey = "";
      checkInTodayListDate = "";
      resetQuickCheckInState();
      render();
      if (selectedClassId) {
        loadTodayCheckIns();
      }
    });
    root.querySelector("[data-teacher-check-in-start]")?.addEventListener("click", () => {
      if (!selectedClassId) {
        checkInSelectionError = "Select a class before starting check-in.";
        render();
        return;
      }
      const classRecord = selectedClass();
      const todayValue = localDateValue(new Date());
      if (classRecord && !isScheduledClassDate(classRecord, todayValue)) {
        checkInSelectionError = "No class is scheduled today for the selected class.";
        render();
        return;
      }
      resetQuickCheckInState();
      checkInFlowStage = "camera";
      render();
    });
    root.querySelector("[data-check-in-today-list]")?.addEventListener("click", handleShowTodayCheckIns);
    root.querySelector("[data-check-in-print-sheet]")?.addEventListener("click", handlePrintCheckOutSheet);
    bindQuickCheckInControls();
    root.querySelector("[data-check-in-quick-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeQuickCheckInList();
      }
    });
    root.querySelector("[data-check-in-today-list-close]")?.addEventListener("click", closeTodayCheckInsModal);
    root.querySelector("[data-check-in-today-list-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeTodayCheckInsModal();
      }
    });
    initializeQuickCheckInTabulator();
  }

  function renderTeacherCheckIn() {
    const currentClass = selectedClass();
    root.innerHTML = `
      <main class="admin-check-in-page">
        <section class="admin-check-in-shell" aria-labelledby="teacher-check-in-camera-title">
          <header class="admin-check-in-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2 id="teacher-check-in-camera-title">Check in</h2>
              <p class="check-in-context-line">
                ${escapeHtml(currentClass ? currentClass.name : "Choose a class")}
              </p>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-teacher-check-in-back type="button">Back</button>
              <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
            </div>
          </header>

          <section class="check-in-camera-panel">
            <aside class="check-in-result-panel" aria-live="polite">
              <p class="check-in-result-site">${escapeHtml(currentClass ? `Class: ${currentClass.name}` : "No class selected")}</p>
              <div class="check-in-status-block">
                <span class="check-in-result-label">Barcode status</span>
                <p class="check-in-status" data-check-in-status>${escapeHtml(checkInScannerStatus)}</p>
              </div>
              <div class="check-in-status-block">
                <span class="check-in-result-label">Manual check-in</span>
                <form class="check-in-manual-form" data-check-in-manual-form novalidate>
                  <input
                    class="text-input"
                    data-check-in-manual-input
                    type="text"
                    inputmode="numeric"
                    autocomplete="off"
                    placeholder="Student ID"
                    value="${escapeHtml(checkInManualStudentId)}"
                  >
                  <button type="submit" class="secondary-button compact-button" data-check-in-manual-submit>Submit</button>
                </form>
              </div>
              <div class="check-in-status-block">
                <span class="check-in-result-label">Today's check-ins</span>
                <p class="check-in-status">${escapeHtml(renderTodayCheckInCountText())}</p>
              </div>
            </aside>
            <div class="check-in-video-frame">
              <video autoplay muted playsinline data-check-in-video></video>
              <div class="check-in-target" aria-hidden="true"></div>
            </div>
          </section>
        </section>
      </main>
    `;

    root.querySelector("[data-logout]").addEventListener("click", handleLogout);
    root.querySelector("[data-teacher-check-in-back]")?.addEventListener("click", () => {
      checkInFlowStage = "intro";
      checkInManualStudentId = "";
      checkInScannerStatus = "Starting camera...";
      render();
    });
    root.querySelector("[data-check-in-today-list-close]")?.addEventListener("click", closeTodayCheckInsModal);
    root.querySelector("[data-check-in-today-list-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeTodayCheckInsModal();
      }
    });
    bindManualCheckInForm();
    startCheckInScanner();
  }

  function renderExternalAttendanceIntro() {
    if (role === "SCHOOL_ADMIN" && !loadingSites && !sites.length) {
      loadSites();
    }
    if (role === "SCHOOL_ADMIN" && !selectedSiteId && sites.length) {
      selectedSiteId = selectedSiteId || sites[0].id;
    }
    if (role === "SCHOOL_ADMIN" && selectedSiteId && !loadingClasses && !classes.length) {
      loadClasses();
    }
    if (role === "TEACHER" && !classes.length && !loadingClasses) {
      loadTeacherClasses();
    }
    if (classes.length && !selectedClassId) {
      selectedClassId = classes[0].id;
    }
    const currentSite = selectedSite();
    const currentClass = selectedClass();
    root.innerHTML = `
      <main class="admin-choice-page">
        <section class="admin-choice-shell" aria-labelledby="external-attendance-title">
          <header class="app-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2 id="external-attendance-title">External attendance</h2>
              <p>Choose a class, then open the external student attendance calendar.</p>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-external-attendance-back type="button">Back</button>
              <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
            </div>
          </header>

          <div class="admin-choice-grid">
            <section class="admin-choice-card teacher-check-in-card">
              <span>Attendance query</span>
              <strong>Choose site and class</strong>
              <small>${role === "TEACHER"
                ? "Teachers choose a class. School admins choose a site, then a class."
                : "Pick a site, then one of its classes before querying attendance."}</small>
              <div class="teacher-check-in-card-body">
                ${role === "SCHOOL_ADMIN" ? `
                  <label class="check-in-selector-field">
                    <span>Site</span>
                    <select data-external-attendance-site-select ${loadingSites ? "disabled" : ""}>
                      <option value="">Select site</option>
                      ${sites.map((site) => `<option value="${escapeHtml(site.id)}"${site.id === selectedSiteId ? " selected" : ""}>${escapeHtml(site.name)}</option>`).join("")}
                    </select>
                  </label>
                ` : ""}
                <label class="check-in-selector-field">
                  <span>Class</span>
                  <select data-external-attendance-class-select ${role === "SCHOOL_ADMIN" && (!selectedSiteId || loadingClasses) ? "disabled" : loadingClasses ? "disabled" : ""}>
                    <option value="">Select class</option>
                    ${classes.map((classRecord) => `<option value="${escapeHtml(classRecord.id)}"${classRecord.id === selectedClassId ? " selected" : ""}>${escapeHtml(classRecord.name)}</option>`).join("")}
                  </select>
                </label>
                ${externalAttendanceDetailError ? `<p class="message error" role="alert">${escapeHtml(externalAttendanceDetailError)}</p>` : ""}
                <p class="check-in-today-count">${escapeHtml(currentSite ? `Site: ${currentSite.name}` : role === "TEACHER" ? "Class list loaded from your assigned classes." : "Choose a site to load classes.")}</p>
                <div class="check-in-launch-actions">
                  <button class="check-in-launch-button" data-external-attendance-query type="button" ${!selectedClassId || (role === "SCHOOL_ADMIN" && !selectedSiteId) ? "disabled" : ""}>Query</button>
                </div>
              </div>
            </section>
          </div>
        </section>
      </main>
    `;

    root.querySelector("[data-logout]")?.addEventListener("click", handleLogout);
    root.querySelector("[data-external-attendance-back]")?.addEventListener("click", () => {
      if (role === "SCHOOL_ADMIN") {
        adminMode = "";
      } else {
        teacherMode = "choice";
      }
      resetExternalAttendanceState();
      render();
    });
    root.querySelector("[data-external-attendance-site-select]")?.addEventListener("change", (event) => {
      selectedSiteId = event.currentTarget.value;
      selectedClassId = "";
      resetExternalAttendanceState();
      render();
      if (selectedSiteId) {
        loadClasses();
      }
    });
    root.querySelector("[data-external-attendance-class-select]")?.addEventListener("change", (event) => {
      selectedClassId = event.currentTarget.value;
      resetExternalAttendanceState();
      render();
    });
    root.querySelector("[data-external-attendance-query]")?.addEventListener("click", () => {
      if (role === "SCHOOL_ADMIN" && !selectedSiteId) {
        externalAttendanceDetailError = "Choose a site before querying attendance.";
        render();
        return;
      }
      if (!selectedClassId) {
        externalAttendanceDetailError = role === "TEACHER"
          ? "Choose a class before querying attendance."
          : "Choose a class before querying attendance.";
        render();
        return;
      }
      externalAttendanceStage = "calendar";
      externalAttendanceDetailError = "";
      externalAttendanceDetailRows = [];
      externalAttendanceDate = "";
      externalAttendanceDetailOpen = false;
      render();
    });
  }

  function renderExternalAttendanceCalendar() {
    const classRecord = selectedClass();
    const months = classRecord ? calendarMonths(classRecord.startDate, classRecord.endDate) : [];
    if (classRecord && !externalAttendanceCountLoading && externalAttendanceCountQueryKey !== [selectedClassId, classRecord.startDate, classRecord.endDate].join(":")) {
      void loadExternalAttendanceCounts();
    }
    root.innerHTML = `
      <main class="admin-choice-page">
        <section class="admin-choice-shell" aria-labelledby="external-attendance-calendar-title">
          <header class="app-header">
            <div>
              <p class="eyebrow">${escapeHtml(school.name)}</p>
              <h2 id="external-attendance-calendar-title">External attendance</h2>
              <p>${escapeHtml(classRecord ? `${classRecord.name} - ${enrollmentDateRange(classRecord)}` : "Choose a class to open the attendance calendar.")}</p>
            </div>
            <div class="header-actions">
              <button class="secondary-button compact-button" data-external-attendance-back type="button">Back</button>
              <button class="secondary-button compact-button" data-logout type="button">Sign out</button>
            </div>
          </header>

          <div class="attendance-calendar-panel external-attendance-calendar-panel" aria-label="External student attendance calendar">
          <div class="attendance-calendar-legend">
            <span><i class="calendar-key checked"></i>Has check-ins</span>
            <span><i class="calendar-key scheduled"></i>Class day</span>
            <span><i class="calendar-key no-class"></i>No class</span>
          </div>
          ${externalAttendanceCountError ? `<p class="message error" role="alert">${escapeHtml(externalAttendanceCountError)}</p>` : ""}
            <div class="attendance-calendar-months">
              ${months.length
                ? months.map((month) => externalAttendanceCalendarMonth(month, classRecord)).join("")
                : `<div class="data-list"><div class="data-row">Class dates are unavailable.</div></div>`}
            </div>
          </div>

        </section>
      </main>
      ${externalAttendanceDetailOpen ? renderExternalAttendanceDetailModal() : ""}
    `;

    root.querySelector("[data-logout]")?.addEventListener("click", handleLogout);
    root.querySelectorAll("[data-external-attendance-day]").forEach((button) => {
      button.addEventListener("click", () => {
        const dateValue = button.dataset.externalAttendanceDay;
        if (!dateValue || button.disabled) {
          return;
        }
        externalAttendanceDetailOpen = true;
        void loadExternalAttendanceDetails(dateValue);
      });
    });
    root.querySelectorAll("[data-external-attendance-back]")?.forEach((button) => {
      button.addEventListener("click", () => {
        externalAttendanceStage = "intro";
        externalAttendanceDetailError = "";
        externalAttendanceDetailRows = [];
        externalAttendanceDate = "";
        render();
      });
    });
    root.querySelector("[data-external-attendance-detail-close]")?.addEventListener("click", closeExternalAttendanceDetailModal);
    root.querySelector("[data-external-attendance-detail-modal]")?.addEventListener("click", (event) => {
      if (event.target === event.currentTarget) {
        closeExternalAttendanceDetailModal();
      }
    });
    if (externalAttendanceDetailOpen) {
      void initializeExternalAttendanceDetailTabulator();
    }
  }
}

export function bestSchoolRole(user, tenantId) {
  if (hasTenantRole(user, tenantId, "SCHOOL_ADMIN")) {
    return "SCHOOL_ADMIN";
  }
  if (hasTenantRole(user, tenantId, "TEACHER")) {
    return "TEACHER";
  }
  if (hasTenantRole(user, tenantId, "PARENT")) {
    return "PARENT";
  }
  return "";
}

function hasTenantRole(user, tenantId, role) {
  return (user.tenantRoles || []).some(
    (tenantRole) => tenantRole.tenantId === tenantId && tenantRole.role === role
  );
}

function hasExternalAuth(user) {
  return Array.isArray(user?.authProviders) && user.authProviders.length > 0;
}

function profileMenu(user, { profileDisabled = false } = {}) {
  const email = user?.email || "Account";
  const initial = email.trim().charAt(0).toUpperCase() || "A";
  return `
    <div class="profile-menu">
      <button
        aria-label="Account menu"
        class="profile-button"
        data-profile-menu-toggle
        title="Signed in as ${escapeHtml(email)}"
        type="button"
      >
        ${escapeHtml(initial)}
      </button>
      <div class="profile-dropdown" data-profile-menu hidden>
        <p>Signed in as <strong>${escapeHtml(email)}</strong></p>
        ${profileDisabled
          ? `<button title="Profile editing is disabled for third-party parent sign-ins." type="button" disabled>My profile</button>`
          : `<button data-profile-action="profile" type="button">My profile</button>`}
        <button data-logout type="button">Sign out</button>
      </div>
    </div>
  `;
}

function profilePanel(profile, user, loading) {
  const account = profile || user || {};
  return `
    <div class="modal-backdrop" data-profile-modal>
      <section class="profile-panel" role="dialog" aria-modal="true" aria-labelledby="profile-title">
        <div class="workspace-heading workspace-heading-row">
          <div>
            <h3 id="profile-title">My profile</h3>
            <p>${escapeHtml(loading ? "Loading your profile..." : account.email || "Manage your account details and password.")}</p>
          </div>
          <button aria-label="Close profile" class="secondary-button compact-button" data-profile-close type="button">Close</button>
        </div>

        <div class="profile-panel-grid">
          <form class="profile-card" data-dirty-form data-profile-form>
            <h4>Profile</h4>
            <div class="readonly-profile-field">
              <span>Email</span>
              <strong>${escapeHtml(account.email || "")}</strong>
            </div>
            <label>
              <span>First name</span>
              <input name="firstName" type="text" value="${escapeHtml(account.firstName || "")}" />
            </label>
            <label>
              <span>Last name</span>
              <input name="lastName" type="text" value="${escapeHtml(account.lastName || "")}" />
            </label>
            <label>
              <span>Phone</span>
              <input name="phone" required type="tel" value="${escapeHtml(account.phone || "")}" />
            </label>
            <button type="submit">Save profile</button>
          </form>

          <form class="profile-card" data-dirty-form data-password-form>
            <h4>Password</h4>
            <label>
              <span>Current password</span>
              <input autocomplete="current-password" name="currentPassword" required type="password" />
            </label>
            <label>
              <span>New password</span>
              <input autocomplete="new-password" minlength="8" name="newPassword" required type="password" />
            </label>
            <label>
              <span>Confirm new password</span>
              <input autocomplete="new-password" minlength="8" name="confirmPassword" required type="password" />
            </label>
            <button type="submit">Change password</button>
          </form>
        </div>
      </section>
    </div>
  `;
}

function profileFromUser(user) {
  return {
    id: user?.id || "",
    email: user?.email || "",
    firstName: user?.firstName || "",
    lastName: user?.lastName || "",
    phone: user?.phone || "",
  };
}

function noticeToast(notice) {
  if (!notice) {
    return "";
  }
  return `
    <div class="toast success-toast" role="status" aria-live="polite">
      ${escapeHtml(notice)}
    </div>
  `;
}

function showTransientToast(message, type = "success") {
  const existingToast = document.querySelector("[data-transient-toast]");
  existingToast?.remove();

  const toast = document.createElement("div");
  toast.dataset.transientToast = "true";
  toast.className = `toast ${type === "error" ? "error-toast" : "success-toast"}`;
  toast.setAttribute("role", type === "error" ? "alert" : "status");
  toast.setAttribute("aria-live", "polite");
  toast.textContent = message;
  document.body.append(toast);
  window.setTimeout(() => toast.remove(), 3400);
}

function initializeDirtyForms(root) {
  root.querySelectorAll("[data-dirty-form]").forEach((form) => {
    form.dataset.initialFormState = serializeForm(form);
    updateDirtyForm(form);
    form.addEventListener("input", () => updateDirtyForm(form));
    form.addEventListener("change", () => updateDirtyForm(form));
  });
}

function updateDirtyForm(form) {
  if (!form) {
    return;
  }
  const submitButton = form.querySelector('button[type="submit"]');
  if (!submitButton) {
    return;
  }
  const notificationStep = Number(form.querySelector("[data-notification-step-value]")?.value || 0);
  if (notificationStep && notificationStep < 4) {
    submitButton.disabled = true;
    submitButton.title = "Complete all notification steps before sending";
    return;
  }
  const isDirty = serializeForm(form) !== form.dataset.initialFormState;
  submitButton.disabled = !isDirty;
  submitButton.title = isDirty ? "" : "No changes to save";
}

function validateRequiredCheckboxGroups(form) {
  const groups = Array.from(form.querySelectorAll("[data-required-checkbox-group]"));
  for (const group of groups) {
    const checked = group.querySelectorAll("input[type='checkbox']:checked:not(:disabled)").length > 0;
    if (!checked) {
      group.scrollIntoView({ block: "center", behavior: "smooth" });
      const legend = group.querySelector("legend")?.textContent?.replace("*", "").trim() || group.dataset.requiredCheckboxGroup;
      showTransientToast(`Select at least one ${legend}.`, "error");
      return false;
    }
  }
  return true;
}

function serializeForm(form) {
  return JSON.stringify(Array.from(new FormData(form).entries()));
}

function workspaceHint(section, role = "") {
  if (section.id === "overview") {
    if (role === "PARENT") {
      return "Scan each child’s profile, current registrations, and classes still open for enrollment.";
    }
    return "Choose an operation to start a workflow.";
  }
  if (section.id === "sites") {
    return "Manage sites and enter a site workspace from its row.";
  }
  if (section.id === "programs") {
    return "Manage the programs available at this site.";
  }
  if (section.id === "classes") {
    return section.actions.length
      ? "Manage classes, pricing, and public links from each row."
      : "Browse the classes published by this school.";
  }
  if (section.id === "attendance") {
    if (role === "PARENT") {
      return "Use the date cards to check in eligible enrolled children and review attendance history.";
    }
    return "Select a class to review student attendance by date.";
  }
  return "Choose an operation above to open the working panel for this area.";
}

function toolbarFor(section) {
  if (!section.actions.length) {
    return "";
  }
  if (["sites", "programs", "classes"].includes(section.id)) {
    return "";
  }
  return `
    <section class="operation-toolbar" aria-label="${escapeHtml(section.title)} actions">
      ${section.actions
        .map((action) => `<button type="button" data-operation-action="${escapeHtml(action)}">${escapeHtml(action)}</button>`)
        .join("")}
    </section>
  `;
}

function panelHeaderAction(section, role) {
  if (role !== "SCHOOL_ADMIN") {
    return "";
  }
  const addActions = {
    sites: "Add site",
    programs: "Add program",
    classes: "Add class",
  };
  const action = addActions[section.id];
  if (!action) {
    return "";
  }
  return `
    <button
      aria-label="${escapeHtml(action)}"
      class="icon-button"
      data-operation-action="${escapeHtml(action)}"
      title="${escapeHtml(action)}"
      type="button"
    >
      +
    </button>
  `;
}

function operationPanel(
  section,
  action,
  selectedSite,
  selectedProgram,
  selectedClass,
  selectedChild,
  selectedPricing,
  user,
  sites = [],
  programs = [],
  classes = [],
  loadingPricing = false
) {
  const fields = fieldsFor(action, selectedSite, selectedProgram, selectedClass, selectedChild, selectedPricing, user, sites, programs);
  const siteOperation = isSiteOperation(action);
  const pricingOperation = isPricingOperation(action);
  const notificationOperation = isNotificationOperation(action);
  return `
    <form class="operation-panel${notificationOperation ? " notification-modal-panel" : ""}" data-dirty-form data-operation-form>
      <div class="workspace-heading">
        <h3>${escapeHtml(operationTitle(action))}</h3>
        <p>${escapeHtml(operationDescription(section, action))}</p>
        ${loadingPricing ? `<p class="context-note">Loading existing pricing...</p>` : ""}
      </div>

      ${
        pricingOperation
          ? pricingRowsPanel(selectedPricing)
          : notificationOperation
            ? notificationComposePanel(classes)
            : `<div class="operation-grid">
              ${fields
                .map(
                  (field) => fieldFor(field)
                )
                .join("")}
            </div>`
      }

      ${siteOperation ? sitePlaceHiddenFields(selectedSite) : ""}

      <div class="operation-actions">
        <button type="submit">${notificationOperation ? "Send" : siteOperation ? "Save site" : "Save"}</button>
        <button class="secondary-button" data-operation-cancel type="button">Cancel</button>
      </div>
    </form>
  `;
}

function notificationModal(
  section,
  action,
  selectedSite,
  selectedProgram,
  selectedClass,
  selectedChild,
  selectedPricing,
  user,
  sites = [],
  programs = [],
  classes = [],
  loadingPricing = false
) {
  return `
    <div class="modal-backdrop" data-notification-modal>
      ${operationPanel(section, action, selectedSite, selectedProgram, selectedClass, selectedChild, selectedPricing, user, sites, programs, classes, loadingPricing)}
    </div>
  `;
}

function enrollmentModal(classRecord, children, pricing, enrollments, loadingPricing) {
  const feeItems = pricing?.feeItems || [];
  const requiredFees = feeItems.filter((item) => item.category === "required_fees");
  const optionalFees = feeItems.filter((item) => item.category === "optional_fees");
  const requiredFeeTotal = requiredFees.reduce((total, item) => total + Number(item.fee || 0), 0);
  const enrolledChildIds = new Set((enrollments || [])
    .filter((enrollment) => enrollment.classId === classRecord?.id && !["cancelled", "rejected"].includes(enrollment.status))
    .map((enrollment) => enrollment.childId));
  const selectableChildren = children || [];
  const allChildrenEnrolled = selectableChildren.length > 0 && selectableChildren.every((child) => enrolledChildIds.has(child.id));
  return `
    <div class="modal-backdrop" data-enrollment-modal>
      <form
        class="operation-panel enrollment-modal-panel"
        data-enrollment-form
        data-required-fee-total="${escapeHtml(requiredFeeTotal)}"
        data-required-fee-currency="${escapeHtml(requiredFees[0]?.currency || "USD")}"
      >
        <input data-enrollment-step-value name="enrollmentStep" type="hidden" value="1" />
        <div class="workspace-heading">
          <h3>Enroll children</h3>
          <p>${escapeHtml(classRecord ? `${classRecord.name} - ${classScheduleSummary(classRecord)}` : "Choose children for this class.")}</p>
        </div>

        <div class="wizard-steps enrollment-wizard-steps" aria-label="Enrollment steps">
          ${["Children", "Fees", "Submit"].map((label, index) => `
            <span class="${index === 0 ? "is-active" : ""}" data-enrollment-wizard-indicator="${index + 1}">${index + 1}. ${escapeHtml(label)}</span>
          `).join("")}
        </div>

        <div class="enrollment-wizard">
          <section data-enrollment-wizard-step="1">
            <fieldset class="checkbox-field" data-required-checkbox-group="children">
              <legend>Children <span class="required-marker">*</span></legend>
              <div class="checkbox-grid two-column-checkbox-grid">
                ${
                  selectableChildren.length
                    ? selectableChildren.map((child) => {
                        const alreadyEnrolled = enrolledChildIds.has(child.id);
                        return `
                          <label class="checkbox-option ${alreadyEnrolled ? "is-disabled" : ""}">
                            <input
                              ${alreadyEnrolled ? "checked disabled" : ""}
                              name="childIds"
                              type="checkbox"
                              value="${escapeHtml(child.id)}"
                            />
                            <span>${escapeHtml(`${child.firstName} ${child.lastName}`.trim())}${alreadyEnrolled ? " - already enrolled" : ""}</span>
                          </label>
                        `;
                      }).join("")
                    : `<p class="context-note">Add a child before enrolling in a class.</p>`
                }
              </div>
            </fieldset>
          </section>

          <section data-enrollment-wizard-step="2" hidden>
            <section class="enrollment-summary">
              <div>
                <strong>Required fees</strong>
                ${
                  loadingPricing
                    ? `<span>Loading fees...</span>`
                    : requiredFees.length
                      ? requiredFees.map((item) => `<span>${escapeHtml(item.name)}: ${formatMoney(item.fee, item.currency)}</span>`).join("")
                      : "<span>None</span>"
                }
              </div>
              <div>
                <strong>Status after submit</strong>
                <span>${requiredFeeTotal > 0 ? "Pending payment" : "Enrolled"}</span>
              </div>
            </section>

            <fieldset class="checkbox-field">
              <legend>Optional fees</legend>
              <div class="checkbox-grid two-column-checkbox-grid">
                ${
                  loadingPricing
                    ? `<p class="context-note">Loading fees...</p>`
                    : optionalFees.length
                      ? optionalFees.map((item) => `
                        <label class="checkbox-option">
                          <input
                            data-enrollment-fee-amount="${escapeHtml(item.fee || 0)}"
                            data-enrollment-fee-checkbox
                            data-enrollment-fee-currency="${escapeHtml(item.currency || "USD")}"
                            data-enrollment-fee-name="${escapeHtml(item.name)}"
                            name="optionalFeeItemIds"
                            type="checkbox"
                            value="${escapeHtml(item.id)}"
                          />
                          <span>${escapeHtml(item.name)} - ${formatMoney(item.fee, item.currency)}</span>
                        </label>
                      `).join("")
                      : `<p class="context-note">No optional fees are configured for this class.</p>`
                }
              </div>
            </fieldset>

            <fieldset
              class="checkbox-field"
              data-enrollment-payment-methods
              data-payment-touched="${requiredFeeTotal > 0 ? "true" : "false"}"
              ${requiredFeeTotal > 0 ? "" : "hidden"}
            >
              <legend>Payment</legend>
              <div class="checkbox-grid two-column-checkbox-grid">
                <label class="checkbox-option">
                  <input ${requiredFeeTotal > 0 ? "checked" : ""} name="paymentPreference" type="radio" value="online" />
                  <span>Pay online</span>
                </label>
                <label class="checkbox-option">
                  <input ${requiredFeeTotal > 0 ? "" : "checked"} name="paymentPreference" type="radio" value="skip" />
                  <span>Skip payment for now</span>
                </label>
              </div>
            </fieldset>
          </section>

          <section data-enrollment-wizard-step="3" hidden>
            <section class="enrollment-summary">
              <div>
                <strong>Class</strong>
                <span>${escapeHtml(classRecord ? classRecord.name : "Class")}</span>
                <span>${escapeHtml(classRecord ? classScheduleSummary(classRecord) : "")}</span>
              </div>
              <div>
                <strong>Required fees</strong>
                <span>${escapeHtml(formatMoney(requiredFeeTotal, requiredFees[0]?.currency || "USD"))}</span>
                <span>${escapeHtml(requiredFeeTotal > 0 ? "Payment can be completed online or later from Payments." : "No required payment.")}</span>
              </div>
              <div>
                <strong>Optional fees</strong>
                <span data-enrollment-review-optional>None</span>
              </div>
              <div>
                <strong>Total due</strong>
                <span data-enrollment-review-total>${escapeHtml(formatMoney(requiredFeeTotal, requiredFees[0]?.currency || "USD"))}</span>
              </div>
              <div>
                <strong>Payment</strong>
                <span data-enrollment-review-payment>${requiredFeeTotal > 0 ? "Pay online" : "No payment selected"}</span>
              </div>
            </section>
          </section>

          <div class="wizard-actions">
            <button class="secondary-button compact-button" data-enrollment-wizard-back disabled type="button">Back</button>
            <button class="secondary-button compact-button" data-enrollment-wizard-next ${!selectableChildren.length || allChildrenEnrolled ? "disabled" : ""} type="button">Next</button>
            <button ${!selectableChildren.length || allChildrenEnrolled ? "disabled" : ""} hidden type="submit">Enroll selected</button>
            <button class="secondary-button" data-enrollment-cancel type="button">Cancel</button>
          </div>
        </div>
      </form>
    </div>
  `;
}

function classScheduleSummary(classRecord) {
  const time = [classRecord?.startTime, classRecord?.endTime].filter(Boolean).join("-");
  if (classRecord?.classType === "weekly") {
    const days = (classRecord.weekdays || []).join(", ");
    return [days, time].filter(Boolean).join(" ");
  }
  return time || "Time range";
}

function formatMoney(cents, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency || "USD",
  }).format(Number(cents || 0) / 100);
}

function localDateValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function operationTitle(action) {
  if (isNotificationOperation(action)) {
    return "Free send";
  }
  if (isPricingOperation(action)) {
    return "Class pricing";
  }
  return action;
}

function operationDescription(section, action) {
  if (isPricingOperation(action)) {
    return "Add the fees parents may need to pay for this class. Required fees count toward enrollment payment; optional fees can be selected separately.";
  }
  if (isCreateSiteOperation(action)) {
    return "Create a site for this school.";
  }
  if (isEditSiteOperation(action)) {
    return "Update this site’s details.";
  }
  if (isCreateProgramOperation(action)) {
    return "Create a program for the selected site.";
  }
  if (isEditProgramOperation(action)) {
    return "Update the selected program.";
  }
  if (isCreateClassOperation(action)) {
    return "Create a class under a site program.";
  }
  if (isEditClassOperation(action)) {
    return "Update the selected class.";
  }
  if (isNotificationOperation(action)) {
    return "Upload an .eml template, review recipients, send a test email, then confirm the final BCC send.";
  }
  if (isCreateChildOperation(action)) {
    return "Add a student profile for your family.";
  }
  if (isEditChildOperation(action)) {
    return "Update the selected student profile.";
  }
  return `Complete this ${section.title.toLowerCase()} action.`;
}

function isSiteOperation(action) {
  return action.toLowerCase().includes("site");
}

function isCreateSiteOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("site") && (normalized.includes("create") || normalized.includes("add"));
}

function isEditSiteOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("site") && normalized.includes("edit");
}

function isLogOnSiteAction(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("log on") && normalized.includes("site");
}

function isProgramOperation(action) {
  return action.toLowerCase().includes("program");
}

function isCreateProgramOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("program") && (normalized.includes("create") || normalized.includes("add"));
}

function isEditProgramOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("program") && normalized.includes("edit");
}

function isClassOperation(action) {
  return action.toLowerCase().includes("class");
}

function isBrowseClassesAction(action) {
  return action.toLowerCase() === "browse classes";
}

function isCreateClassOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("class") && (normalized.includes("create") || normalized.includes("add"));
}

function isEditClassOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("class") && normalized.includes("edit");
}

function isPricingOperation(action) {
  return action.toLowerCase().includes("pricing");
}

function isAssignTeacherOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("teacher") && normalized.includes("assign");
}

function isNotificationOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("notification") || normalized.includes("message") || normalized === "free send";
}

function isCreateChildOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("child") && (normalized.includes("add") || normalized.includes("create"));
}

function isEditChildOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("child") && normalized.includes("edit");
}

function notificationComposePanel(classes = []) {
  return `
    <input data-notification-step-value name="notificationStep" type="hidden" value="1" />
    <input name="audience" type="hidden" value="manual" />
    <div class="wizard-steps" aria-label="Notification sending steps">
      ${["Template", "CC", "Test", "Recipients"].map((label, index) => `
        <span class="${index === 0 ? "is-active" : ""}" data-wizard-indicator="${index + 1}">${index + 1}. ${escapeHtml(label)}</span>
      `).join("")}
    </div>
    <div class="notification-wizard">
      <section data-wizard-step="1">
        <label>
          <span>.eml template <span class="required-marker" aria-label="required">*</span></span>
          <input accept=".eml,message/rfc822" data-eml-template name="emlTemplate" required type="file" />
        </label>
        <label>
          <span>Subject <span class="required-marker" aria-label="required">*</span></span>
          <input name="subject" placeholder="Subject from template" required type="text" />
        </label>
        <input name="body" type="hidden" value="Email body is provided by the uploaded .eml template." />
        <textarea name="templateEml" hidden></textarea>
      </section>

      <section data-wizard-step="2" hidden>
        <label>
          <span>CC address list</span>
          <textarea name="ccEmails" placeholder="admin@example.com, teacher@example.com"></textarea>
        </label>
      </section>

      <section data-wizard-step="3" hidden>
        <label>
          <span>Test BCC email</span>
          <input name="testBccEmail" placeholder="your-test@example.com" type="email" />
        </label>
        <button class="secondary-button compact-button" data-send-test-notification type="button">Send test</button>
      </section>

      <section data-wizard-step="4" hidden>
        <label>
          <span>BCC address list <span class="required-marker" aria-label="required">*</span></span>
          <textarea name="bccEmails" required placeholder="one@example.com, two@example.com"></textarea>
        </label>
        <div class="send-confirmation-note">
          Review the template, CC list, and BCC recipients before sending. A confirmation box will appear after you click Send.
        </div>
      </section>
      <div class="wizard-actions">
        <button class="secondary-button compact-button" data-wizard-back disabled type="button">Back</button>
        <button class="secondary-button compact-button" data-wizard-next type="button">Next</button>
      </div>
    </div>
  `;
}

function pricingRowsPanel(selectedPricing) {
  const rows = pricingRecords(selectedPricing);
  return `
    <div class="pricing-records" data-pricing-rows>
      ${rows.map((row) => pricingRowFields(row)).join("")}
    </div>
    <button class="secondary-button compact-button" data-add-pricing-row type="button">Add pricing record</button>
  `;
}

function pricingRowFields(row) {
  return `
    <div class="pricing-record-row" data-pricing-row>
      <label>
        <span>Category</span>
        <select name="pricingCategory">
          <option value="required_fees" ${row.category === "required_fees" ? "selected" : ""}>Required Fees</option>
          <option value="optional_fees" ${row.category === "optional_fees" ? "selected" : ""}>Optional Fees</option>
        </select>
      </label>
      <label>
        <span>Name</span>
        <input name="pricingName" placeholder="Tuition, snack, lunch" type="text" value="${escapeHtml(row.name || "")}" />
      </label>
      <label>
        <span>Currency</span>
        <select name="pricingCurrency">
          <option value="USD" ${row.currency === "USD" ? "selected" : ""}>USD</option>
        </select>
      </label>
      <label>
        <span>Fee</span>
        <input min="0" name="pricingFee" placeholder="0.00" step="0.01" type="number" value="${escapeHtml(row.feeDollars || "")}" />
      </label>
      <label class="pricing-note-field">
        <span>Note</span>
        <textarea name="pricingNote" placeholder="Optional note">${escapeHtml(row.note || "")}</textarea>
      </label>
      <button class="secondary-button compact-button pricing-remove-button" data-remove-pricing-row type="button">Remove</button>
    </div>
  `;
}

function siteQuotaText(quota) {
  if (quota.unlimitedSites) {
    return `${quota.currentSiteCount} sites used. This school has unlimited sites.`;
  }
  return `${quota.currentSiteCount} of ${quota.maxSites} sites used. ${quota.remainingSites} remaining.`;
}

function fieldFor(field) {
  if (field.type === "static") {
    return `
      <div class="form-static-field">
        <span>${escapeHtml(field.label)}</span>
        <strong>${escapeHtml(field.value || "")}</strong>
      </div>
    `;
  }
  if (field.type === "checkbox-group") {
    return `
      <fieldset
        class="checkbox-field"
        ${field.toggleFor ? `data-toggle-for="${escapeHtml(field.toggleFor)}"` : ""}
        ${field.required ? `data-required-checkbox-group="${escapeHtml(field.name)}"` : ""}
      >
        <legend>${fieldLabel(field)}</legend>
        ${inputFor(field)}
      </fieldset>
    `;
  }
  if (field.type === "checkbox") {
    return `
      <div class="checkbox-field single-checkbox-field">
        <span>${fieldLabel(field)}</span>
        ${inputFor(field)}
      </div>
    `;
  }
  return `
    <label>
      <span>${fieldLabel(field)}</span>
      ${inputFor(field)}
    </label>
  `;
}

function fieldLabel(field) {
  return `${escapeHtml(field.label)}${field.required ? ` <span class="required-marker" aria-label="required">*</span>` : ""}`;
}

function inputFor(field) {
  if (field.type === "readonly") {
    return `
      <input
        name="${escapeHtml(field.name)}"
        readonly
        type="text"
        value="${escapeHtml(field.value || "")}"
      />
    `;
  }
  if (field.type === "textarea") {
    return `<textarea name="${escapeHtml(field.name)}" placeholder="${escapeHtml(field.placeholder || "")}" ${field.required ? "required" : ""}>${escapeHtml(field.value || "")}</textarea>`;
  }
  if (field.type === "checkbox-group") {
    const selected = new Set(field.values || []);
    return `
      <div class="checkbox-grid">
        ${(field.options || [])
          .map((option) => {
            const value = optionValue(option);
            const label = optionLabel(option);
            return `
              <label class="checkbox-option">
                <input
                  ${selected.has(value) ? "checked" : ""}
                  name="${escapeHtml(field.name)}"
                  type="checkbox"
                  value="${escapeHtml(value)}"
                />
                <span>${escapeHtml(label)}</span>
              </label>
            `;
          })
          .join("")}
      </div>
    `;
  }
  if (field.type === "checkbox") {
    return `
      <label class="checkbox-option inline-checkbox">
        <input
          ${field.checked ? "checked" : ""}
          name="${escapeHtml(field.name)}"
          type="checkbox"
          value="true"
        />
        <span>${escapeHtml(field.help || "Enabled")}</span>
      </label>
    `;
  }
  if (field.type === "select") {
    return `
      <select name="${escapeHtml(field.name)}" ${field.required ? "required" : ""}>
        ${(field.options || [])
          .map((option) => {
            const value = optionValue(option);
            const label = optionLabel(option);
            return `<option value="${escapeHtml(value)}" ${value === field.value ? "selected" : ""}>${escapeHtml(label)}</option>`;
          })
          .join("")}
      </select>
    `;
  }
  const listId = field.suggestions?.length ? `${field.name}-suggestions` : "";
  return `
    <input
      ${field.googleAutocomplete ? "data-google-address-autocomplete" : ""}
      ${listId ? `list="${escapeHtml(listId)}"` : ""}
      ${field.min !== undefined ? `min="${escapeHtml(field.min)}"` : ""}
      name="${escapeHtml(field.name)}"
      placeholder="${escapeHtml(field.placeholder || "")}"
      ${field.required ? "required" : ""}
      ${field.step !== undefined ? `step="${escapeHtml(field.step)}"` : ""}
      type="${escapeHtml(field.type || "text")}"
      value="${escapeHtml(field.value || "")}"
    />
    ${listId ? datalistFor(listId, field.suggestions) : ""}
  `;
}

function optionValue(option) {
  if (option && typeof option === "object") {
    return String(option.value ?? option.label ?? "");
  }
  return String(option ?? "");
}

function optionLabel(option) {
  if (option && typeof option === "object") {
    return String(option.label ?? option.value ?? "");
  }
  return String(option ?? "");
}

function datalistFor(id, suggestions) {
  return `
    <datalist id="${escapeHtml(id)}">
      ${suggestions.map((suggestion) => `<option value="${escapeHtml(suggestion)}"></option>`).join("")}
    </datalist>
  `;
}

function fieldsFor(
  action,
  selectedSite = null,
  selectedProgram = null,
  selectedClass = null,
  selectedChild = null,
  selectedPricing = null,
  user = null,
  sites = [],
  availablePrograms = []
) {
  const normalized = action.toLowerCase();

  if (normalized.includes("site")) {
    return [
      { name: "name", label: "Site name", placeholder: "Downtown Studio", value: selectedSite?.name || "" },
      {
        name: "timezone",
        label: "Timezone",
        type: "select",
        options: timezoneOptions(selectedSite?.timezone),
        value: selectedSite?.timezone || defaultTimezone(),
      },
      { name: "status", label: "Status", type: "select", options: ["active", "inactive"], value: selectedSite?.status || "active" },
      {
        name: "streetAddress",
        label: "Street address",
        placeholder: "123 Main St",
        value: selectedSite?.streetAddress || "",
        googleAutocomplete: true,
        suggestions: suggestionsFromSites(sites, "streetAddress", ["123 Main St", "456 Center St", "789 School Rd"]),
      },
      {
        name: "suite",
        label: "Suite",
        placeholder: "Suite 200",
        value: selectedSite?.suite || "",
        suggestions: suggestionsFromSites(sites, "suite", ["Suite 100", "Suite 200", "Room 101"]),
      },
      {
        name: "city",
        label: "City",
        placeholder: "Ann Arbor",
        value: selectedSite?.city || "",
        suggestions: suggestionsFromSites(sites, "city", [
          "Ann Arbor",
          "Detroit",
          "Troy",
          "Novi",
          "Farmington Hills",
          "Canton",
          "Livonia",
        ]),
      },
      { name: "state", label: "State", type: "select", options: stateOptions(), value: selectedSite?.state || "MI" },
      {
        name: "zipCode",
        label: "Zip code",
        placeholder: "48104",
        value: selectedSite?.zipCode || "",
        suggestions: suggestionsFromSites(sites, "zipCode", ["48104", "48105", "48226", "48084"]),
      },
      {
        name: "ownerFullName",
        label: "Site owner full name",
        placeholder: "Owner name",
        value: selectedSite?.ownerFullName || defaultOwnerName(user),
      },
      { name: "ownerPhone", label: "Owner phone", placeholder: "555-0100", value: selectedSite?.ownerPhone || user?.phone || "" },
      {
        name: "ownerEmail",
        label: "Owner email",
        type: "email",
        placeholder: "owner@example.com",
        value: selectedSite?.ownerEmail || user?.email || "",
      },
      {
        name: "gradeLevelsServed",
        label: "Grade levels served",
        type: "checkbox-group",
        options: gradeLevelOptions(),
        values: selectedSite ? selectedGradeLevels(selectedSite.gradeLevelsServed) : gradeLevelOptions(),
      },
    ];
  }
  if (normalized.includes("program")) {
    const programForForm = isCreateProgramOperation(action) ? null : selectedProgram;
    return [
      { name: "name", label: "Program name", placeholder: "Summer Art Camp", value: programForForm?.name || "" },
      {
        name: "description",
        label: "Description",
        type: "textarea",
        placeholder: "Describe the program focus, goals, and audience.",
        value: programForForm?.description || "",
      },
      { name: "startDate", label: "Start date", type: "date", value: programForForm?.startDate || "" },
      { name: "endDate", label: "End date", type: "date", value: programForForm?.endDate || "" },
    ];
  }
  if (normalized.includes("pricing")) {
    return [
      {
        name: "pricingType",
        label: "Fee category",
        type: "select",
        options: [
          { value: "paid", label: "Required Fees" },
          { value: "free", label: "Optional Fees" },
        ],
        value: selectedPricing?.pricingType || "paid",
      },
      {
        name: "currency",
        label: "Currency",
        type: "select",
        options: ["USD"],
        value: selectedPricing?.currency || "USD",
      },
      {
        name: "tuitionAmount",
        label: "Required Fees: tuition",
        type: "number",
        min: "0",
        step: "0.01",
        placeholder: "0.00",
        value: feeAmountDollars(selectedPricing, "tuition"),
      },
      {
        name: "snackAmount",
        label: "Optional Fees: snack",
        type: "number",
        min: "0",
        step: "0.01",
        placeholder: "0.00",
        value: feeAmountDollars(selectedPricing, "snack"),
      },
      {
        name: "snackRequired",
        label: "Move snack to Required Fees",
        type: "checkbox",
        help: "Require this fee for enrollment",
        checked: feeRequired(selectedPricing, "snack", false),
      },
      {
        name: "lunchAmount",
        label: "Optional Fees: lunch",
        type: "number",
        min: "0",
        step: "0.01",
        placeholder: "0.00",
        value: feeAmountDollars(selectedPricing, "lunch"),
      },
      {
        name: "lunchRequired",
        label: "Move lunch to Required Fees",
        type: "checkbox",
        help: "Require this fee for enrollment",
        checked: feeRequired(selectedPricing, "lunch", false),
      },
    ];
  }
  if (normalized.includes("class") || normalized.includes("schedule") || normalized.includes("roster")) {
    const classForForm = isCreateClassOperation(action) ? null : selectedClass;
    const classEditableFields = [
      ...(classForForm?.classType === "time_range" ? [] : [{
        name: "weekdays",
        label: "Week days",
        type: "checkbox-group",
        toggleFor: "weekly",
        options: weekdayOptions(),
        required: true,
        values: classForForm?.weekdays?.length ? classForForm.weekdays : weekdayOptions(),
      }]),
      { name: "capacity", label: "Capacity", type: "number", placeholder: "12", required: true, value: classForForm?.capacity || "" },
      { name: "startDate", label: "Start date", type: "date", required: true, value: classForForm?.startDate || "" },
      { name: "endDate", label: "End date", type: "date", required: true, value: classForForm?.endDate || "" },
      { name: "startTime", label: "Start time", type: "time", required: true, value: classForForm?.startTime || "" },
      { name: "endTime", label: "End time", type: "time", required: true, value: classForForm?.endTime || "" },
      {
        name: "description",
        label: "Description",
        type: "textarea",
        placeholder: "Describe the class format, topic, and expected skill level.",
        value: classForForm?.description || "",
      },
    ];
    if (!isCreateClassOperation(action)) {
      return [
        {
          label: "Program",
          type: "static",
          value: availablePrograms.find((program) => program.id === classForForm?.programId)?.name || "Program",
        },
        { label: "Class name", type: "static", value: classForForm?.name || "" },
        {
          label: "Class type",
          type: "static",
          value: classForForm?.classType === "time_range" ? "Time range" : "Weekly",
        },
        ...classEditableFields,
      ];
    }
    return [
      {
        name: "programId",
        label: "Program",
        type: "select",
        options: availablePrograms.map((program) => ({ value: program.id, label: program.name })),
        required: true,
        value: classForForm?.programId || selectedProgram?.id || availablePrograms[0]?.id || "",
      },
      { name: "name", label: "Class name", placeholder: "Beginner Drawing", required: true, value: classForForm?.name || "" },
      {
        name: "classType",
        label: "Class type",
        type: "select",
        required: true,
        options: [
          { value: "weekly", label: "Weekly" },
          { value: "time_range", label: "Time range" },
        ],
        value: classForForm?.classType || "weekly",
      },
      ...classEditableFields,
    ];
  }
  if (normalized.includes("teacher")) {
    return [
      { name: "email", label: "Teacher email", type: "email", placeholder: "teacher@example.com", required: true },
      { name: "className", label: "Class", type: "static", value: selectedClass?.name || "Selected class" },
    ];
  }
  if (normalized.includes("payment") || normalized.includes("receipt") || normalized.includes("refund") || normalized.includes("stripe")) {
    return [
      { name: "amount", label: "Amount", type: "number", placeholder: "12500" },
      { name: "method", label: "Method", type: "select", options: ["stripe", "check", "cash", "zelle", "wire"] },
      { name: "notes", label: "Notes", type: "textarea", placeholder: "Payment notes" },
    ];
  }
  if (normalized.includes("attendance") || normalized.includes("check in")) {
    return [
      { name: "student", label: "Student", placeholder: "Student name" },
      { name: "classDate", label: "Class date", type: "date" },
      { name: "status", label: "Status", type: "select", options: ["checked_in", "absent", "late"] },
    ];
  }
  if (normalized.includes("notification") || normalized.includes("message") || normalized.includes("provider")) {
    return [
      { name: "subject", label: "Subject", placeholder: "Class reminder" },
      { name: "cc", label: "CC", placeholder: "admin@example.com" },
      { name: "body", label: "Body", type: "textarea", placeholder: "Email content or notes" },
    ];
  }
  if (normalized.includes("child") || normalized.includes("student")) {
    const childForForm = isCreateChildOperation(action) ? null : selectedChild;
    return [
      { name: "firstName", label: "First name", placeholder: "First name", required: true, value: childForForm?.firstName || "" },
      { name: "lastName", label: "Last name", placeholder: "Last name", required: true, value: childForForm?.lastName || "" },
      { name: "dateOfBirth", label: "Date of birth", type: "date", required: true, value: childForForm?.dateOfBirth || "" },
      {
        name: "gender",
        label: "Gender",
        type: "select",
        required: true,
        options: [
          { value: "", label: "Select gender" },
          { value: "female", label: "Female" },
          { value: "male", label: "Male" },
          { value: "non_binary", label: "Non-binary" },
          { value: "prefer_not_to_say", label: "Prefer not to say" },
        ],
        value: childForForm?.gender || "",
      },
      {
        name: "grade",
        label: "Grade",
        type: "select",
        required: true,
        options: [
          { value: "", label: "Select grade" },
          ...gradeLevelOptions().map((grade) => ({ value: grade, label: grade })),
        ],
        value: childForForm?.grade || "",
      },
      { name: "school", label: "School", placeholder: "Current school name", required: true, value: childForForm?.school || "" },
      {
        name: "race",
        label: "Race",
        type: "checkbox-group",
        required: true,
        options: [
          { value: "american_indian_or_alaska_native", label: "American Indian or Alaska Native" },
          { value: "asian", label: "Asian" },
          { value: "black_or_african_american", label: "Black or African American" },
          { value: "hispanic_or_latino", label: "Hispanic or Latino" },
          { value: "middle_eastern_or_north_african", label: "Middle Eastern or North African" },
          { value: "native_hawaiian_or_pacific_islander", label: "Native Hawaiian or Pacific Islander" },
          { value: "white", label: "White" },
          { value: "two_or_more_races", label: "Two or more races" },
          { value: "prefer_not_to_say", label: "Prefer not to say" },
        ],
        values: childForForm?.race || [],
      },
      {
        name: "note",
        label: "Note",
        type: "textarea",
        placeholder: "Allergies, pickup notes, learning needs, or anything the school should know.",
        value: childForForm?.note || "",
      },
    ];
  }
  if (normalized.includes("enrollment") || normalized.includes("register")) {
    return [
      { name: "child", label: "Child", placeholder: "Child name" },
      { name: "className", label: "Class", placeholder: "Beginner Drawing" },
      { name: "dates", label: "Dates", placeholder: "Mondays in July" },
    ];
  }

  return [
    { name: "name", label: "Name", placeholder: action },
    { name: "notes", label: "Notes", type: "textarea", placeholder: "Workflow notes" },
  ];
}

function suggestionsFromSites(sites, fieldName, fallback = []) {
  return Array.from(new Set([
    ...sites.map((site) => site[fieldName]).filter(Boolean),
    ...fallback,
  ])).sort();
}

function stateOptions() {
  return [
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
    "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
    "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
    "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    "DC",
  ];
}

function gradeLevelOptions() {
  return ["PRE-K", "K", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"];
}

function weekdayOptions() {
  return ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
}

function selectedGradeLevels(value = "") {
  return String(value)
    .split(",")
    .map((grade) => grade.trim())
    .filter(Boolean);
}

function defaultTimezone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "America/New_York";
}

function defaultOwnerName(user) {
  if (!user?.email) {
    return "";
  }
  return user.email.split("@")[0].replace(/[._-]+/g, " ");
}

function sitePayload(formData, site, user) {
  return {
    name: formText(formData, "name", site?.name),
    timezone: formText(formData, "timezone", site?.timezone || defaultTimezone()),
    status: formText(formData, "status", site?.status || "active"),
    streetAddress: formText(formData, "streetAddress", site?.streetAddress),
    suite: formText(formData, "suite", site?.suite),
    city: formText(formData, "city", site?.city),
    state: formText(formData, "state", site?.state),
    zipCode: formText(formData, "zipCode", site?.zipCode),
    ownerFullName: formText(formData, "ownerFullName", site?.ownerFullName || defaultOwnerName(user)),
    ownerPhone: formText(formData, "ownerPhone", site?.ownerPhone || user?.phone),
    ownerEmail: formText(formData, "ownerEmail", site?.ownerEmail || user?.email),
    gradeLevelsServed: formValues(formData, "gradeLevelsServed").join(", "),
    googlePlaceId: formText(formData, "googlePlaceId", site?.googlePlaceId),
    formattedAddress: formText(formData, "formattedAddress", site?.formattedAddress),
    latitude: formText(formData, "latitude", site?.latitude),
    longitude: formText(formData, "longitude", site?.longitude),
  };
}

function programPayload(formData, site, program = null) {
  const payload = {
    name: formText(formData, "name", program?.name),
    description: formText(formData, "description", program?.description),
    startDate: formText(formData, "startDate", program?.startDate),
    endDate: formText(formData, "endDate", program?.endDate),
  };
  if (site?.id) {
    payload.siteId = site.id;
  }
  return payload;
}

function classPayload(formData, classRecord = null) {
  const capacity = formText(formData, "capacity", classRecord?.capacity);
  const classType = formText(formData, "classType", classRecord?.classType || "weekly");
  return {
    programId: formText(formData, "programId", classRecord?.programId),
    name: formText(formData, "name", classRecord?.name),
    description: formText(formData, "description", classRecord?.description),
    classType,
    weekdays: classType === "weekly" ? formValues(formData, "weekdays") : [],
    capacity: capacity ? Number(capacity) : null,
    startDate: formText(formData, "startDate", classRecord?.startDate),
    endDate: formText(formData, "endDate", classRecord?.endDate),
    startTime: formText(formData, "startTime", classRecord?.startTime),
    endTime: formText(formData, "endTime", classRecord?.endTime),
  };
}

function childPayload(formData, tenantId, child = null) {
  return {
    tenantId,
    firstName: formText(formData, "firstName", child?.firstName),
    lastName: formText(formData, "lastName", child?.lastName),
    dateOfBirth: formText(formData, "dateOfBirth", child?.dateOfBirth) || null,
    gender: formText(formData, "gender", child?.gender),
    grade: formText(formData, "grade", child?.grade),
    school: formText(formData, "school", child?.school),
    race: formValues(formData, "race"),
    note: formText(formData, "note", child?.note),
  };
}

function emptyClassPricing() {
  return {
    pricingType: "paid",
    currency: "USD",
    totalAmount: 0,
    feeItems: [],
  };
}

function emptyPricingRecord() {
  return {
    category: "required_fees",
    name: "",
    currency: "USD",
    feeDollars: "",
    note: "",
  };
}

function pricingRecords(pricing) {
  const records = (pricing?.feeItems || []).map((item) => ({
    category: item.category || item.feeType || "required_fees",
    name: item.name || "",
    currency: item.currency || pricing?.currency || "USD",
    feeDollars: centsToDollars(item.fee ?? item.amount),
    note: item.note || "",
  }));
  return records.length ? records : [emptyPricingRecord()];
}

function pricingPayload(formData) {
  const categories = formValues(formData, "pricingCategory");
  const names = formValuesIncludingBlank(formData, "pricingName");
  const currencies = formValuesIncludingBlank(formData, "pricingCurrency");
  const fees = formValuesIncludingBlank(formData, "pricingFee");
  const notes = formValuesIncludingBlank(formData, "pricingNote");
  const feeItems = categories
    .map((category, index) => ({
      category,
      name: names[index] || "",
      currency: currencies[index] || "USD",
      fee: dollarsToCents(fees[index]),
      note: notes[index] || "",
    }))
    .filter((item) => item.name && item.fee > 0);

  return {
    pricingType: feeItems.some((item) => item.category === "required_fees" && item.fee > 0) ? "paid" : "free",
    currency: feeItems[0]?.currency || "USD",
    feeItems,
  };
}

function notificationPayload(formData) {
  const audience = formText(formData, "audience", "manual");
  const payload = {
    ccEmails: splitEmails(formText(formData, "ccEmails")),
    bccEmails: splitEmails(formText(formData, "bccEmails")),
    subject: formText(formData, "subject"),
    body: formText(formData, "body"),
    bodyMimeType: "text/plain",
    templateEml: String(formData.get("templateEml") || ""),
  };
  if (audience.startsWith("class:")) {
    payload.classId = audience.slice("class:".length);
    payload.bccEmails = [];
  }
  return payload;
}

function splitEmails(value) {
  return String(value || "")
    .split(/[\s,;]+/)
    .map((email) => email.trim())
    .filter(Boolean);
}

function parseEmlTemplate(raw) {
  const normalized = String(raw || "").replace(/\r\n/g, "\n");
  const separatorIndex = normalized.search(/\n\n/);
  const headerText = separatorIndex >= 0 ? normalized.slice(0, separatorIndex) : normalized;
  const headers = parseEmlHeaders(headerText);

  return {
    subject: decodeMimeHeader(headers.get("subject") || ""),
    ccEmails: splitEmails(decodeMimeHeader(headers.get("cc") || "")),
  };
}

function parseEmlHeaders(headerText) {
  const unfolded = [];
  String(headerText || "").split("\n").forEach((line) => {
    if (/^[\t ]/.test(line) && unfolded.length) {
      unfolded[unfolded.length - 1] += ` ${line.trim()}`;
    } else {
      unfolded.push(line);
    }
  });
  return unfolded.reduce((headers, line) => {
    const index = line.indexOf(":");
    if (index > 0) {
      headers.set(line.slice(0, index).trim().toLowerCase(), line.slice(index + 1).trim());
    }
    return headers;
  }, new Map());
}

function decodeMimeHeader(value) {
  return String(value || "")
    .replace(/=\?utf-8\?b\?([^?]+)\?=/gi, (_, encoded) => {
      try {
        return decodeBase64Utf8(encoded);
      } catch {
        return "";
      }
    })
    .replace(/=\?utf-8\?q\?([^?]+)\?=/gi, (_, encoded) => decodeQuotedPrintable(encoded.replace(/_/g, " ")));
}

function extractEmlBody(bodyText, contentType, transferEncoding) {
  const boundary = boundaryFromContentType(contentType) || inferBoundary(bodyText);
  if (boundary) {
    const parts = splitMimeParts(bodyText, boundary);
    const plainPart = parts.find((part) => part.contentType.includes("text/plain"));
    const htmlPart = parts.find((part) => part.contentType.includes("text/html"));
    const selectedPart = plainPart || htmlPart || parts[0];
    if (selectedPart) {
      const decoded = decodeEmlBody(selectedPart.body, selectedPart.transferEncoding || transferEncoding);
      return selectedPart.contentType.includes("text/html") ? stripHtml(decoded) : decoded;
    }
  }
  return decodeEmlBody(bodyText, transferEncoding);
}

function decodeEmlBody(bodyText, transferEncoding) {
  if (transferEncoding.includes("base64")) {
    try {
      return decodeBase64Utf8(String(bodyText).replace(/\s/g, ""));
    } catch {
      return bodyText;
    }
  }
  if (transferEncoding.includes("quoted-printable")) {
    return decodeQuotedPrintable(bodyText);
  }
  return bodyText;
}

function decodeQuotedPrintable(value) {
  const text = String(value || "")
    .replace(/=\n/g, "")
    .replace(/\r/g, "");
  const bytes = [];
  for (let index = 0; index < text.length; index += 1) {
    if (text[index] === "=" && /^[A-Fa-f0-9]{2}$/.test(text.slice(index + 1, index + 3))) {
      bytes.push(parseInt(text.slice(index + 1, index + 3), 16));
      index += 2;
    } else {
      bytes.push(text.charCodeAt(index));
    }
  }
  return new TextDecoder("utf-8").decode(new Uint8Array(bytes));
}

function boundaryFromContentType(contentType) {
  return contentType.match(/boundary="?([^";\n]+)"?/i)?.[1] || "";
}

function inferBoundary(bodyText) {
  return String(bodyText || "").match(/(?:^|\n)--([^\s-][^\n]*)/)?.[1]?.trim() || "";
}

function splitMimeParts(bodyText, boundary) {
  return String(bodyText || "")
    .split(`--${boundary}`)
    .map((part) => part.trim())
    .filter((part) => part && part !== "--" && !part.startsWith("--"))
    .map((part) => {
      const separatorIndex = part.search(/\n\n/);
      const headerText = separatorIndex >= 0 ? part.slice(0, separatorIndex) : "";
      const body = separatorIndex >= 0 ? part.slice(separatorIndex + 2) : part;
      const headers = parseEmlHeaders(headerText);
      return {
        contentType: (headers.get("content-type") || "").toLowerCase(),
        transferEncoding: (headers.get("content-transfer-encoding") || "").toLowerCase(),
        body,
      };
    });
}

function decodeBase64Utf8(value) {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  return new TextDecoder("utf-8").decode(bytes);
}

function stripHtml(value) {
  const container = document.createElement("div");
  container.innerHTML = value;
  return container.textContent || container.innerText || "";
}

function dollarsToCents(value) {
  const numberValue = Number(value || 0);
  if (!Number.isFinite(numberValue) || numberValue < 0) {
    return 0;
  }
  return Math.round(numberValue * 100);
}

function centsToDollars(value) {
  if (!value) {
    return "";
  }
  return (Number(value) / 100).toFixed(2);
}

function feeAmountDollars(pricing, feeType) {
  const amount = feeItem(pricing, feeType)?.amount;
  if (!amount) {
    return "";
  }
  return (amount / 100).toFixed(2);
}

function feeRequired(pricing, feeType, fallback = false) {
  const item = feeItem(pricing, feeType);
  return item ? Boolean(item.required) : fallback;
}

function feeItem(pricing, feeType) {
  return (pricing?.feeItems || []).find((item) => item.feeType === feeType);
}

function formText(formData, name, fallback = "") {
  if (!formData.has(name)) {
    return String(fallback || "").trim();
  }
  return String(formData.get(name) || "").trim();
}

function formValues(formData, name) {
  return formData.getAll(name).map((value) => String(value).trim()).filter(Boolean);
}

function formValuesIncludingBlank(formData, name) {
  return formData.getAll(name).map((value) => String(value).trim());
}

function formChecked(formData, name) {
  return formData.get(name) === "true";
}

function friendlyOperationError(action, error) {
  if (isPricingOperation(action) && error?.status === 404) {
    return "Class pricing could not be saved. Restart the backend with the latest code, then select the class again and retry.";
  }
  return error instanceof Error ? error.message : "Unable to save";
}

function sitePlaceHiddenFields(site) {
  return `
    <input data-place-field="googlePlaceId" name="googlePlaceId" type="hidden" value="${escapeHtml(site?.googlePlaceId || "")}" />
    <input data-place-field="formattedAddress" name="formattedAddress" type="hidden" value="${escapeHtml(site?.formattedAddress || "")}" />
    <input data-place-field="latitude" name="latitude" type="hidden" value="${escapeHtml(site?.latitude || "")}" />
    <input data-place-field="longitude" name="longitude" type="hidden" value="${escapeHtml(site?.longitude || "")}" />
  `;
}

function initializeClassTypeControls(root) {
  const classTypeSelect = root.querySelector('select[name="classType"]');
  const weekdaysField = root.querySelector('[data-toggle-for="weekly"]');
  if (!classTypeSelect || !weekdaysField) {
    return;
  }

  const syncWeekdayVisibility = () => {
    const weekly = classTypeSelect.value === "weekly";
    weekdaysField.hidden = !weekly;
    weekdaysField.querySelectorAll('input[name="weekdays"]').forEach((input) => {
      input.disabled = !weekly;
    });
  };

  classTypeSelect.addEventListener("change", syncWeekdayVisibility);
  syncWeekdayVisibility();
}

function initializeNotificationAudienceControls(root) {
  const audience = root.querySelector('select[name="audience"]');
  const bccLabel = root.querySelector('textarea[name="bccEmails"]')?.closest("label");
  if (!audience || !bccLabel) {
    return;
  }
  const sync = () => {
    const manual = audience.value === "manual";
    bccLabel.hidden = !manual;
    bccLabel.querySelector("textarea").disabled = !manual;
  };
  audience.addEventListener("change", sync);
  sync();
}

function initializeEmlTemplateUpload(root) {
  const input = root.querySelector("[data-eml-template]");
  if (!input) {
    return;
  }
  input.addEventListener("change", async () => {
    const file = input.files?.[0];
    const form = input.closest("form");
    if (!file || !form) {
      return;
    }
    if (!file.name.toLowerCase().endsWith(".eml")) {
      input.setCustomValidity("Please choose a .eml file.");
      input.reportValidity();
      return;
    }
    input.setCustomValidity("");
    const raw = await file.text();
    const template = parseEmlTemplate(raw);
    if (template.subject) {
      setFormValue(form, "subject", template.subject);
    }
    if (template.ccEmails.length) {
      setFormValue(form, "ccEmails", template.ccEmails.join(", "));
    }
    setFormValue(form, "templateEml", raw);
    setFormValue(form, "body", "Email body is provided by the uploaded .eml template.");
    updateDirtyForm(form);
  });
}

function initializeNotificationWizard(root) {
  const form = root.querySelector("[data-operation-form]");
  const stepInput = form?.querySelector("[data-notification-step-value]");
  if (!form || !stepInput) {
    return;
  }
  const submitButton = form.querySelector('button[type="submit"]');
  const backButton = form.querySelector("[data-wizard-back]");
  const nextButton = form.querySelector("[data-wizard-next]");

  const currentStep = () => Number(stepInput.value || 1);
  const stepSection = (step) => form.querySelector(`[data-wizard-step="${step}"]`);
  const sync = () => {
    const step = currentStep();
    form.querySelectorAll("[data-wizard-step]").forEach((section) => {
      const active = Number(section.dataset.wizardStep) === step;
      section.hidden = !active;
    });
    form.querySelectorAll("[data-wizard-indicator]").forEach((indicator) => {
      indicator.classList.toggle("is-active", Number(indicator.dataset.wizardIndicator) === step);
      indicator.classList.toggle("is-complete", Number(indicator.dataset.wizardIndicator) < step);
    });
    backButton.disabled = step === 1;
    nextButton.hidden = step === 4;
    if (submitButton) {
      submitButton.hidden = step !== 4;
    }
    updateDirtyForm(form);
  };
  const goToStep = (step) => {
    stepInput.value = String(Math.max(1, Math.min(4, step)));
    sync();
  };
  nextButton?.addEventListener("click", () => {
    const section = stepSection(currentStep());
    if (section && !Array.from(section.querySelectorAll("input, textarea, select")).every((field) => field.reportValidity())) {
      return;
    }
    goToStep(currentStep() + 1);
  });
  backButton?.addEventListener("click", () => goToStep(currentStep() - 1));
  sync();
}

function initializeEnrollmentWizard(root) {
  const form = root.querySelector("[data-enrollment-form]");
  const stepInput = form?.querySelector("[data-enrollment-step-value]");
  if (!form || !stepInput) {
    return;
  }
  const submitButton = form.querySelector('button[type="submit"]');
  const backButton = form.querySelector("[data-enrollment-wizard-back]");
  const nextButton = form.querySelector("[data-enrollment-wizard-next]");
  const paymentMethods = form.querySelector("[data-enrollment-payment-methods]");
  const paymentInputs = Array.from(paymentMethods?.querySelectorAll("input") || []);
  const feeInputs = Array.from(form.querySelectorAll("[data-enrollment-fee-checkbox]"));
  const hasRequiredFees = paymentMethods && !paymentMethods.hasAttribute("hidden");
  const reviewOptional = form.querySelector("[data-enrollment-review-optional]");
  const reviewTotal = form.querySelector("[data-enrollment-review-total]");
  const reviewPayment = form.querySelector("[data-enrollment-review-payment]");
  const requiredFeeTotal = Number(form.dataset.requiredFeeTotal || 0);
  const requiredFeeCurrency = form.dataset.requiredFeeCurrency || "USD";

  const currentStep = () => Number(stepInput.value || 1);
  const stepSection = (step) => form.querySelector(`[data-enrollment-wizard-step="${step}"]`);
  const selectedOptionalFees = () => feeInputs
    .filter((input) => input.checked)
    .map((input) => ({
      amount: Number(input.dataset.enrollmentFeeAmount || 0),
      currency: input.dataset.enrollmentFeeCurrency || requiredFeeCurrency,
      name: input.dataset.enrollmentFeeName || "Optional fee",
    }));
  const syncReview = () => {
    const selectedFees = selectedOptionalFees();
    if (reviewOptional) {
      reviewOptional.textContent = selectedFees.length
        ? selectedFees.map((fee) => `${fee.name}: ${formatMoney(fee.amount, fee.currency)}`).join(", ")
        : "None";
    }
    if (reviewTotal) {
      const total = requiredFeeTotal + selectedFees.reduce((sum, fee) => sum + fee.amount, 0);
      reviewTotal.textContent = formatMoney(total, selectedFees[0]?.currency || requiredFeeCurrency);
    }
    if (reviewPayment) {
      const selectedPayment = paymentInputs.find((input) => input.checked);
      reviewPayment.textContent = paymentMethods?.hidden
        ? "No payment selected"
        : selectedPayment?.value === "online"
          ? "Pay online"
          : "Skip payment for now";
    }
  };
  const syncPaymentMethods = () => {
    if (!paymentMethods) {
      return;
    }
    const hasSelectedFee = hasRequiredFees || feeInputs.some((input) => input.checked);
    paymentMethods.hidden = !hasSelectedFee;
    paymentInputs.forEach((input) => {
      input.disabled = !hasSelectedFee;
    });
    if (hasSelectedFee && paymentMethods.dataset.paymentTouched !== "true") {
      const onlineInput = paymentInputs.find((input) => input.value === "online");
      if (onlineInput) {
        onlineInput.checked = true;
      }
    }
    if (!hasSelectedFee) {
      paymentMethods.dataset.paymentTouched = "false";
      const skipInput = paymentInputs.find((input) => input.value === "skip");
      if (skipInput) {
        skipInput.checked = true;
      }
    }
    syncReview();
  };
  const sync = () => {
    const step = currentStep();
    form.querySelectorAll("[data-enrollment-wizard-step]").forEach((section) => {
      section.hidden = Number(section.dataset.enrollmentWizardStep) !== step;
    });
    form.querySelectorAll("[data-enrollment-wizard-indicator]").forEach((indicator) => {
      const indicatorStep = Number(indicator.dataset.enrollmentWizardIndicator);
      indicator.classList.toggle("is-active", indicatorStep === step);
      indicator.classList.toggle("is-complete", indicatorStep < step);
    });
    backButton.disabled = step === 1;
    nextButton.hidden = step === 3;
    if (submitButton) {
      submitButton.hidden = step !== 3;
    }
    syncPaymentMethods();
    syncReview();
  };
  const goToStep = (step) => {
    stepInput.value = String(Math.max(1, Math.min(3, step)));
    sync();
  };
  nextButton?.addEventListener("click", () => {
    const section = stepSection(currentStep());
    if (section && !Array.from(section.querySelectorAll("input, textarea, select")).every((field) => field.reportValidity())) {
      return;
    }
    if (currentStep() === 1 && !validateRequiredCheckboxGroups(section || form)) {
      return;
    }
    goToStep(currentStep() + 1);
  });
  backButton?.addEventListener("click", () => goToStep(currentStep() - 1));
  feeInputs.forEach((input) => input.addEventListener("change", syncPaymentMethods));
  paymentInputs.forEach((input) => input.addEventListener("change", () => {
    if (paymentMethods) {
      paymentMethods.dataset.paymentTouched = "true";
    }
    syncReview();
  }));
  sync();
}

function initializeGooglePlacesAutocomplete(root) {
  const input = root.querySelector("[data-google-address-autocomplete]");
  if (!input || input.dataset.googlePlacesAttached === "true") {
    return;
  }

  loadGooglePlaces()
    .then(() => {
      if (!window.google?.maps?.places?.Autocomplete) {
        return;
      }
      const autocomplete = new window.google.maps.places.Autocomplete(input, {
        componentRestrictions: { country: "us" },
        fields: ["address_components", "formatted_address", "geometry", "place_id"],
        types: ["address"],
      });
      input.dataset.googlePlacesAttached = "true";
      input.addEventListener("input", () => clearSelectedPlace(input.closest("form")));
      autocomplete.addListener("place_changed", () => {
        applySelectedPlace(input.closest("form"), autocomplete.getPlace());
      });
    })
    .catch(() => {
      input.dataset.googlePlacesAttached = "false";
    });
}

function loadGooglePlaces() {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  if (!apiKey) {
    return Promise.resolve();
  }
  if (window.google?.maps?.places) {
    return Promise.resolve();
  }
  if (googlePlacesPromise) {
    return googlePlacesPromise;
  }

  googlePlacesPromise = new Promise((resolve, reject) => {
    const existingScript = document.querySelector("script[data-google-places]");
    if (existingScript) {
      existingScript.addEventListener("load", resolve, { once: true });
      existingScript.addEventListener("error", reject, { once: true });
      return;
    }

    const script = document.createElement("script");
    script.async = true;
    script.defer = true;
    script.dataset.googlePlaces = "true";
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&libraries=places&v=weekly`;
    script.addEventListener("load", resolve, { once: true });
    script.addEventListener("error", reject, { once: true });
    document.head.appendChild(script);
  });
  return googlePlacesPromise;
}

function applySelectedPlace(form, place) {
  if (!form || !place) {
    return;
  }
  const components = addressComponents(place.address_components || []);
  const streetAddress = [components.streetNumber, components.route].filter(Boolean).join(" ");
  setFormValue(form, "streetAddress", streetAddress);
  if (components.subpremise) {
    setFormValue(form, "suite", components.subpremise);
  }
  setFormValue(form, "city", components.city);
  setFormValue(form, "state", components.state);
  setFormValue(form, "zipCode", components.zipCode);
  setFormValue(form, "googlePlaceId", place.place_id || "");
  setFormValue(form, "formattedAddress", place.formatted_address || "");

  const location = place.geometry?.location;
  if (location) {
    setFormValue(form, "latitude", String(location.lat()));
    setFormValue(form, "longitude", String(location.lng()));
  }
}

function clearSelectedPlace(form) {
  if (!form) {
    return;
  }
  setFormValue(form, "googlePlaceId", "");
  setFormValue(form, "formattedAddress", "");
  setFormValue(form, "latitude", "");
  setFormValue(form, "longitude", "");
}

function addressComponents(components) {
  const byType = (type, format = "long_name") =>
    components.find((component) => component.types.includes(type))?.[format] || "";
  return {
    streetNumber: byType("street_number"),
    route: byType("route"),
    subpremise: byType("subpremise"),
    city: byType("locality") || byType("postal_town") || byType("sublocality") || byType("administrative_area_level_3"),
    state: byType("administrative_area_level_1", "short_name"),
    zipCode: byType("postal_code"),
  };
}

function setFormValue(form, name, value) {
  const field = form.elements.namedItem(name);
  if (field && value !== undefined) {
    field.value = value;
    field.dispatchEvent(new Event("input", { bubbles: true }));
  }
}

function timezoneOptions(currentTimezone = "") {
  const fallbackTimezones = [
    "America/New_York",
    "America/Detroit",
    "America/Chicago",
    "America/Denver",
    "America/Los_Angeles",
    "America/Phoenix",
    "America/Anchorage",
    "Pacific/Honolulu",
    "UTC",
    "Europe/London",
    "Europe/Paris",
    "Asia/Shanghai",
    "Asia/Tokyo",
    "Australia/Sydney",
  ];
  const supportedTimezones =
    typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : fallbackTimezones;
  const selectedTimezone = currentTimezone || defaultTimezone();
  return Array.from(new Set([selectedTimezone, ...supportedTimezones, ...fallbackTimezones])).sort();
}

function endpointFor(section, action) {
  const normalized = action.toLowerCase();

  if (normalized.includes("site")) return "/api/tenants/{tenantId}/sites";
  if (normalized.includes("program")) return "/api/tenants/{tenantId}/programs";
  if (normalized.includes("teacher")) return "/api/tenants/{tenantId}/classes/{classId}/teachers";
  if (normalized.includes("pricing")) return "/api/tenants/{tenantId}/classes/{classId}/pricing";
  if (normalized.includes("schedule")) return "/api/tenants/{tenantId}/classes/{classId}/schedules";
  if (normalized.includes("class")) return "/api/tenants/{tenantId}/classes";
  if (normalized.includes("child") || normalized.includes("student")) return "/api/parents/me/children";
  if (normalized.includes("enrollment") || normalized.includes("register")) return "/api/enrollments";
  if (normalized.includes("payment") || normalized.includes("receipt") || normalized.includes("refund") || normalized.includes("stripe")) {
    return "/api/enrollments/{enrollmentId}/payments";
  }
  if (normalized.includes("attendance") || normalized.includes("check in")) return "/api/attendance/check-in";
  if (normalized.includes("notification") || normalized.includes("message") || normalized.includes("provider")) {
    return "/api/tenants/{tenantId}/notifications";
  }

  if (section.id === "sites") return "/api/tenants/{tenantId}/sites";
  if (section.id === "programs") return "/api/tenants/{tenantId}/programs";
  if (section.id === "classes" && normalized.includes("pricing")) return "/api/tenants/{tenantId}/classes/{classId}/pricing";
  if (section.id === "classes") return "/api/tenants/{tenantId}/classes";
  if (section.id === "teachers") return "/api/tenants/{tenantId}/classes/{classId}/teachers";
  if (section.id === "children") return "/api/parents/me/children";
  if (section.id === "enrollments") return "/api/enrollments";
  if (section.id === "payments") return "/api/enrollments/{enrollmentId}/payments";
  if (section.id === "attendance") return "/api/attendance/check-in";
  if (section.id === "notifications") return "/api/tenants/{tenantId}/notifications";
  return "module-specific endpoint";
}
