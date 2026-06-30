import { escapeHtml } from "./authPage.js";
import { changePassword, getProfile, updateProfile } from "./api/account.js";
import { createClass, listClasses, updateClass } from "./api/classes.js";
import { getTenantClassPricing, saveClassPricing } from "./api/pricing.js";
import { createProgram, listPrograms, updateProgram } from "./api/programs.js";
import { createSite, listSites, updateSite } from "./api/sites.js";
import {
  listNotificationHistory,
  listNotificationProviders,
  sendNotification,
  startGmailConnection,
} from "./api/notifications.js";

let googlePlacesPromise;

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
    summary: "Review class attendance and check in students when needed.",
    actions: ["Open class attendance", "Check in student"],
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
    summary: "Review children, enrollments, payments, and check-in status.",
    actions: ["Add child", "Browse classes"],
    rows: ["No family dashboard data loaded yet."],
  },
  {
    id: "children",
    label: "Children",
    title: "Children",
    summary: "Manage child profiles for this school.",
    actions: ["Add child", "Edit child"],
    rows: ["No child records loaded yet."],
  },
  {
    id: "classes",
    label: "Classes",
    title: "Available Classes",
    summary: "Browse public classes and choose available dates.",
    actions: ["Browse classes", "View available dates"],
    rows: ["No class catalog loaded yet."],
  },
  {
    id: "enrollments",
    label: "Enrollments",
    title: "Enrollments",
    summary: "Track pending and active class registrations.",
    actions: ["Register child", "View enrollment"],
    rows: ["No enrollment records loaded yet."],
  },
  {
    id: "payments",
    label: "Payments",
    title: "Payments",
    summary: "Pay required fees, upload receipts, and review payment history.",
    actions: ["Pay with Stripe", "Upload receipt", "View payments"],
    rows: ["No payment records loaded yet."],
  },
  {
    id: "attendance",
    label: "Attendance",
    title: "Attendance",
    summary: "Check in children and review attendance history.",
    actions: ["Check in child", "View attendance"],
    rows: ["No attendance records loaded yet."],
  },
  {
    id: "notifications",
    label: "Notifications",
    title: "Notifications",
    summary: "Review email messages sent by the school.",
    actions: ["View message history"],
    rows: ["No notification history loaded yet."],
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
  let adminMode = role === "SCHOOL_ADMIN" ? "" : "portal";
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
  let notificationProviders = [];
  let notificationHistory = [];
  let loadingNotifications = false;
  let profileOpen = false;
  let profile = null;
  let loadingProfile = false;
  let noticeTimer = null;

  if (role === "SCHOOL_ADMIN") {
    loadSites();
  }

  window.addEventListener("message", (event) => {
    if (event.data?.type === "schooldays:gmail-connected") {
      notice = "Gmail connected.";
      error = "";
      loadNotifications();
    }
  });

  render();

  function render() {
    scheduleNoticeDismissal();
    if (role === "SCHOOL_ADMIN" && !adminMode) {
      renderSchoolAdminLanding();
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

    root.innerHTML = `
      <main class="app-shell">
        <aside class="app-sidebar">
          <div class="app-brand">
            <p class="eyebrow">${escapeHtml(school.name)}</p>
            <h1>${escapeHtml(title)}</h1>
          </div>

          ${role === "SCHOOL_ADMIN" ? adminModeSwitcher() : ""}

          <nav class="app-nav" aria-label="${escapeHtml(dashboard.navLabel)}">
            ${sections
              .map(
                (section) => `
                  <button
                    class="${section.id === activeSection.id ? "is-active" : ""}"
                    data-section-id="${escapeHtml(section.id)}"
                    type="button"
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
            ${profileMenu(user)}
          </header>

          ${toolbarFor(activeSection)}

          <section class="workspace-panel">
            <div class="workspace-heading workspace-heading-row">
              <div>
                <h3>${escapeHtml(activeSection.title)} workspace</h3>
                <p>${escapeHtml(workspaceHint(activeSection))}</p>
                ${activeSection.id === "sites" && siteQuota ? `<p class="context-note">${escapeHtml(siteQuotaText(siteQuota))}</p>` : ""}
              </div>
              ${panelHeaderAction(activeSection)}
            </div>

            ${error ? `<p class="message error" role="alert">${escapeHtml(error)}</p>` : ""}
            ${activeOperation ? operationPanel(activeSection, activeOperation, selectedSite(), selectedProgram(), selectedClass(), selectedClassPricing, user, sites, programs, classes, loadingClassPricing) : ""}

            ${dataList(activeSection, rows)}
          </section>
          ${profileOpen ? profilePanel(profile, user, loadingProfile) : ""}
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
      });
    });

    root.querySelector("[data-logout]").addEventListener("click", onLogout);
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
    root.querySelectorAll("[data-admin-mode]").forEach((button) => {
      button.addEventListener("click", () => {
        adminMode = button.dataset.adminMode;
        activeSectionId = adminMode === "site" ? "programs" : "sites";
        activeOperation = "";
        notice = "";
        error = "";
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
    root.querySelectorAll("[data-operation-action]").forEach((button) => {
      button.addEventListener("click", () => {
        if (isLogOnSiteAction(button.dataset.operationAction)) {
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
        if (isProgramOperation(button.dataset.operationAction) && role === "SCHOOL_ADMIN" && adminMode === "site" && !selectedSite()) {
          notice = "";
          error = "Log on to a site before managing programs.";
          activeOperation = "";
          render();
          return;
        }
        if (isEditProgramOperation(button.dataset.operationAction) && !selectedProgram()) {
          notice = "";
          error = "Select a program before editing.";
          activeOperation = "";
          render();
          return;
        }
        if (isClassOperation(button.dataset.operationAction) && role === "SCHOOL_ADMIN" && adminMode === "site" && !selectedSite()) {
          notice = "";
          error = "Log on to a site before managing classes.";
          activeOperation = "";
          render();
          return;
        }
        if (isCreateClassOperation(button.dataset.operationAction) && !programs.length) {
          notice = "";
          error = "Create a program before adding classes.";
          activeOperation = "";
          render();
          return;
        }
        if (isEditClassOperation(button.dataset.operationAction) && !selectedClass()) {
          notice = "";
          error = "Select a class before editing.";
          activeOperation = "";
          render();
          return;
        }
        if (isPricingOperation(button.dataset.operationAction) && !selectedClass()) {
          notice = "";
          error = "Select a class before configuring pricing.";
          activeOperation = "";
          render();
          return;
        }
        if (isEditSiteOperation(button.dataset.operationAction) && !selectedSite()) {
          notice = "";
          error = "Select a site before editing.";
          activeOperation = "";
          render();
          return;
        }
        activeOperation = button.dataset.operationAction;
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
        notice = "";
        error = "";
        render();
        if (isPricingOperation(activeOperation)) {
          loadSelectedClassPricing();
        }
      });
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
    root.querySelectorAll("[data-class-public-link-id]").forEach((button) => {
      button.addEventListener("click", async () => {
        selectedClassId = button.dataset.classPublicLinkId;
        await copyPublicClassLink(button.dataset.classPublicLinkId);
      });
    });

    initializeGooglePlacesAutocomplete(root);
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
          </div>
          ${profileOpen ? profilePanel(profile, user, loadingProfile) : ""}
          ${noticeToast(notice)}
        </section>
      </main>
    `;
    root.querySelector("[data-logout]").addEventListener("click", onLogout);
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
    root.querySelector("[data-logout]").addEventListener("click", onLogout);
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
                  <div class="row-actions">
                    <button
                      aria-label="Edit ${escapeHtml(classRecord.name)}"
                      class="icon-button subtle-icon-button"
                      data-class-edit-id="${escapeHtml(classRecord.id)}"
                      title="Edit class"
                      type="button"
                    >
                      ✎
                    </button>
                    <button
                      aria-label="Configure pricing for ${escapeHtml(classRecord.name)}"
                      class="icon-button subtle-icon-button"
                      data-class-pricing-id="${escapeHtml(classRecord.id)}"
                      title="Configure pricing"
                      type="button"
                    >
                      $
                    </button>
                    <button
                      aria-label="Copy public link for ${escapeHtml(classRecord.name)}"
                      class="icon-button subtle-icon-button"
                      data-class-public-link-id="${escapeHtml(classRecord.id)}"
                      title="Copy public link"
                      type="button"
                    >
                      ↗
                    </button>
                  </div>
                </div>
              `
            )
            .join("")}
        </div>
      `;
    }
    if (section.id === "notifications") {
      return notificationList(rows);
    }
    return `
      <div class="data-list">
        ${rows.map((row) => `<div class="data-row">${escapeHtml(row)}</div>`).join("")}
      </div>
    `;
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

  function classScheduleText(classRecord) {
    const time = [classRecord.startTime, classRecord.endTime].filter(Boolean).join("-");
    if (classRecord.classType === "weekly") {
      const days = (classRecord.weekdays || []).join(", ");
      return [days, time].filter(Boolean).join(" ");
    }
    return time || "Time range";
  }

  function resetSiteWorkspaceData() {
    resetPrograms();
    resetClasses();
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
    if (loadingClasses || !school?.tenantId || !site) {
      return;
    }
    loadingClasses = true;
    try {
      const response = await listClasses(school.tenantId, site.id);
      classes = response.classes || [];
      if (selectedClassId && !classes.some((classRecord) => classRecord.id === selectedClassId)) {
        selectedClassId = "";
      }
      classRows = classes.length
        ? classes.map((classRecord) => `${classRecord.name} - ${programName(classRecord.programId)} - ${classScheduleText(classRecord)}`)
        : ["No classes have been created for this site yet."];
    } catch (loadError) {
      classRows = ["Classes could not be loaded."];
      error = loadError instanceof Error ? loadError.message : "Classes could not be loaded.";
    } finally {
      loadingClasses = false;
      render();
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
      </div>
    `;
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

function profileMenu(user) {
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
        <button data-profile-action="profile" type="button">My profile</button>
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

function serializeForm(form) {
  return JSON.stringify(Array.from(new FormData(form).entries()));
}

function workspaceHint(section) {
  if (section.id === "overview") {
    return "Choose an operation to start a workflow.";
  }
  if (section.id === "sites") {
    return "Manage sites and enter a site workspace from its row.";
  }
  if (section.id === "programs") {
    return "Manage the programs available at this site.";
  }
  if (section.id === "classes") {
    return "Manage classes, pricing, and public links from each row.";
  }
  return "Choose an operation above to open the working panel for this area.";
}

function toolbarFor(section) {
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

function panelHeaderAction(section) {
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
  selectedPricing,
  user,
  sites = [],
  programs = [],
  classes = [],
  loadingPricing = false
) {
  const fields = fieldsFor(action, selectedSite, selectedProgram, selectedClass, selectedPricing, user, sites, programs);
  const siteOperation = isSiteOperation(action);
  const pricingOperation = isPricingOperation(action);
  const notificationOperation = isNotificationOperation(action);
  return `
    <form class="operation-panel" data-dirty-form data-operation-form>
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

function isNotificationOperation(action) {
  const normalized = action.toLowerCase();
  return normalized.includes("notification") || normalized.includes("message") || normalized === "free send";
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
      <fieldset class="checkbox-field" ${field.toggleFor ? `data-toggle-for="${escapeHtml(field.toggleFor)}"` : ""}>
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
          .map(
            (option) => `
              <label class="checkbox-option">
                <input
                  ${selected.has(option) ? "checked" : ""}
                  name="${escapeHtml(field.name)}"
                  type="checkbox"
                  value="${escapeHtml(option)}"
                />
                <span>${escapeHtml(option)}</span>
              </label>
            `
          )
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
  return String(typeof option === "object" ? option.value : option);
}

function optionLabel(option) {
  return String(typeof option === "object" ? option.label : option);
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
      { name: "email", label: "Teacher email", type: "email", placeholder: "teacher@example.com" },
      { name: "className", label: "Class", placeholder: "Beginner Drawing" },
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
    return [
      { name: "firstName", label: "First name", placeholder: "First name" },
      { name: "lastName", label: "Last name", placeholder: "Last name" },
      { name: "dateOfBirth", label: "Date of birth", type: "date" },
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
