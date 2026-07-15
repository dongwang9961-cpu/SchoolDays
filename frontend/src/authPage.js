import {
  completePasswordReset,
  completeRegistration,
  getAuthConfig,
  login,
  requestParentRegistrationLink,
  startGoogleAuth,
} from "./api/auth.js";
import { markAuthenticatedSessionStarted } from "./api/client.js";

const REMEMBERED_LOGIN_EMAIL_KEY = "schooldays.rememberedLoginEmail";

export function renderAuthPage({
  brandEyebrow,
  brandTitle,
  brandDescription,
  contextMarkup,
  allowGoogleLogin = false,
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

          ${loginForm({ allowGoogleLogin })}
          ${parentRegisterForm()}
          ${completeRegistrationForm(Boolean(initialToken))}
          ${teacherInvitationForm(Boolean(initialToken))}
          ${schoolInvitationForm(Boolean(initialToken))}
          ${passwordResetForm(Boolean(initialToken))}
        </section>
      </section>
    </main>
  `;

  const modeSelect = root.querySelector("[data-mode-select]");
  const forms = [...root.querySelectorAll("[data-auth-form]")];
  const googleLoginButton = root.querySelector("[data-google-login]");
  const googleRegisterButton = root.querySelector("[data-google-register]");

  root.querySelectorAll('input[name="token"]').forEach((input) => {
    input.value = initialToken;
  });

  showMode(initialMode);

  modeSelect?.addEventListener("change", () => showMode(modeSelect.value));
  googleLoginButton?.addEventListener("click", () => handleGoogleAuth("login", googleLoginButton));
  googleRegisterButton?.addEventListener("click", () => handleGoogleAuth("register", googleRegisterButton));
  configureGoogleAuth();

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
      if (mode === "login") {
        rememberLoginPreference(form, email, password);
      }
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
    if (mode === "reset") {
      return completePasswordReset({
        token: String(formData.get("token") || "").trim(),
        password,
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
    if (mode === "complete") {
      request.address = {
        streetAddress: String(formData.get("streetAddress") || "").trim(),
        suite: String(formData.get("suite") || "").trim(),
        city: String(formData.get("city") || "").trim(),
        state: String(formData.get("state") || "").trim(),
        zipCode: String(formData.get("zipCode") || "").trim(),
      };
    }

    if (mode === "teacher" || mode === "school") {
      return completeRegistration(request);
    }
    return completeRegistration(request);
  }

  async function handleGoogleAuth(mode, button) {
    const form = root.querySelector(`[data-auth-form="${mode}"]`);
    const errorMessage = form.querySelector("[data-error]");
    const successMessage = form.querySelector("[data-success]");

    setMessage(errorMessage, "");
    setMessage(successMessage, "");
    setLoading(button, true, "Opening Google");

    try {
      const response = await startGoogleAuth({ tenantId: requireTenantId() });
      window.location.assign(response.authorizationUrl);
    } catch (error) {
      setMessage(errorMessage, error instanceof Error ? error.message : "Unable to start Google sign-in");
      setLoading(button, false);
    }
  }

  function requireTenantId() {
    if (tenantId) {
      return tenantId;
    }
    throw new Error("Open the school website URL before registering as a parent.");
  }

  async function configureGoogleAuth() {
    if (!googleLoginButton && !googleRegisterButton) {
      return;
    }

    try {
      const config = await getAuthConfig();
      if (config.googleLoginEnabled) {
        if (googleLoginButton) {
          googleLoginButton.disabled = false;
          googleLoginButton.textContent = "Sign in with Google";
        }
        if (googleRegisterButton) {
          googleRegisterButton.disabled = false;
          googleRegisterButton.textContent = "Continue with Google";
        }
        return;
      }
    } catch (error) {
      // Keep email-link registration available if config cannot be loaded.
    }

    root.querySelector("[data-google-login-row]")?.remove();
    root.querySelector("[data-google-login-divider]")?.remove();
    root.querySelector("[data-google-register-row]")?.remove();
    root.querySelector("[data-google-register-divider]")?.remove();
    const hint = root.querySelector("[data-parent-register-hint]");
    if (hint) {
      hint.textContent = "Request a secure email link.";
    }
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

function loginForm({ allowGoogleLogin = false } = {}) {
  return `
    <form class="auth-form" data-auth-form="login">
      <div class="form-heading">
        <h2>Welcome back</h2>
        <p>Use the email address connected to your account.</p>
      </div>

      ${allowGoogleLogin ? `
        <div data-google-login-row>
          <button class="secondary-button google-auth-button" data-google-login disabled type="button">Checking Google</button>
        </div>

        <div class="form-divider" data-google-login-divider><span>or</span></div>
      ` : ""}

      <label>
        <span>Email <span class="required-marker" aria-label="required">*</span></span>
        <input autocomplete="username" maxlength="320" name="email" placeholder="parent@example.com" required type="email" value="${escapeHtml(rememberedLoginEmail())}" />
      </label>

      <label>
        <span>Password <span class="required-marker" aria-label="required">*</span></span>
        <input autocomplete="current-password" maxlength="128" minlength="8" name="password" placeholder="At least 8 characters" required type="password" />
      </label>

      <label class="checkbox-option auth-remember-option">
        <input name="rememberLogin" type="checkbox" ${rememberedLoginEmail() ? "checked" : ""} />
        <span>Remember this login on this device</span>
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
        <p data-parent-register-hint>Continue with Google or request a secure email link.</p>
      </div>

      <div data-google-register-row>
        <button class="secondary-button" data-google-register disabled type="button">Checking Google</button>
      </div>

      <div class="form-divider" data-google-register-divider><span>or</span></div>

      <label>
        <span>Email <span class="required-marker" aria-label="required">*</span></span>
        <input autocomplete="email" maxlength="320" name="email" placeholder="parent@example.com" required type="email" />
      </label>

      <p class="message error" data-error hidden role="alert"></p>
      <p class="message success" data-success hidden role="status"></p>

      <button data-submit type="submit">Send registration link</button>
    </form>
  `;
}

function completeRegistrationForm(hasEmailLinkToken = false) {
  return `
    <form class="auth-form" data-auth-form="complete" hidden>
      <div class="form-heading">
        <h2>Complete registration</h2>
        <p>${hasEmailLinkToken ? "Create your account from the secure email link." : "Use the secure token from your email link to finish your account."}</p>
      </div>

      ${registrationFields("Create account", { hasEmailLinkToken, includeAddress: true })}
    </form>
  `;
}

function teacherInvitationForm(hasEmailLinkToken = false) {
  return `
    <form class="auth-form" data-auth-form="teacher" hidden>
      <div class="form-heading">
        <h2>Accept teacher invitation</h2>
        <p>${hasEmailLinkToken ? "Create your teacher account from the secure invitation link." : "Use the invitation token sent to your teacher email."}</p>
      </div>

      ${registrationFields("Join as teacher", { hasEmailLinkToken })}
    </form>
  `;
}

function schoolInvitationForm(hasEmailLinkToken = false) {
  return `
    <form class="auth-form" data-auth-form="school" hidden>
      <div class="form-heading">
        <h2>Accept school invitation</h2>
        <p>Create the initial school administrator account from the platform invitation.</p>
      </div>

      ${registrationFields("Create school admin", { hasEmailLinkToken })}
    </form>
  `;
}

function passwordResetForm(hasEmailLinkToken = false) {
  return `
    <form class="auth-form" data-auth-form="reset" hidden>
      <div class="form-heading">
        <h2>Reset password</h2>
        <p>${hasEmailLinkToken ? "Create a new password from the secure email link." : "Use the secure token from your password reset email."}</p>
      </div>

      ${hasEmailLinkToken
        ? `<input name="token" required type="hidden" />`
        : `
          <label>
            <span>Token <span class="required-marker" aria-label="required">*</span></span>
            <input maxlength="200" name="token" placeholder="Paste email token" required type="text" />
          </label>
        `}

      <label>
        <span>New password <span class="required-marker" aria-label="required">*</span></span>
        <input autocomplete="new-password" maxlength="128" minlength="8" name="password" required type="password" />
      </label>

      <p class="message error" data-error hidden role="alert"></p>
      <p class="message success" data-success hidden role="status"></p>

      <button data-submit type="submit">Reset password</button>
    </form>
  `;
}

function registrationFields(buttonText, { hasEmailLinkToken = false, includeAddress = false } = {}) {
  return `
    ${hasEmailLinkToken
      ? `<input name="token" required type="hidden" />`
      : `
        <label>
          <span>Token <span class="required-marker" aria-label="required">*</span></span>
          <input maxlength="200" name="token" placeholder="Paste email token" required type="text" />
        </label>
      `}

    <div class="field-grid">
      <label>
        <span>First name <span class="required-marker" aria-label="required">*</span></span>
        <input autocomplete="given-name" maxlength="100" name="firstName" required type="text" />
      </label>

      <label>
        <span>Last name <span class="required-marker" aria-label="required">*</span></span>
        <input autocomplete="family-name" maxlength="100" name="lastName" required type="text" />
      </label>
    </div>

    ${hasEmailLinkToken
      ? ""
      : `
        <label>
          <span>Email</span>
          <input autocomplete="email" maxlength="320" name="email" placeholder="parent@example.com" type="email" />
        </label>
      `}

    <label>
      <span>Phone <span class="required-marker" aria-label="required">*</span></span>
      <input autocomplete="tel" maxlength="50" name="phone" placeholder="555-0100" required type="tel" />
    </label>

    ${includeAddress ? parentAddressFields() : ""}

    <label>
      <span>Password <span class="required-marker" aria-label="required">*</span></span>
      <input autocomplete="new-password" maxlength="128" minlength="8" name="password" required type="password" />
    </label>

    <p class="message error" data-error hidden role="alert"></p>
    <p class="message success" data-success hidden role="status"></p>

    <button data-submit type="submit">${buttonText}</button>
  `;
}

function parentAddressFields() {
  return `
    <div class="form-heading compact-heading">
      <h3>Home address</h3>
      <p>Required for parent accounts.</p>
    </div>

    <label>
      <span>Street address <span class="required-marker" aria-label="required">*</span></span>
      <input autocomplete="address-line1" maxlength="200" name="streetAddress" required type="text" />
    </label>

    <label>
      <span>Suite or apartment</span>
      <input autocomplete="address-line2" maxlength="100" name="suite" type="text" />
    </label>

    <div class="field-grid">
      <label>
        <span>City <span class="required-marker" aria-label="required">*</span></span>
        <input autocomplete="address-level2" maxlength="100" name="city" required type="text" />
      </label>

      <label>
        <span>State <span class="required-marker" aria-label="required">*</span></span>
        <select autocomplete="address-level1" name="state" required>
          ${stateOptions()}
        </select>
      </label>
    </div>

    <label>
      <span>ZIP code <span class="required-marker" aria-label="required">*</span></span>
      <input autocomplete="postal-code" maxlength="20" name="zipCode" required type="text" />
    </label>
  `;
}

function stateOptions() {
  return [
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
    "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
    "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
    "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    "DC"
  ]
    .map((state) => `<option value="${state}"${state === "MI" ? " selected" : ""}>${state}</option>`)
    .join("");
}

function storeAuthResponse(response) {
  localStorage.setItem("schooldays.accessToken", response.accessToken);
  markAuthenticatedSessionStarted();
}

function rememberedLoginEmail() {
  return localStorage.getItem(REMEMBERED_LOGIN_EMAIL_KEY) || "";
}

function rememberLoginPreference(form, email, password) {
  const rememberLogin = Boolean(form.querySelector("input[name='rememberLogin']")?.checked);
  if (!rememberLogin) {
    localStorage.removeItem(REMEMBERED_LOGIN_EMAIL_KEY);
    return;
  }

  localStorage.setItem(REMEMBERED_LOGIN_EMAIL_KEY, email);
  if (window.PasswordCredential && navigator.credentials?.store) {
    const credential = new PasswordCredential({
      id: email,
      name: email,
      password,
    });
    navigator.credentials.store(credential).catch(() => {});
  }
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
  if (mode === "reset") {
    return "Resetting";
  }
  return "Creating account";
}

function successText(mode, user) {
  if (mode === "login") {
    return `Signed in as ${displayName(user)}.`;
  }
  if (mode === "reset") {
    return `Password reset for ${displayName(user)}.`;
  }
  return `Account ready for ${displayName(user)}.`;
}

function registrationLinkSuccessText(response) {
  return `Registration link sent to ${response.email}. Please check your email to finish registration.`;
}
