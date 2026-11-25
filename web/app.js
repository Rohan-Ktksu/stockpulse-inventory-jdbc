const TOKEN_KEY = "stockpulse_token";

const state = {
  token: localStorage.getItem(TOKEN_KEY) || "",
  user: null,
  products: [],
  pendingDeleteId: null,
  searchTimer: null
};

// DOM Elements
const authView = document.getElementById("auth-view");
const dashboardView = document.getElementById("dashboard-view");
const userControls = document.getElementById("user-controls");
const userDisplayName = document.getElementById("user-display-name");
const userDisplayHandle = document.getElementById("user-display-handle");

const dbStatusPill = document.getElementById("db-status-pill");
const dbStatusText = document.getElementById("db-status-text");

const tabLogin = document.getElementById("tab-login");
const tabRegister = document.getElementById("tab-register");
const loginForm = document.getElementById("login-form");
const registerForm = document.getElementById("register-form");
const authAlert = document.getElementById("auth-alert");

const productForm = document.getElementById("product-form");
const formTitle = document.getElementById("form-title");
const formModeBadge = document.getElementById("form-mode-badge");
const saveProductBtn = document.getElementById("save-product-btn");
const cancelEditBtn = document.getElementById("cancel-edit-btn");

const productIdInput = document.getElementById("product-id");
const prodSkuInput = document.getElementById("prod-sku");
const prodCategoryInput = document.getElementById("prod-category");
const prodNameInput = document.getElementById("prod-name");
const prodQuantityInput = document.getElementById("prod-quantity");
const prodPriceInput = document.getElementById("prod-price");
const prodSupplierInput = document.getElementById("prod-supplier");
const prodDescriptionInput = document.getElementById("prod-description");

const searchInput = document.getElementById("search-input");
const filterCategory = document.getElementById("filter-category");
const productsTbody = document.getElementById("products-tbody");
const categoryBreakdown = document.getElementById("category-breakdown");

const kpiTotalProducts = document.getElementById("kpi-total-products");
const kpiTotalUnits = document.getElementById("kpi-total-units");
const kpiTotalValue = document.getElementById("kpi-total-value");
const kpiAlerts = document.getElementById("kpi-alerts");
const kpiAlertsDetail = document.getElementById("kpi-alerts-detail");

const deleteModal = document.getElementById("delete-modal");
const deleteModalMessage = document.getElementById("delete-modal-message");
const cancelDeleteBtn = document.getElementById("cancel-delete-btn");
const confirmDeleteBtn = document.getElementById("confirm-delete-btn");

document.addEventListener("DOMContentLoaded", () => {
  bindEvents();
  checkHealth();
  restoreSession();
});

function bindEvents() {
  tabLogin.addEventListener("click", () => switchAuthTab("login"));
  tabRegister.addEventListener("click", () => switchAuthTab("register"));

  loginForm.addEventListener("submit", handleLogin);
  registerForm.addEventListener("submit", handleRegister);

  document.getElementById("logout-btn").addEventListener("click", handleLogout);
  document.getElementById("refresh-btn").addEventListener("click", () => {
    refreshDashboardData();
    showToast("Data refreshed from MySQL.", "success");
  });

  productForm.addEventListener("submit", handleProductSubmit);
  cancelEditBtn.addEventListener("click", resetProductForm);

  searchInput.addEventListener("input", () => {
    clearTimeout(state.searchTimer);
    state.searchTimer = setTimeout(loadProducts, 220);
  });
  filterCategory.addEventListener("change", loadProducts);

  cancelDeleteBtn.addEventListener("click", closeDeleteModal);
  confirmDeleteBtn.addEventListener("click", confirmDeleteProduct);
  deleteModal.addEventListener("click", (e) => {
    if (e.target === deleteModal) closeDeleteModal();
  });
}

async function apiFetch(url, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.token) {
    headers["Authorization"] = `Bearer ${state.token}`;
  }
  const response = await fetch(url, { ...options, headers });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 && state.token) {
      clearSession();
    }
    throw new Error(data.error || `Request failed (${response.status})`);
  }
  return data;
}

async function checkHealth() {
  try {
    const res = await fetch("/api/health");
    const data = await res.json();
    if (data.databaseConnected) {
      dbStatusPill.className = "status-pill status-online";
      dbStatusText.textContent = "MySQL Connected";
      dbStatusPill.title = `Connected to ${data.target || "MySQL"}`;
    } else {
      dbStatusPill.className = "status-pill status-offline";
      dbStatusText.textContent = "MySQL Disconnected";
      const detail = data.error ? `${data.error} (Target: ${data.target}, User: ${data.user})` : "Database unreachable";
      dbStatusPill.title = detail;
      if (!state.token) {
        showAuthAlert(`MySQL connection issue: ${detail}`, "error");
      }
    }
  } catch {
    dbStatusPill.className = "status-pill status-offline";
    dbStatusText.textContent = "Server Offline";
  }
}

async function restoreSession() {
  if (!state.token) {
    showAuthView();
    return;
  }
  try {
    const data = await apiFetch("/api/me");
    state.user = data.user;
    showDashboardView();
  } catch {
    clearSession();
  }
}

function switchAuthTab(mode) {
  hideAuthAlert();
  if (mode === "login") {
    tabLogin.classList.add("active");
    tabRegister.classList.remove("active");
    loginForm.classList.remove("hidden");
    registerForm.classList.add("hidden");
  } else {
    tabRegister.classList.add("active");
    tabLogin.classList.remove("active");
    registerForm.classList.remove("hidden");
    loginForm.classList.add("hidden");
  }
}

function showAuthAlert(message, type = "error") {
  authAlert.textContent = message;
  authAlert.className = `alert alert-${type}`;
}

function hideAuthAlert() {
  authAlert.className = "alert hidden";
  authAlert.textContent = "";
}

async function handleLogin(e) {
  e.preventDefault();
  hideAuthAlert();
  const username = document.getElementById("login-identifier").value.trim();
  const password = document.getElementById("login-password").value;

  try {
    const data = await apiFetch("/api/login", {
      method: "POST",
      body: JSON.stringify({ username, password })
    });
    state.token = data.token;
    state.user = data.user;
    localStorage.setItem(TOKEN_KEY, state.token);
    loginForm.reset();
    showToast(`Welcome back, ${state.user.fullName}!`, "success");
    showDashboardView();
  } catch (err) {
    showAuthAlert(err.message, "error");
  }
}

async function handleRegister(e) {
  e.preventDefault();
  hideAuthAlert();

  const fullName = document.getElementById("reg-fullname").value.trim();
  const username = document.getElementById("reg-username").value.trim();
  const email = document.getElementById("reg-email").value.trim();
  const password = document.getElementById("reg-password").value;
  const confirm = document.getElementById("reg-confirm").value;

  if (password !== confirm) {
    showAuthAlert("Passwords do not match.", "error");
    return;
  }

  try {
    const data = await apiFetch("/api/register", {
      method: "POST",
      body: JSON.stringify({ fullName, username, email, password })
    });
    state.token = data.token;
    state.user = data.user;
    localStorage.setItem(TOKEN_KEY, state.token);
    registerForm.reset();
    showToast("Account created and signed in!", "success");
    showDashboardView();
  } catch (err) {
    showAuthAlert(err.message, "error");
  }
}

async function handleLogout() {
  try {
    await apiFetch("/api/logout", { method: "POST" });
  } catch {
    // Ignore logout errors and clear local session
  }
  clearSession();
  showToast("Signed out.", "success");
}

function clearSession() {
  state.token = "";
  state.user = null;
  localStorage.removeItem(TOKEN_KEY);
  resetProductForm();
  showAuthView();
}

function showAuthView() {
  authView.classList.remove("hidden");
  dashboardView.classList.add("hidden");
  userControls.classList.add("hidden");
}

function showDashboardView() {
  authView.classList.add("hidden");
  dashboardView.classList.remove("hidden");
  userControls.classList.remove("hidden");
  userDisplayName.textContent = state.user.fullName;
  userDisplayHandle.textContent = `@${state.user.username}`;
  refreshDashboardData();
}

async function refreshDashboardData() {
  await Promise.all([loadDashboardStats(), loadProducts(), checkHealth()]);
}

async function loadDashboardStats() {
  try {
    const stats = await apiFetch("/api/dashboard");
    kpiTotalProducts.textContent = Number(stats.totalProducts || 0).toLocaleString();
    kpiTotalUnits.textContent = Number(stats.totalUnits || 0).toLocaleString();
    kpiTotalValue.textContent = formatCurrency(stats.totalValue || 0);

    const low = Number(stats.lowStockCount || 0);
    const out = Number(stats.outOfStockCount || 0);
    kpiAlerts.textContent = (low + out).toLocaleString();
    kpiAlertsDetail.textContent = `${low} low stock · ${out} out of stock`;

    renderCategoryBreakdown(stats.categories || []);
  } catch (err) {
    showToast(err.message, "error");
  }
}

function renderCategoryBreakdown(categories) {
  if (!categories.length) {
    categoryBreakdown.innerHTML = `<p class="muted-text">No products categorized yet.</p>`;
    return;
  }

  categoryBreakdown.innerHTML = categories
    .map(
      (c) => `
      <div class="category-row">
        <div>
          <strong>${escapeHtml(c.category)}</strong>
          <span class="category-meta">${c.itemCount} items &middot; ${c.unitCount} units</span>
        </div>
        <span class="category-val">${formatCurrency(c.categoryValue)}</span>
      </div>
    `
    )
    .join("");
}

async function loadProducts() {
  const params = new URLSearchParams();
  const q = searchInput.value.trim();
  const category = filterCategory.value;
  if (q) params.set("q", q);
  if (category && category !== "ALL") params.set("category", category);

  const queryStr = params.toString() ? `?${params.toString()}` : "";
  try {
    const data = await apiFetch(`/api/products${queryStr}`);
    state.products = data.products || [];
    renderProductsTable(state.products);
  } catch (err) {
    productsTbody.innerHTML = `<tr><td colspan="9" class="empty-cell">${escapeHtml(err.message)}</td></tr>`;
  }
}

function renderProductsTable(products) {
  if (!products.length) {
    productsTbody.innerHTML = `<tr><td colspan="9" class="empty-cell">No matching products found in MySQL.</td></tr>`;
    return;
  }

  productsTbody.innerHTML = products
    .map((p) => {
      const statusInfo = getStatusBadge(p.status);
      return `
        <tr>
          <td><span class="sku-code">${escapeHtml(p.sku)}</span></td>
          <td>
            <span class="prod-title">${escapeHtml(p.name)}</span>
            ${p.description ? `<span class="prod-desc" title="${escapeHtml(p.description)}">${escapeHtml(p.description)}</span>` : ""}
          </td>
          <td>${escapeHtml(p.category)}</td>
          <td>${escapeHtml(p.supplier)}</td>
          <td class="num">${Number(p.quantity).toLocaleString()}</td>
          <td class="num">${formatCurrency(p.unitPrice)}</td>
          <td class="num"><strong>${formatCurrency(p.totalValue)}</strong></td>
          <td><span class="badge ${statusInfo.cls}">${statusInfo.label}</span></td>
          <td>
            <div class="row-actions">
              <button type="button" class="btn btn-secondary btn-xs" onclick="startEditProduct(${p.id})">Edit</button>
              <button type="button" class="btn btn-outline-danger btn-xs" onclick="openDeleteModal(${p.id})">Delete</button>
            </div>
          </td>
        </tr>
      `;
    })
    .join("");
}

function getStatusBadge(status) {
  if (status === "OUT_OF_STOCK") {
    return { cls: "badge-out-of-stock", label: "Out of Stock" };
  }
  if (status === "LOW_STOCK") {
    return { cls: "badge-low-stock", label: "Low Stock" };
  }
  return { cls: "badge-in-stock", label: "In Stock" };
}

async function handleProductSubmit(e) {
  e.preventDefault();
  const id = productIdInput.value.trim();

  const payload = {
    sku: prodSkuInput.value.trim(),
    name: prodNameInput.value.trim(),
    category: prodCategoryInput.value,
    quantity: Number(prodQuantityInput.value),
    unitPrice: Number(prodPriceInput.value),
    supplier: prodSupplierInput.value.trim(),
    description: prodDescriptionInput.value.trim()
  };

  try {
    if (id) {
      await apiFetch(`/api/products/${id}`, {
        method: "PUT",
        body: JSON.stringify(payload)
      });
      showToast("Product updated in MySQL.", "success");
    } else {
      await apiFetch("/api/products", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      showToast("Product inserted into MySQL.", "success");
    }
    resetProductForm();
    await refreshDashboardData();
  } catch (err) {
    showToast(err.message, "error");
  }
}

window.startEditProduct = function (id) {
  const product = state.products.find((p) => p.id === id);
  if (!product) return;

  productIdInput.value = product.id;
  prodSkuInput.value = product.sku;
  prodNameInput.value = product.name;
  prodCategoryInput.value = product.category;
  prodQuantityInput.value = product.quantity;
  prodPriceInput.value = product.unitPrice;
  prodSupplierInput.value = product.supplier;
  prodDescriptionInput.value = product.description || "";

  formTitle.textContent = `Edit Product (#${product.id})`;
  formModeBadge.textContent = "UPDATE";
  formModeBadge.classList.add("edit-mode");
  saveProductBtn.textContent = "Save Changes";
  cancelEditBtn.classList.remove("hidden");
  prodSkuInput.focus();
};

function resetProductForm() {
  productForm.reset();
  productIdInput.value = "";
  formTitle.textContent = "Add New Product";
  formModeBadge.textContent = "INSERT";
  formModeBadge.classList.remove("edit-mode");
  saveProductBtn.textContent = "Add Product";
  cancelEditBtn.classList.add("hidden");
}

window.openDeleteModal = function (id) {
  const product = state.products.find((p) => p.id === id);
  if (!product) return;
  state.pendingDeleteId = id;
  deleteModalMessage.textContent = `Are you sure you want to permanently delete "${product.name}" (SKU: ${product.sku}) from MySQL?`;
  deleteModal.classList.remove("hidden");
};

function closeDeleteModal() {
  state.pendingDeleteId = null;
  deleteModal.classList.add("hidden");
}

async function confirmDeleteProduct() {
  if (!state.pendingDeleteId) return;
  const id = state.pendingDeleteId;
  try {
    await apiFetch(`/api/products/${id}`, { method: "DELETE" });
    if (String(productIdInput.value) === String(id)) {
      resetProductForm();
    }
    closeDeleteModal();
    showToast("Product deleted from MySQL.", "success");
    await refreshDashboardData();
  } catch (err) {
    closeDeleteModal();
    showToast(err.message, "error");
  }
}

function showToast(message, type = "success") {
  const container = document.getElementById("toast-container");
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 3500);
}

function formatCurrency(value) {
  const num = Number(value) || 0;
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD"
  }).format(num);
}

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
