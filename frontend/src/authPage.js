import {
  acceptTeacherInvitation,
  acceptTenantInvitation,
  completeRegistration,
  login,
  requestParentRegistrationLink,
  startGoogleRegistration,
} from "./api/auth.js";

export function renderAuthPage({
  brandEyebrow,
  brandTitle,
  brandDescription,
  contextMarkup,
  initialMode,
  modes,
  onAuthenticated,
  tenantId,
}) {
  const root = document.querySelector("#root");
  const urlParams = new URLSearchParams(window.location.search);
  const initialToken = urlParams.get("token") || "";

  root.innerHTML = `
    <main class="login-page">
      <section class="login-shell" aria-labelledby="login-title">
        <div class="brand-panel">
          <p class="eyebrow">${escapeHtml(brandEyebrow)}</p>
          <h1 id="login-title">${escapeHtml(brandTitle)}</h1>
          <p>${escapeHtml(brandDescription)}</p>
        </div>

        <section class="auth-panel">
          ${contextMarkup}

          ${modes.length > 1 ? `
            <div class="mode-selector">
              <label>
                <span>Action</span>
                <select data-mode-select aria-label="Authentication action">
                  ${modeOptions(modes)}
                </select>
              </label>
            </div>
          ` : ""}

          ${loginForm()}
          ${parentRegisterForm()}
          ${completeRegistrationForm()}
          ${teacherInvitationForm()}
          ${schoolInvitationForm()}
        </section>
      </section>
    </main>
  `;

  const modeSelect = root.querySelector("[data-mode-select]");
  const forms = [...root.querySelectorAll("[data-auth-form]")];
  const googleRegisterButton = root.querySelector("[data-google-register]");

  root.querySelectorAll('input[name="token"]').forEach((input) => {
    input.value = initialToken;
  });

  showMode(initialMode);

  modeSelect?.addEventListener("change", () => showMode(modeSelect.value));
  googleRegisterButton?.addEventListener("click", handleGoogleRegistration);

  forms.forEach((form) => {
    form.addEventListener("submit", (event) => handleSubmit(event, form));
  });

  function showMode(mode) {
    if (modeSelect) {
      modeSelect.value = mode;
    }
    forms.forEach((form) => {
      form.hidden = form.dataset.authForm !== mode;
    });
  }

  async function handleSubmit(event, form) {
    event.preventDefault();

    const formData = new FormData(form);
    const mode = form.dataset.authForm;
    const email = String(formData.get("email") || "").trim();
    const password = String(formData.get("password") || "");
    const submitButton = form.querySelector("[data-submit]");
    const errorMessage = form.querySelector("[data-error]");
    const successMessage = form.querySelector("[data-success]");

    setMessage(errorMessage, "");
    setMessage(successMessage, "");
    setLoading(submitButton, true, buttonLoadingText(mode));

    try {
      const response = await submitAuthForm(mode, formData, email, password);
      if (mode === "register") {
        setMessage(successMessage, registrationLinkSuccessText(response));
        return;
      }

      storeAuthResponse(response);
      if (typeof onAuthenticated === "function") {
        onAuthenticated(response);
        return;
      }
      setMessage(successMessage, successText(mode, response.user));
    } catch (error) {
      setMessage(errorMessage, error instanceof Error ? error.message : "Unable to sign in");
    } finally {
      setLoading(submitButton, false);
    }
  }

  async function submitAuthForm(mode, formData, email, password) {
    if (mode === "login") {
      return login({ email, password });
    }
    if (mode === "register") {
      return requestParentRegistrationLink({
        tenantId: requireTenantId(),
        email,
      });
    }

    const request = {
      token: String(formData.get("token") || "").trim(),
      email,
      password,
      firstName: String(formData.get("firstName") || "").trim(),
      lastName: String(formData.get("lastName") || "").trim(),
      phone: String(formData.get("phone") || "").trim(),
    };

    if (mode === "teacher") {
      return acceptTeacherInvitation(request);
    }
    if (mode === "school") {
      return acceptTenantInvitation(request);
    }
    return completeRegistration(request);
  }

  async function handleGoogleRegistration() {
    const form = root.querySelector('[data-auth-form="register"]');
    const errorMessage = form.querySelector("[data-error]");
    const successMessage = form.querySelector("[data-success]");

    setMessage(errorMessage, "");
    setMessage(successMessage, "");
    setLoading(googleRegisterButton, true, "Opening Google");

    try {
      const response = await startGoogleRegistration({ tenantId: requireTenantId() });
      window.location.assign(response.authorizationUrl);
    } catch (error) {
      setMessage(errorMessage, error instanceof Error ? error.message : "Unable to start Google registration");
      setLoading(googleRegisterButton, false);
    }
  }

  function requireTenantId() {
    if (tenantId) {
      return tenantId;
    }
    throw new Error("Open the school website URL before registering as a parent.");
  }
}

export function contextNote(html) {
  return `<p class="context-note">${html}</p>`;
}

export function contextError(message) {
  return `<p class="message error" role="alert">${escapeHtml(message)}</p>`;
}

export function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function modeOptions(modes) {
  return modes
    .map((mode) => `<option value="${escapeHtml(mode.value)}">${escapeHtml(mode.label)}</option>`)
    .join("");
}

function loginForm() {
  return `
    <form class="auth-form" data-auth-form="login">
      <div class="form-heading">
        <h2>Welcome back</h2>
        <p>Use the email address connected to your account.</p>
      </div>

      <label>
        <span>Email</span>
        <input autocomplete="email" name="email" placeholder="parent@example.com" required type="email" />
      </label>

      <label>
        <span>Password</span>
        <input autocomplete="current-password" minlength="8" name="password" placeholder="At least 8 characters" required type="password" />
      </label>

      <p class="message error" data-error hidden role="alert"></p>
      <p class="message success" data-success hidden role="status"></p>

      <button data-submit type="submit">Sign in</button>
    </form>
  `;
}

function parentRegisterForm() {
  return `
    <form class="auth-form" data-auth-form="register" hidden>
      <div class="form-heading">
        <h2>Register as a parent</h2>
        <p>Continue with Google or request a secure email link.</p>
      </div>

      <button class="secondary-button" data-google-register type="button">Continue with Google</button>

      <div class="form-divider"><span>or</span></div>

      <label>
        <span>Email</span>
        <input autocomplete="email" name="email" placeholder="parent@example.com" required type="email" />
      </label>

      <p class="message error" data-error hidden role="alert"></p>
      <p class="message success" data-success hidden role="status"></p>

      <button data-submit type="submit">Send registration link</button>
    </form>
  `;
}

function completeRegistrationForm() {
  return `
    <form class="auth-form" data-auth-form="complete" hidden>
      <div class="form-heading">
        <h2>Complete registration</h2>
        <p>Use the secure token from your email link to finish your account.</p>
      </div>

      ${registrationFields("Create account")}
    </form>
  `;
}

function teacherInvitationForm() {
  return `
    <form class="auth-form" data-auth-form="teacher" hidden>
      <div class="form-heading">
        <h2>Accept teacher invitation</h2>
        <p>Use the invitation token sent to your teacher email.</p>
      </div>

      ${registrationFields("Join as teacher")}
    </form>
  `;
}

function schoolInvitationForm() {
  return `
    <form class="auth-form" data-auth-form="school" hidden>
      <div class="form-heading">
        <h2>Accept school invitation</h2>
        <p>Create the initial school administrator account from the platform invitation.</p>
      </div>

      ${registrationFields("Create school admin")}
    </form>
  `;
}

function registrationFields(buttonText) {
  return `
    <label>
      <span>Token</span>
      <input name="token" placeholder="Paste email token" required type="text" />
    </label>

    <div class="field-grid">
      <label>
        <span>First name</span>
        <input autocomplete="given-name" name="firstName" required type="text" />
      </label>

      <label>
        <span>Last name</span>
        <input autocomplete="family-name" name="lastName" required type="text" />
      </label>
    </div>

    <label>
      <span>Email</span>
      <input autocomplete="email" name="email" placeholder="parent@example.com" type="email" />
    </label>

    <label>
      <span>Phone</span>
      <input autocomplete="tel" name="phone" placeholder="555-0100" required type="tel" />
    </label>

    <label>
      <span>Password</span>
      <input autocomplete="new-password" minlength="8" name="password" required type="password" />
    </label>

    <p class="message error" data-error hidden role="alert"></p>
    <p class="message success" data-success hidden role="status"></p>

    <button data-submit type="submit">${buttonText}</button>
  `;
}

function storeAuthResponse(response) {
  localStorage.setItem("schooldays.accessToken", response.accessToken);
}

function setLoading(submitButton, isLoading, loadingText = "Working") {
  submitButton.disabled = isLoading;
  if (!submitButton.dataset.idleText) {
    submitButton.dataset.idleText = submitButton.textContent;
  }
  submitButton.textContent = isLoading ? loadingText : submitButton.dataset.idleText;
}

function setMessage(element, message) {
  element.textContent = message;
  element.hidden = message.length === 0;
}

function displayName(user) {
  const name = [user.firstName, user.lastName].filter(Boolean).join(" ").trim();
  return name || user.email;
}

function buttonLoadingText(mode) {
  if (mode === "login") {
    return "Signing in";
  }
  if (mode === "register") {
    return "Sending link";
  }
  if (mode === "teacher") {
    return "Joining";
  }
  if (mode === "school") {
    return "Creating";
  }
  return "Creating account";
}

function successText(mode, user) {
  if (mode === "login") {
    return `Signed in as ${displayName(user)}.`;
  }
  return `Account ready for ${displayName(user)}.`;
}

function registrationLinkSuccessText(response) {
  if (response.link) {
    return `Registration link created for ${response.email}. Open ${response.link} to finish registration.`;
  }
  return `Registration link sent to ${response.email}.`;
}
