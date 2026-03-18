(function () {
  const result = document.getElementById("result");
  const statusLine = document.getElementById("statusLine");
  const dashboardClassBoard = document.getElementById("dashboardClassBoard");
  const queryYearInput = document.getElementById("queryYear");
  const poolYearFilter = document.getElementById("poolYearFilter");
  const auditResult = document.getElementById("auditResult");
  const teacherPool = document.getElementById("teacherPool");
  const studentPool = document.getElementById("studentPool");
  const teacherPageInfo = document.getElementById("teacherPageInfo");
  const studentPageInfo = document.getElementById("studentPageInfo");
  const tabButtons = Array.from(document.querySelectorAll(".tab-btn"));
  const tabPanels = Array.from(document.querySelectorAll(".tab-panel"));
  const yearClassTabButtons = Array.from(document.querySelectorAll(".subtab-btn"));
  const yearClassPanels = Array.from(document.querySelectorAll(".yearclass-panel"));
  const poolTabButtons = Array.from(document.querySelectorAll("[data-pool-tab-target]"));
  const poolPanels = Array.from(document.querySelectorAll(".pool-panel"));
  const assignmentTabButtons = Array.from(document.querySelectorAll("[data-assignment-tab-target]"));
  const assignmentPanels = Array.from(document.querySelectorAll(".assignment-panel"));
  const adminProtected = document.getElementById("adminProtected");
  const adminAuthSection = document.getElementById("adminAuthSection");
  const adminLoginMessage = document.getElementById("adminLoginMessage");
  const btnAdminLogout = document.getElementById("btnAdminLogout");
  const TEACHER_TOKEN_KEY = "qt_teacher_access_token";
  const ADMIN_SESSION_TOKEN_KEY = "qt_admin_session_token";
  let adminToken = "";

  let cachedYearClasses = [];
  let cachedYears = [];
  let dashboardYearValue = null;
  let assignmentTeacherItems = [];
  let assignmentStudentItems = [];
  let teacherPoolItems = [];
  let studentPoolItems = [];
  let teacherTotalCount = 0;
  let studentTotalCount = 0;
  let teacherPage = 1;
  let studentPage = 1;
  const selectedTeacherIds = new Set();
  const selectedStudentIds = new Set();
  const selectedAssignmentTeacherIds = new Set();
  const selectedAssignmentStudentIds = new Set();
  let lastUndoAction = null;
  const bootstrapUiState = {
    mode: "전체",
    contract: "-",
    loaded: "없음",
    request: "-"
  };
  const yearEditorState = {
    mode: "create",
    yearId: null
  };
  const classEditorState = {
    mode: "create",
    yearClassId: null
  };
  const teacherEditorState = {
    mode: "create",
    teacherId: null
  };
  const studentEditorState = {
    mode: "create",
    studentId: null
  };

  const savedAuditActionType = localStorage.getItem("qt_admin_audit_action_type");
  const savedAuditKeyword = localStorage.getItem("qt_admin_audit_keyword");
  const savedAuditFromAt = localStorage.getItem("qt_admin_audit_from_at");
  const savedAuditToAt = localStorage.getItem("qt_admin_audit_to_at");
  const savedBootstrapIncludeYearClasses = localStorage.getItem("qt_admin_bootstrap_include_year_classes");
  const savedBootstrapIncludeActionTypes = localStorage.getItem("qt_admin_bootstrap_include_action_types");
  const savedBootstrapIncludePools = localStorage.getItem("qt_admin_bootstrap_include_pools");
  const savedBootstrapIncludeAuditLogs = localStorage.getItem("qt_admin_bootstrap_include_audit_logs");
  const savedActiveTab = localStorage.getItem("qt_admin_active_tab") || "dashboard";
  const savedYearClassTab = localStorage.getItem("qt_admin_yearclass_tab") || "years";
  const savedPoolTab = localStorage.getItem("qt_admin_pool_tab") || "teachers";
  if (savedAuditActionType) document.getElementById("auditActionType").value = savedAuditActionType;
  if (savedAuditKeyword) document.getElementById("auditKeyword").value = savedAuditKeyword;
  if (savedAuditFromAt) document.getElementById("auditFromAt").value = savedAuditFromAt;
  if (savedAuditToAt) document.getElementById("auditToAt").value = savedAuditToAt;

  function readAdminToken() {
    const teacherToken = String(localStorage.getItem(TEACHER_TOKEN_KEY) || "").trim();
    if (teacherToken) {
      sessionStorage.setItem(ADMIN_SESSION_TOKEN_KEY, teacherToken);
      return teacherToken;
    }
    return String(sessionStorage.getItem(ADMIN_SESSION_TOKEN_KEY) || "").trim();
  }

  function persistAdminToken(token) {
    const normalizedToken = String(token || "").trim();
    adminToken = normalizedToken;
    if (!normalizedToken) {
      sessionStorage.removeItem(ADMIN_SESSION_TOKEN_KEY);
      localStorage.removeItem(TEACHER_TOKEN_KEY);
      return;
    }
    sessionStorage.setItem(ADMIN_SESSION_TOKEN_KEY, normalizedToken);
    localStorage.setItem(TEACHER_TOKEN_KEY, normalizedToken);
  }

  function isAuthorizationMessage(message) {
    const text = String(message || "").trim();
    return text.includes("Authorization")
      || text.includes("인증")
      || text.includes("권한")
      || text.includes("토큰")
      || text.includes("로그인");
  }

  function isAuthorizationFailure(status, message) {
    return status === 401 || status === 403 || isAuthorizationMessage(message);
  }

  adminToken = readAdminToken();

  function setAdminAuthenticated(authenticated, teacherName) {
    adminProtected.classList.toggle("hidden", !authenticated);
    adminAuthSection.classList.toggle("hidden", authenticated);
    if (btnAdminLogout) {
      btnAdminLogout.classList.toggle("hidden", !authenticated);
    }
  }

  function refreshVisibleTabData() {
    const activeTab = localStorage.getItem("qt_admin_active_tab") || "dashboard";
    if (!adminToken) return;
    if (activeTab === "pool") {
      const activePoolTab = localStorage.getItem("qt_admin_pool_tab") || "teachers";
      setPoolTab(activePoolTab);
      return;
    }
    if (activeTab === "assignment") {
      if (!assignmentTeacherItems.length || !assignmentStudentItems.length) {
        loadAssignmentPools().catch(function (e) {
          render(e.message);
        });
      } else {
        renderAssignmentManagement();
      }
    }
  }

  function setActiveTab(tabName) {
    const safeTab = tabPanels.some(panel => panel.dataset.tabPanel === tabName) ? tabName : "dashboard";
    tabButtons.forEach(button => {
      const active = button.dataset.tabTarget === safeTab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });
    tabPanels.forEach(panel => {
      panel.classList.toggle("active", panel.dataset.tabPanel === safeTab);
    });
    localStorage.setItem("qt_admin_active_tab", safeTab);
    if (safeTab === "assignment" && adminToken) {
      setAssignmentTab("teachers");
      if (!assignmentTeacherItems.length || !assignmentStudentItems.length) {
        loadAssignmentPools().catch(function (e) {
          render(e.message);
        });
      } else {
        renderAssignmentManagement();
      }
    }
    if (safeTab === "yearclass") {
      setYearClassTab("years");
    }
    if (safeTab === "pool" && adminToken) {
      setPoolTab("teachers");
    } else if (safeTab === "pool") {
      setPoolTab("teachers");
    }
    if (safeTab === "audit" && adminToken) {
      const params = buildAuditParams(100);
      api("/api/admin/audit-logs?" + params.toString(), { method: "GET" })
        .then(function (body) {
          renderAuditLogsResponse(body);
        })
        .catch(function (e) {
          auditResult.innerHTML = `<div class="table-empty">${escapeHtml(e.message || "운영 로그 조회에 실패했습니다.")}</div>`;
          render(e.message);
        });
    }
  }

  function setYearClassTab(tabName) {
    const safeTab = yearClassPanels.some(panel => panel.dataset.yearclassPanel === tabName) ? tabName : "years";
    yearClassTabButtons.forEach(button => {
      const active = button.dataset.yearclassTabTarget === safeTab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });
    yearClassPanels.forEach(panel => {
      panel.classList.toggle("active", panel.dataset.yearclassPanel === safeTab);
    });
    localStorage.setItem("qt_admin_yearclass_tab", safeTab);
  }

  function setPoolTab(tabName) {
    const safeTab = poolPanels.some(panel => panel.dataset.poolPanel === tabName) ? tabName : "teachers";
    poolTabButtons.forEach(button => {
      const active = button.dataset.poolTabTarget === safeTab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });
    poolPanels.forEach(panel => {
      panel.classList.toggle("active", panel.dataset.poolPanel === safeTab);
    });
    localStorage.setItem("qt_admin_pool_tab", safeTab);
    if (adminToken) {
      if (safeTab === "teachers") {
        loadTeacherPool(teacherPage).catch(function (e) {
          render(e.message);
        });
      } else {
        loadStudentPool(studentPage).catch(function (e) {
          render(e.message);
        });
      }
    }
  }

  function setAssignmentTab(tabName) {
    const safeTab = assignmentPanels.some(panel => panel.dataset.assignmentPanel === tabName) ? tabName : "teachers";
    assignmentTabButtons.forEach(button => {
      const active = button.dataset.assignmentTabTarget === safeTab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });
    assignmentPanels.forEach(panel => {
      panel.classList.toggle("active", panel.dataset.assignmentPanel === safeTab);
    });
    localStorage.setItem("qt_admin_assignment_tab", safeTab);
  }

  tabButtons.forEach(button => {
    button.addEventListener("click", function () {
      setActiveTab(button.dataset.tabTarget);
    });
  });

  yearClassTabButtons.forEach(button => {
    button.addEventListener("click", function () {
      if (button.dataset.yearclassTabTarget) {
        setYearClassTab(button.dataset.yearclassTabTarget);
      }
    });
  });

  poolTabButtons.forEach(button => {
    button.addEventListener("click", function () {
      setPoolTab(button.dataset.poolTabTarget);
    });
  });

  assignmentTabButtons.forEach(button => {
    button.addEventListener("click", function () {
      setAssignmentTab(button.dataset.assignmentTabTarget);
    });
  });

  setActiveTab(savedActiveTab);
  setYearClassTab(savedYearClassTab);
  setPoolTab(savedPoolTab);
  setAssignmentTab("teachers");
  setAdminAuthenticated(false);

  function render(data) {
    if (!result) return;
    result.textContent = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  }

  function setStatus(message) {
    statusLine.textContent = message;
  }

  function setAdminLoginMessage(message) {
    if (!adminLoginMessage) {
      return;
    }
    const text = String(message || "").trim();
    adminLoginMessage.textContent = text;
    adminLoginMessage.classList.toggle("hidden", !text);
  }

  function extractErrorMessage(value, fallback) {
    if (value == null) {
      return fallback || "요청 처리 중 오류가 발생했습니다.";
    }
    if (typeof value === "string") {
      const trimmed = value.trim();
      if (!trimmed) {
        return fallback || "요청 처리 중 오류가 발생했습니다.";
      }
      try {
        return extractErrorMessage(JSON.parse(trimmed), fallback);
      } catch (_) {
        return trimmed;
      }
    }
    if (typeof value === "object") {
      if (typeof value.message === "string" && value.message.trim()) {
        return value.message.trim();
      }
      if (typeof value.error === "string" && value.error.trim()) {
        return value.error.trim();
      }
    }
    return fallback || "요청 처리 중 오류가 발생했습니다.";
  }

  function setUndoAction(action) {
    lastUndoAction = action;
    const undoMessage = action ? `되돌리기 가능: ${action.label}` : "되돌릴 작업 없음";
    const undoHint = document.getElementById("undoHint");
    if (undoHint) {
      undoHint.textContent = undoMessage;
    }
  }

  function resetUiState() {
    cachedYears = [];
    cachedYearClasses = [];
    dashboardYearValue = null;
    assignmentTeacherItems = [];
    assignmentStudentItems = [];
    teacherPoolItems = [];
    studentPoolItems = [];
    teacherTotalCount = 0;
    studentTotalCount = 0;
    teacherPage = 1;
    studentPage = 1;
    selectedTeacherIds.clear();
    selectedStudentIds.clear();
    selectedAssignmentTeacherIds.clear();
    selectedAssignmentStudentIds.clear();
    setUndoAction(null);
    teacherPool.innerHTML = "";
    studentPool.innerHTML = "";
    teacherPageInfo.textContent = "1 / 1";
    studentPageInfo.textContent = "1 / 1";
    auditResult.innerHTML = "";
    bootstrapUiState.loaded = "없음";
    bootstrapUiState.contract = "-";
    bootstrapUiState.request = "-";
    updateBootstrapSummaryHint();
    renderYearManagement();
    renderClassManagement();
    renderAssignmentManagement();
  }

  function clearTokenAndReset(message) {
    persistAdminToken("");
    resetUiState();
    setAdminAuthenticated(false);
    setAdminLoginMessage(message || "");
    setActiveTab("dashboard");
    setStatus(message ? "토큰 만료/무효" : "로그아웃됨");
    render(message || "로그아웃되었습니다.");
  }

  function handleBootstrapFailure(prefix, error) {
    const message = (prefix ? prefix + ": " : "") + (error && error.message ? error.message : String(error));
    if (message.includes("유효하지 않은 인증 토큰입니다.")) {
      clearTokenAndReset(message);
      return;
    }
    render(message);
  }

  function bindBootstrapToggle(id, storageKey) {
    const el = document.getElementById(id);
    el.addEventListener("change", function () {
      localStorage.setItem(storageKey, String(el.checked));
      updateBootstrapModeHint();
      if (adminToken) {
        loadBootstrapAndDefaultYear().catch(function (e) {
          handleBootstrapFailure("include 옵션 적용 실패", e);
        });
      }
    });
  }

  function updateBootstrapModeHint() {
    const includeYearClasses = true;
    const includeActionTypes = true;
    const includePools = false;
    const includeAuditLogs = false;
    const isFull = includeYearClasses && includeActionTypes && includePools && includeAuditLogs;
    const isLite = includeYearClasses && includeActionTypes && !includePools && !includeAuditLogs;
    const mode = isFull ? "전체" : (isLite ? "경량" : "커스텀");
    bootstrapUiState.mode = mode;
    updateBootstrapSummaryHint();
  }

  function updateBootstrapLoadedHint(body) {
    const yearClasses = Array.isArray(body && body.yearClasses) ? body.yearClasses.length : 0;
    const teacherItems = body && body.teachers && Array.isArray(body.teachers.items) ? body.teachers.items.length : 0;
    const studentItems = body && body.students && Array.isArray(body.students.items) ? body.students.items.length : 0;
    const auditItems = body && body.auditLogs && Array.isArray(body.auditLogs.items) ? body.auditLogs.items.length : 0;
    const now = new Date();
    const stamp = now.toLocaleTimeString();
    bootstrapUiState.loaded = `${stamp} (반 ${yearClasses}, 교사 ${teacherItems}, 학생 ${studentItems}, 로그 ${auditItems})`;
    updateBootstrapSummaryHint();
  }

  function updateBootstrapContractHint(body) {
    const schemaVersion = body && body.schemaVersion ? String(body.schemaVersion) : "-";
    const generatedAt = body && body.generatedAt ? String(body.generatedAt) : "-";
    bootstrapUiState.contract = `${schemaVersion} / ${generatedAt}`;
    updateBootstrapSummaryHint();
  }

  function updateBootstrapRequestHint(path) {
    bootstrapUiState.request = path || "-";
    updateBootstrapSummaryHint();
  }

  function updateBootstrapSummaryHint() {
    const summaryText =
      `bootstrap 요약: 모드=${bootstrapUiState.mode} / 계약=${bootstrapUiState.contract} / 최근 로드=${bootstrapUiState.loaded} / 요청=${bootstrapUiState.request}`;
    const summaryEl = document.getElementById("bootstrapSummaryHint");
    if (summaryEl) {
      summaryEl.textContent = summaryText;
    }
  }

  function applyBootstrapIncludePreset(preset) {
    updateBootstrapModeHint();
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function formatDateTime(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  function formatContactNumber(value) {
    const digits = String(value || "").replace(/[^0-9]/g, "");
    if (!digits) return "-";
    if (digits.length === 11) {
      return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
    }
    if (digits.length === 10) {
      if (digits.startsWith("02")) {
        return `${digits.slice(0, 2)}-${digits.slice(2, 6)}-${digits.slice(6)}`;
      }
      return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
    }
    if (digits.length === 9 && digits.startsWith("02")) {
      return `${digits.slice(0, 2)}-${digits.slice(2, 5)}-${digits.slice(5)}`;
    }
    return digits;
  }

  function formatBirthDate(value) {
    const digits = String(value || "").replace(/[^0-9]/g, "");
    if (!digits) return "-";
    if (digits.length === 8) {
      return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6)}`;
    }
    if (digits.length === 6) {
      return `${digits.slice(0, 2)}-${digits.slice(2, 4)}-${digits.slice(4)}`;
    }
    return digits;
  }

  function renderAuditLogsResponse(body) {
    if (!auditResult) return;

    const items = body && Array.isArray(body.items) ? body.items : [];
    const totalCount = Number(body && body.totalCount || 0);
    const limit = Number(body && body.limit || 0);
    const offset = Number(body && body.offset || 0);

    if (!items.length) {
      auditResult.innerHTML = `<div class="table-empty">조회된 운영 로그가 없습니다. total=${escapeHtml(totalCount)} / limit=${escapeHtml(limit)} / offset=${escapeHtml(offset)}</div>`;
      return;
    }

    auditResult.innerHTML = `
      <table class="manager-table">
        <thead>
          <tr>
            <th>No</th>
            <th>ID</th>
            <th>작업시각</th>
            <th>담당자</th>
            <th>액션</th>
            <th>상세</th>
          </tr>
        </thead>
        <tbody>
          ${items.map((row, index) => {
            const data = row && row.data ? row.data : {};
            return `
              <tr>
                <td data-label="No">${offset + index + 1}</td>
                <td data-label="ID">${escapeHtml(data.id || "-")}</td>
                <td data-label="작업시각">${escapeHtml(formatDateTime(data.created_at))}</td>
                <td data-label="담당자">${escapeHtml(data.actor_teacher_id || "-")}</td>
                <td data-label="액션"><span class="badge-soft">${escapeHtml(data.action_type || "-")}</span></td>
                <td data-label="상세">${escapeHtml(data.detail || "-")}</td>
              </tr>
            `;
          }).join("")}
        </tbody>
      </table>
    `;
  }

  function normalizeBirthDateInput(value) {
    const digits = String(value || "").replace(/[^0-9]/g, "");
    if (!digits) return "";
    if (digits.length !== 8) {
      throw new Error("생년월일은 8자리 숫자로 입력해야 합니다.");
    }
    return digits;
  }

  function formatSchoolGrade(value) {
    const text = String(value || "").trim();
    return text ? `${text}학년` : "-";
  }

  function roleLabel(role) {
    const normalized = String(role || "").toUpperCase();
    if (normalized === "ADMIN") return "관리자";
    if (normalized === "PASTOR") return "전도사";
    if (normalized === "DIRECTOR") return "부장";
    return "교사";
  }

  function rolePriority(role) {
    const normalized = String(role || "").toUpperCase();
    if (normalized === "ADMIN") return 0;
    if (normalized === "PASTOR") return 1;
    if (normalized === "DIRECTOR") return 2;
    return 3;
  }

  function assignmentRoleLabel(role) {
    return String(role || "").toUpperCase() === "HOMEROOM" ? "담임" : "보조";
  }

  function assignmentRoleOptions(selectedRole) {
    const normalized = String(selectedRole || "ASSISTANT").toUpperCase();
    return `
      <option value="ASSISTANT" ${normalized === "ASSISTANT" ? "selected" : ""}>보조</option>
      <option value="HOMEROOM" ${normalized === "HOMEROOM" ? "selected" : ""}>담임</option>
    `;
  }

  function getActiveYearValue() {
    const activeYear = cachedYears.find(year => !!year.active);
    if (activeYear && Number.isFinite(Number(activeYear.yearValue))) {
      return Number(activeYear.yearValue);
    }
    return null;
  }

  function syncYearOptions() {
    const options = cachedYears.map(year => `<option value="${year.yearValue}">${year.yearValue}년</option>`).join("");
    const yearFilter = document.getElementById("classManagementYearFilter");
    const classYearSelect = document.getElementById("classEditorYearValue");
    const poolYearSelect = document.getElementById("poolYearFilter");
    const assignmentYearSelect = document.getElementById("assignmentYearFilter");
    yearFilter.innerHTML = options;
    classYearSelect.innerHTML = options;
    if (poolYearSelect) {
      poolYearSelect.innerHTML = options;
    }
    if (assignmentYearSelect) {
      assignmentYearSelect.innerHTML = options;
    }

    if (!yearFilter.value && cachedYears[0]) {
      yearFilter.value = String(cachedYears[0].yearValue);
    }
    if (!classYearSelect.value && cachedYears[0]) {
      classYearSelect.value = String(cachedYears[0].yearValue);
    }
    if (poolYearSelect && !poolYearSelect.value && cachedYears[0]) {
      poolYearSelect.value = String(getActiveYearValue() || cachedYears[0].yearValue);
    }
    if (assignmentYearSelect && !assignmentYearSelect.value && cachedYears[0]) {
      assignmentYearSelect.value = String(getActiveYearValue() || cachedYears[0].yearValue);
    }
  }

  function getPoolYearValue() {
    const selected = Number(poolYearFilter && poolYearFilter.value);
    if (Number.isFinite(selected)) {
      return selected;
    }
    return getActiveYearValue();
  }

  function currentAssignmentYearClassId() {
    const rawValue = String(document.getElementById("assignmentYearClassSelect").value || "").trim();
    if (!rawValue) return null;
    const value = Number(rawValue);
    return Number.isFinite(value) && value > 0 ? value : null;
  }

  function getAssignmentYearValue() {
    const selected = Number(document.getElementById("assignmentYearFilter").value);
    if (Number.isFinite(selected)) {
      return selected;
    }
    return getActiveYearValue();
  }

  function currentAssignmentYearClass() {
    const yearClassId = currentAssignmentYearClassId();
    return cachedYearClasses.find(item => item.yearClassId === yearClassId) || null;
  }

  function syncAssignmentYearClassOptions() {
    const select = document.getElementById("assignmentYearClassSelect");
    const options = cachedYearClasses.map(item =>
      `<option value="${item.yearClassId}">${escapeHtml(item.className)}</option>`
    ).join("");
    select.innerHTML = options;

    const current = currentAssignmentYearClassId();
    if (current && cachedYearClasses.some(item => item.yearClassId === current)) {
      setAssignmentTarget(current);
    } else if (cachedYearClasses[0]) {
      setAssignmentTarget(cachedYearClasses[0].yearClassId);
    } else {
      document.getElementById("assignTeacherYearClassId").value = "";
      document.getElementById("assignStudentYearClassId").value = "";
    }
  }

  function renderAssignmentManagement() {
    const teacherContainer = document.getElementById("assignmentTeacherList");
    const studentContainer = document.getElementById("assignmentStudentList");
    const summary = document.getElementById("assignmentTargetSummary");
    const yearClass = currentAssignmentYearClass();

    if (!yearClass) {
      if (summary) summary.textContent = "선택한 반 정보가 없습니다.";
      teacherContainer.innerHTML = '<div class="table-empty">반을 먼저 선택해 주세요.</div>';
      studentContainer.innerHTML = '<div class="table-empty">반을 먼저 선택해 주세요.</div>';
      return;
    }

    const teacherAssignmentMap = new Map();
    const studentAssignmentMap = new Map();
    cachedYearClasses.forEach(item => {
      (item.teachers || []).forEach(teacher => {
        teacherAssignmentMap.set(teacher.teacherId, item.className || "-");
      });
      (item.students || []).forEach(student => {
        studentAssignmentMap.set(student.studentId, item.className || "-");
      });
    });
    const assignedTeacherIds = new Set((yearClass.teachers || []).map(item => item.teacherId));
    const assignedStudentIds = new Set((yearClass.students || []).map(item => item.studentId));
    const assignedTeacherMap = new Map((yearClass.teachers || []).map(item => [item.teacherId, item]));
    if (summary) {
      summary.textContent = `${yearClass.yearValue}년 ${yearClass.className} · 교사 ${(yearClass.teachers || []).length}명 · 학생 ${(yearClass.students || []).length}명`;
    }
    const assignmentYearFilter = document.getElementById("assignmentYearFilter");
    if (assignmentYearFilter && Number.isFinite(Number(yearClass.yearValue))) {
      assignmentYearFilter.value = String(yearClass.yearValue);
    }

    const filteredTeachers = assignmentTeacherItems
      .slice()
      .sort((a, b) => {
        const assignedDiff = Number(assignedTeacherIds.has(b.teacherId)) - Number(assignedTeacherIds.has(a.teacherId));
        if (assignedDiff !== 0) return assignedDiff;
        const roleDiff = rolePriority(a.role) - rolePriority(b.role);
        if (roleDiff !== 0) return roleDiff;
        return String(a.teacherName || "").localeCompare(String(b.teacherName || ""), "ko");
      });
    const filteredStudents = assignmentStudentItems
      .slice()
      .sort((a, b) => {
        const assignedDiff = Number(assignedStudentIds.has(b.studentId)) - Number(assignedStudentIds.has(a.studentId));
        if (assignedDiff !== 0) return assignedDiff;
        const gradeA = Number.isFinite(Number(a.schoolGrade)) ? Number(a.schoolGrade) : -1;
        const gradeB = Number.isFinite(Number(b.schoolGrade)) ? Number(b.schoolGrade) : -1;
        if (gradeA !== gradeB) return gradeB - gradeA;
        return String(a.studentName || "").localeCompare(String(b.studentName || ""), "ko");
      });

    teacherContainer.innerHTML = filteredTeachers.length ? `
      <table class="manager-table">
        <thead>
          <tr>
            <th class="selection-cell">선택</th>
            <th>No</th>
            <th>이름</th>
            <th>소속 반</th>
            <th>반 역할</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          ${filteredTeachers.map((item, index) => `
            <tr>
              <td data-label="선택" class="selection-cell">
                <input class="table-checkbox assignmentTeacherSelect" type="checkbox" value="${item.teacherId}" ${selectedAssignmentTeacherIds.has(item.teacherId) ? "checked" : ""} />
              </td>
              <td data-label="No">${index + 1}</td>
              <td data-label="이름">${escapeHtml(item.teacherName || "-")}</td>
              <td data-label="소속 반">${escapeHtml(teacherAssignmentMap.get(item.teacherId) || "-")}</td>
              <td data-label="반 역할">
                ${assignedTeacherIds.has(item.teacherId)
                  ? `<select class="assignmentTeacherRoleSelect" data-teacher-id="${item.teacherId}">
                      ${assignmentRoleOptions(assignedTeacherMap.get(item.teacherId)?.assignmentRole)}
                    </select>`
                  : '<span class="simple-meta">-</span>'}
              </td>
              <td data-label="상태"><span class="assignment-status-pill ${teacherAssignmentMap.has(item.teacherId) ? "assigned" : "unassigned"}">${teacherAssignmentMap.has(item.teacherId) ? "배정됨" : "미배정"}</span></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    ` : '<div class="table-empty">조건에 맞는 교사가 없습니다.</div>';

    studentContainer.innerHTML = filteredStudents.length ? `
      <table class="manager-table">
        <thead>
          <tr>
            <th class="selection-cell">선택</th>
            <th>No</th>
            <th>이름</th>
            <th>학년</th>
            <th>소속 반</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          ${filteredStudents.map((item, index) => `
            <tr>
              <td data-label="선택" class="selection-cell">
                <input class="table-checkbox assignmentStudentSelect" type="checkbox" value="${item.studentId}" ${selectedAssignmentStudentIds.has(item.studentId) ? "checked" : ""} />
              </td>
              <td data-label="No">${index + 1}</td>
              <td data-label="이름">${escapeHtml(item.studentName || "-")}</td>
              <td data-label="학년">${escapeHtml(formatSchoolGrade(item.schoolGrade))}</td>
              <td data-label="소속 반">${escapeHtml(studentAssignmentMap.get(item.studentId) || "-")}</td>
              <td data-label="상태"><span class="assignment-status-pill ${studentAssignmentMap.has(item.studentId) ? "assigned" : "unassigned"}">${studentAssignmentMap.has(item.studentId) ? "배정됨" : "미배정"}</span></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    ` : '<div class="table-empty">조건에 맞는 학생이 없습니다.</div>';

    teacherContainer.querySelectorAll(".assignmentTeacherSelect").forEach(checkbox => {
      checkbox.addEventListener("change", function () {
        const teacherId = Number(checkbox.value);
        if (checkbox.checked) selectedAssignmentTeacherIds.add(teacherId);
        else selectedAssignmentTeacherIds.delete(teacherId);
      });
    });

    teacherContainer.querySelectorAll(".assignmentTeacherRoleSelect").forEach(select => {
      select.addEventListener("change", async function () {
        const teacherId = Number(select.dataset.teacherId);
        const assignmentRole = String(select.value || "ASSISTANT").toUpperCase();
        try {
          await patchTeacherAssignmentRole(yearClass.yearClassId, teacherId, assignmentRole);
          await refreshYearClasses();
        } catch (e) {
          render(e.message);
        }
      });
    });

    studentContainer.querySelectorAll(".assignmentStudentSelect").forEach(checkbox => {
      checkbox.addEventListener("change", function () {
        const studentId = Number(checkbox.value);
        if (checkbox.checked) selectedAssignmentStudentIds.add(studentId);
        else selectedAssignmentStudentIds.delete(studentId);
      });
    });
  }

  async function loadAssignmentPools() {
    const assignmentYear = getAssignmentYearValue()
      || (currentAssignmentYearClass() && currentAssignmentYearClass().yearValue)
      || (Number.isFinite(Number(queryYearInput.value)) ? Number(queryYearInput.value) : null);
    if (!Number.isFinite(Number(assignmentYear))) {
      assignmentTeacherItems = [];
      assignmentStudentItems = [];
      renderAssignmentManagement();
      return;
    }
    const [teachersResponse, studentsResponse] = await Promise.all([
      api(`/api/admin/teachers?year=${assignmentYear}&activeOnly=true&limit=200&offset=0`, { method: "GET" }),
      api(`/api/admin/students?year=${assignmentYear}&activeOnly=true&limit=200&offset=0`, { method: "GET" })
    ]);
    assignmentTeacherItems = Array.isArray(teachersResponse.items) ? teachersResponse.items : [];
    assignmentStudentItems = Array.isArray(studentsResponse.items) ? studentsResponse.items : [];
    renderAssignmentManagement();
  }

  function renderYearManagement() {
    const container = document.getElementById("yearManagementList");
    if (!cachedYears.length) {
      container.innerHTML = '<div class="table-empty">등록된 연도가 없습니다.</div>';
      return;
    }

    container.innerHTML = `
      <table class="manager-table">
        <thead>
          <tr>
            <th>No</th>
            <th>연도</th>
            <th>활성</th>
            <th>관리</th>
          </tr>
        </thead>
        <tbody>
          ${cachedYears.map((year, index) => `
            <tr>
              <td data-label="No">${index + 1}</td>
              <td data-label="연도">${escapeHtml(year.yearValue)}년</td>
              <td data-label="활성"><span class="badge-soft">${year.active ? "활성" : "비활성"}</span></td>
              <td data-label="관리">
                <div class="row-actions">
                  <button class="ghost btnEditYearRow" type="button" data-year-id="${year.id}">수정</button>
                </div>
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;

    container.querySelectorAll(".btnEditYearRow").forEach(button => {
      button.addEventListener("click", function () {
        const target = cachedYears.find(item => item.id === Number(button.dataset.yearId));
        if (!target) return;
        yearEditorState.mode = "edit";
        yearEditorState.yearId = target.id;
        document.getElementById("yearEditorTitle").textContent = "연도 수정";
        document.getElementById("yearEditorValue").value = String(target.yearValue || "");
        document.getElementById("yearEditorValue").disabled = true;
        document.getElementById("yearEditorActive").checked = !!target.active;
        document.getElementById("classEditorPanel").classList.add("hidden");
        setYearClassTab("years");
        document.getElementById("yearEditorPanel").classList.remove("hidden");
      });
    });
  }

  function renderClassManagement() {
    const container = document.getElementById("classManagementList");
    const filterYear = Number(document.getElementById("classManagementYearFilter").value);
    const rows = cachedYearClasses
      .filter(item => !Number.isFinite(filterYear) || item.yearValue === filterYear)
      .slice()
      .sort((a, b) => {
        if (a.yearValue !== b.yearValue) return b.yearValue - a.yearValue;
        return a.sortOrder - b.sortOrder;
      });

    if (!rows.length) {
      container.innerHTML = '<div class="table-empty">선택한 연도에 등록된 반이 없습니다.</div>';
      return;
    }

    container.innerHTML = `
      <table class="manager-table">
        <thead>
          <tr>
            <th>No</th>
            <th>연도</th>
            <th>반 이름</th>
            <th>상태</th>
            <th>관리</th>
          </tr>
        </thead>
        <tbody>
          ${rows.map((item, index) => `
            <tr>
              <td data-label="No">${index + 1}</td>
              <td data-label="연도">${escapeHtml(item.yearValue)}년</td>
              <td data-label="반 이름">${escapeHtml(item.className)}</td>
              <td data-label="상태"><span class="badge-soft">${item.active ? "활성" : "비활성"}</span></td>
              <td data-label="관리">
                <div class="row-actions">
                  <button class="ghost btnEditClassRow" type="button" data-year-class-id="${item.yearClassId}">수정</button>
                </div>
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;

    container.querySelectorAll(".btnEditClassRow").forEach(button => {
      button.addEventListener("click", function () {
        const target = cachedYearClasses.find(item => item.yearClassId === Number(button.dataset.yearClassId));
        if (!target) return;
        classEditorState.mode = "edit";
        classEditorState.yearClassId = target.yearClassId;
        document.getElementById("classEditorTitle").textContent = "반 수정";
        document.getElementById("classEditorYearValue").value = String(target.yearValue || "");
        document.getElementById("classEditorYearValue").disabled = true;
        document.getElementById("classEditorName").value = target.className || "";
        document.getElementById("classEditorSortOrder").value = String(target.sortOrder || "");
        document.getElementById("classEditorActive").checked = !!target.active;
        document.getElementById("yearEditorPanel").classList.add("hidden");
        setYearClassTab("classes");
        document.getElementById("classEditorPanel").classList.remove("hidden");
      });
    });
  }

  function openCreateYearEditor() {
    yearEditorState.mode = "create";
    yearEditorState.yearId = null;
    document.getElementById("yearEditorTitle").textContent = "연도 추가";
    document.getElementById("yearEditorValue").disabled = false;
    document.getElementById("yearEditorValue").value = "";
    document.getElementById("yearEditorActive").checked = true;
    document.getElementById("classEditorPanel").classList.add("hidden");
    setYearClassTab("years");
    document.getElementById("yearEditorPanel").classList.remove("hidden");
  }

  function openCreateClassEditor() {
    classEditorState.mode = "create";
    classEditorState.yearClassId = null;
    document.getElementById("classEditorTitle").textContent = "반 추가";
    document.getElementById("classEditorYearValue").disabled = false;
    document.getElementById("classEditorYearValue").value = document.getElementById("classManagementYearFilter").value || "";
    document.getElementById("classEditorName").value = "";
    document.getElementById("classEditorSortOrder").value = "";
    document.getElementById("classEditorActive").checked = true;
    document.getElementById("yearEditorPanel").classList.add("hidden");
    setYearClassTab("classes");
    document.getElementById("classEditorPanel").classList.remove("hidden");
  }

  async function reloadClassManagementForSelectedYear() {
    const selectedYear = Number(document.getElementById("classManagementYearFilter").value) || getActiveYearValue();
    if (!Number.isFinite(selectedYear)) {
      renderClassManagement();
      return;
    }
    queryYearInput.value = String(selectedYear);
    await refreshYearClasses();
  }

  function renderTeacherManagement() {
    const container = document.getElementById("teacherManagementList");
    if (!teacherPoolItems.length) {
      container.innerHTML = '<div class="table-empty">조회된 교사가 없습니다.</div>';
      return;
    }

    const sortedTeacherItems = teacherPoolItems
      .slice()
      .sort((a, b) => {
        const byRole = rolePriority(a.role) - rolePriority(b.role);
        if (byRole !== 0) return byRole;
        return String(a.teacherName || "").localeCompare(String(b.teacherName || ""), "ko");
      });

    container.innerHTML = `
      <table class="manager-table">
        <thead>
          <tr>
            <th>No</th>
            <th>이름</th>
            <th>역할</th>
            <th>연락처</th>
            <th>생년월일</th>
            <th>관리</th>
          </tr>
        </thead>
        <tbody>
          ${sortedTeacherItems.map((item, index) => `
            <tr>
              <td data-label="No">${index + 1}</td>
              <td data-label="이름">${escapeHtml(item.teacherName || "-")}</td>
              <td data-label="역할">${escapeHtml(roleLabel(item.role))}</td>
              <td data-label="연락처">${escapeHtml(formatContactNumber(item.contactNumber))}</td>
              <td data-label="생년월일">${escapeHtml(formatBirthDate(item.birthDate))}</td>
              <td data-label="관리"><button class="ghost btnEditTeacherRow" type="button" data-teacher-id="${item.teacherId}">수정</button></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;

    container.querySelectorAll(".btnEditTeacherRow").forEach(button => {
      button.addEventListener("click", function () {
        const target = teacherPoolItems.find(item => item.teacherId === Number(button.dataset.teacherId));
        if (!target) return;
        teacherEditorState.mode = "edit";
        teacherEditorState.teacherId = target.teacherId;
        document.getElementById("teacherEditorTitle").textContent = "교사 수정";
        document.getElementById("teacherEditorLoginId").value = target.loginId || "";
        document.getElementById("teacherEditorLoginId").disabled = true;
        document.getElementById("teacherEditorName").value = target.teacherName || "";
        document.getElementById("teacherEditorPassword").value = "";
        document.getElementById("teacherEditorPassword").placeholder = "변경할 때만 입력";
        document.getElementById("teacherEditorContactNumber").value = formatContactNumber(target.contactNumber).replace(/^-$/, "");
        document.getElementById("teacherEditorBirthDate").value = String(target.birthDate || "");
        document.getElementById("teacherEditorRole").value = target.role || "TEACHER";
        document.getElementById("teacherEditorActive").checked = !!target.active;
        document.getElementById("studentEditorPanel").classList.add("hidden");
        document.getElementById("teacherEditorPanel").classList.remove("hidden");
      });
    });
  }

  function renderStudentManagement() {
    const container = document.getElementById("studentManagementList");
    if (!studentPoolItems.length) {
      container.innerHTML = '<div class="table-empty">조회된 학생이 없습니다.</div>';
      return;
    }

    container.innerHTML = `
      <table class="manager-table">
        <thead>
          <tr>
            <th>No</th>
            <th>이름</th>
            <th>학년</th>
            <th>연락처</th>
            <th>생년월일</th>
            <th>관리</th>
          </tr>
        </thead>
        <tbody>
          ${studentPoolItems.map((item, index) => `
            <tr>
              <td data-label="No">${index + 1}</td>
              <td data-label="이름">${escapeHtml(item.studentName || "-")}</td>
              <td data-label="학년">${escapeHtml(formatSchoolGrade(item.schoolGrade))}</td>
              <td data-label="연락처">${escapeHtml(formatContactNumber(item.contactNumber))}</td>
              <td data-label="생년월일">${escapeHtml(formatBirthDate(item.birthDate))}</td>
              <td data-label="관리"><button class="ghost btnEditStudentRow" type="button" data-student-id="${item.studentId}">수정</button></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;

    container.querySelectorAll(".btnEditStudentRow").forEach(button => {
      button.addEventListener("click", function () {
        const target = studentPoolItems.find(item => item.studentId === Number(button.dataset.studentId));
        if (!target) return;
        studentEditorState.mode = "edit";
        studentEditorState.studentId = target.studentId;
        document.getElementById("studentEditorTitle").textContent = "학생 수정";
        document.getElementById("studentEditorName").value = target.studentName || "";
        document.getElementById("studentEditorSchoolGrade").value = target.schoolGrade == null ? "" : String(target.schoolGrade);
        document.getElementById("studentEditorContactNumber").value = formatContactNumber(target.contactNumber).replace(/^-$/, "");
        document.getElementById("studentEditorBirthDate").value = String(target.birthDate || "");
        document.getElementById("studentEditorActive").checked = !!target.active;
        document.getElementById("teacherEditorPanel").classList.add("hidden");
        document.getElementById("studentEditorPanel").classList.remove("hidden");
      });
    });
  }

  function openCreateTeacherEditor() {
    teacherEditorState.mode = "create";
    teacherEditorState.teacherId = null;
    document.getElementById("teacherEditorTitle").textContent = "교사 추가";
    document.getElementById("teacherEditorLoginId").disabled = false;
    document.getElementById("teacherEditorLoginId").value = "";
    document.getElementById("teacherEditorName").value = "";
    document.getElementById("teacherEditorPassword").value = "";
    document.getElementById("teacherEditorPassword").placeholder = "초기 비밀번호";
    document.getElementById("teacherEditorContactNumber").value = "";
    document.getElementById("teacherEditorBirthDate").value = "";
    document.getElementById("teacherEditorRole").value = "TEACHER";
    document.getElementById("teacherEditorActive").checked = true;
    document.getElementById("studentEditorPanel").classList.add("hidden");
    document.getElementById("teacherEditorPanel").classList.remove("hidden");
  }

  function openCreateStudentEditor() {
    studentEditorState.mode = "create";
    studentEditorState.studentId = null;
    document.getElementById("studentEditorTitle").textContent = "학생 추가";
    document.getElementById("studentEditorName").value = "";
    document.getElementById("studentEditorSchoolGrade").value = "";
    document.getElementById("studentEditorContactNumber").value = "";
    document.getElementById("studentEditorBirthDate").value = "";
    document.getElementById("studentEditorActive").checked = true;
    document.getElementById("teacherEditorPanel").classList.add("hidden");
    document.getElementById("studentEditorPanel").classList.remove("hidden");
  }

  function parseIds(text) {
    return text.split(",")
      .map(v => v.trim())
      .filter(Boolean)
      .map(Number)
      .filter(v => Number.isFinite(v) && v > 0);
  }

  function ensureNonEmptyIds(ids, label) {
    if (!ids || ids.length === 0) {
      throw new Error(label + "가 비어 있습니다.");
    }
    return ids;
  }

  function confirmAction(message) {
    return window.confirm(message);
  }

  function tokenOrThrow() {
    const token = String(adminToken || "").trim();
    if (!token) throw new Error("토큰이 비어 있습니다.");
    return token;
  }

  function getTargetYearClassId() {
    const teacherValue = String(document.getElementById("assignTeacherYearClassId").value || "").trim();
    const studentValue = String(document.getElementById("assignStudentYearClassId").value || "").trim();
    const selectValue = String(document.getElementById("assignmentYearClassSelect").value || "").trim();
    const rawValue = teacherValue || studentValue || selectValue;
    if (!rawValue) throw new Error("배정 대상 yearClassId를 입력하세요.");
    const value = Number(rawValue);
    if (!Number.isFinite(value) || value <= 0) throw new Error("배정 대상 yearClassId를 입력하세요.");
    return value;
  }

  function setAssignmentTarget(yearClassId) {
    document.getElementById("assignTeacherYearClassId").value = String(yearClassId);
    document.getElementById("assignStudentYearClassId").value = String(yearClassId);
    const assignmentSelect = document.getElementById("assignmentYearClassSelect");
    if (assignmentSelect && yearClassId) {
      assignmentSelect.value = String(yearClassId);
    }
    selectedAssignmentTeacherIds.clear();
    selectedAssignmentStudentIds.clear();
    setStatus("작업 대상 반 설정: " + yearClassId);
    renderAssignmentManagement();
  }

  async function runAndRefresh(actionPromiseFactory) {
    const body = await actionPromiseFactory();
    render(body);
    await refreshYearClasses();
    return body;
  }

  async function runUndoAware(forward, backward) {
    const body = await runAndRefresh(forward.execute);
    setUndoAction({
      label: backward.label,
      execute: backward.execute
    });
    return body;
  }

  async function api(path, options) {
    const token = tokenOrThrow();
    setStatus("요청 중...");
    const response = await fetch(path, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        ...(options && options.headers ? options.headers : {})
      }
    });

    const text = await response.text();
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch (_) {
      body = text;
    }

    if (!response.ok) {
      setStatus("요청 실패");
      const message = extractErrorMessage(body);
      if (isAuthorizationFailure(response.status, message)) {
        clearTokenAndReset(message || "인증이 만료되었습니다. 다시 로그인해 주세요.");
      }
      throw new Error(message);
    }
    setStatus("요청 성공");
    return body;
  }

  async function validateAdminSession() {
    const token = String(adminToken || "").trim();
    if (!token) {
      setAdminAuthenticated(false);
      return null;
    }

    try {
      const response = await api("/api/admin/me", { method: "GET" });
      setAdminAuthenticated(true, response && response.teacherName);
      return response;
    } catch (e) {
      const message = String(e && e.message ? e.message : e);
      if (isAuthorizationFailure(0, message)) {
        clearTokenAndReset(message);
        return null;
      }
      throw e;
    }
  }

  function postAssignTeachers(yearClassId, teacherIds) {
    return api("/api/admin/year-classes/" + yearClassId + "/teachers", {
      method: "POST",
      body: JSON.stringify({ teacherIds })
    });
  }

  function deleteAssignTeachers(yearClassId, teacherIds) {
    return api("/api/admin/year-classes/" + yearClassId + "/teachers", {
      method: "DELETE",
      body: JSON.stringify({ teacherIds })
    });
  }

  function patchTeacherAssignmentRole(yearClassId, teacherId, assignmentRole) {
    return api(`/api/admin/year-classes/${yearClassId}/teachers/${teacherId}/assignment-role`, {
      method: "PATCH",
      body: JSON.stringify({ assignmentRole })
    });
  }

  function postAssignStudents(yearClassId, studentIds) {
    return api("/api/admin/year-classes/" + yearClassId + "/students", {
      method: "POST",
      body: JSON.stringify({ studentIds })
    });
  }

  function deleteAssignStudents(yearClassId, studentIds) {
    return api("/api/admin/year-classes/" + yearClassId + "/students", {
      method: "DELETE",
      body: JSON.stringify({ studentIds })
    });
  }

  function moveStudents(targetYearClassId, studentIds) {
    return api("/api/admin/year-classes/" + targetYearClassId + "/students/move", {
      method: "PATCH",
      body: JSON.stringify({ studentIds })
    });
  }

  function studentSourceMap() {
    const map = new Map();
    cachedYearClasses.forEach(yc => {
      (yc.students || []).forEach(s => map.set(s.studentId, yc.yearClassId));
    });
    return map;
  }

  function includesText(source, keyword) {
    if (!keyword) return true;
    return (source || "").toLowerCase().includes(keyword.toLowerCase());
  }

  function buildStudentCalendarUrl(studentId, yearValue) {
    const today = new Date();
    return `/app/teacher/students/${studentId}/calendar?year=${yearValue}&month=${today.getMonth() + 1}&source=admin`;
  }

  function applyFilterAndRender() {
    const filtered = cachedYearClasses.slice();
    const activeYear = getActiveYearValue()
      || (Number.isFinite(Number(dashboardYearValue)) ? Number(dashboardYearValue) : null)
      || (filtered[0] ? filtered[0].yearValue : null);
    const yearBadge = document.getElementById("dashboardYearBadge");
    if (yearBadge) {
      yearBadge.textContent = activeYear ? `활성 연도 : ${activeYear}년` : "활성 연도 -";
    }

    if (!filtered.length) {
      const rankingContainer = document.getElementById("dashboardOverallRanking");
      if (rankingContainer) {
        rankingContainer.innerHTML = '<div class="table-empty">순위 데이터가 없습니다.</div>';
      }
      dashboardClassBoard.innerHTML = '<div class="table-empty">조건에 맞는 반이 없습니다.</div>';
      setStatus("");
      return;
    }

    const overallStudents = filtered
      .flatMap(item => Array.isArray(item.students) ? item.students : [])
      .reduce((acc, student) => {
        if (!acc.some(item => item.studentId === student.studentId)) {
          acc.push(student);
        }
        return acc;
      }, [])
      .sort((a, b) => (b.qtCount || 0) - (a.qtCount || 0) || (b.totalCount || 0) - (a.totalCount || 0))
      .slice(0, 5);

    const rankingContainer = document.getElementById("dashboardOverallRanking");
    if (rankingContainer) {
      rankingContainer.innerHTML = overallStudents.length ? `
        <table class="manager-table">
          <thead>
            <tr>
              <th>순위</th>
              <th>이름</th>
              <th>학년</th>
              <th>QT</th>
              <th>노트</th>
              <th>태도</th>
              <th>총합</th>
            </tr>
          </thead>
          <tbody>
            ${overallStudents.map((student, index) => `
              <tr>
                <td data-label="순위">${index + 1}</td>
                <td data-label="이름">${escapeHtml(student.studentName)}</td>
                <td data-label="학년">${escapeHtml(formatSchoolGrade(student.schoolGrade))}</td>
                <td data-label="QT"><strong>${student.qtCount || 0}</strong></td>
                <td data-label="노트">${student.noteCount || 0}</td>
                <td data-label="태도">${student.attitudeCount || 0}</td>
                <td data-label="총합">${student.totalCount || 0}</td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      ` : '<div class="table-empty">순위 데이터가 없습니다.</div>';
    }

    dashboardClassBoard.innerHTML = filtered.map(item => {
      const teachers = (Array.isArray(item.teachers) ? item.teachers : []).slice().sort((a, b) => {
        const aHomeroom = String(a.assignmentRole || "").toUpperCase() === "HOMEROOM" ? 0 : 1;
        const bHomeroom = String(b.assignmentRole || "").toUpperCase() === "HOMEROOM" ? 0 : 1;
        if (aHomeroom !== bHomeroom) return aHomeroom - bHomeroom;
        return String(a.teacherName || "").localeCompare(String(b.teacherName || ""), "ko");
      });
      const students = (Array.isArray(item.students) ? item.students : []).slice().sort((a, b) => {
        const gradeA = Number.isFinite(Number(a.schoolGrade)) ? Number(a.schoolGrade) : -1;
        const gradeB = Number.isFinite(Number(b.schoolGrade)) ? Number(b.schoolGrade) : -1;
        if (gradeA !== gradeB) return gradeB - gradeA;
        return String(a.studentName || "").localeCompare(String(b.studentName || ""), "ko");
      });
      const ranking = students
        .slice()
        .sort((a, b) => (b.totalCount || 0) - (a.totalCount || 0) || (b.qtCount || 0) - (a.qtCount || 0))
        .slice(0, 5);

      return `
        <article class="dashboard-class-card">
          <div class="dashboard-class-head">
            <div>
              <h3 class="dashboard-class-title">${escapeHtml(item.className)}</h3>
              <p class="dashboard-class-meta">담당 : ${teachers.map(t => `${t.teacherName}${String(t.assignmentRole || "").toUpperCase() === "HOMEROOM" ? " (담임)" : ""}`).join(", ") || "미배정"} · 학생 ${students.length}명</p>
            </div>
          </div>

          <div class="student-chip-grid">
            ${students.length ? students.map(student => `
              <button class="student-link-card btnOpenStudentCalendar" type="button" data-student-id="${student.studentId}" data-year-value="${item.yearValue}">
                <div class="student-link-main">
                  <strong class="student-link-name">${escapeHtml(student.studentName)}${student.schoolGrade ? ` (${escapeHtml(student.schoolGrade)}학년)` : ""}</strong>
                  <span class="badge-soft student-link-fruits">🍇 QT ${student.qtCount || 0} · 🍐 노트 ${student.noteCount || 0} · 🫒 태도 ${student.attitudeCount || 0}</span>
                </div>
                <div class="student-link-sub">총 ${student.totalCount || 0}개 · 누르면 체크 달력 열기</div>
              </button>
            `).join("") : '<div class="table-empty">등록된 학생이 없습니다.</div>'}
          </div>

          <div class="ranking-block">
            <h4 class="ranking-title">열매 개수 순위</h4>
            <div class="ranking-list">
              ${ranking.length ? ranking.map((student, index) => `
                <div class="ranking-row">
                  <span>${index + 1}. ${escapeHtml(student.studentName)}</span>
                  <strong>총합 ${student.totalCount || 0}</strong>
                </div>
              `).join("") : '<div class="table-empty">순위 데이터가 없습니다.</div>'}
            </div>
          </div>
        </article>
      `;
    }).join("");

    dashboardClassBoard.querySelectorAll(".btnOpenStudentCalendar").forEach(button => {
      button.addEventListener("click", function () {
        const path = buildStudentCalendarUrl(Number(button.dataset.studentId), Number(button.dataset.yearValue));
        window.open(path, "_blank", "noopener");
      });
    });

    setStatus("");
  }

  async function refreshYearClasses() {
    const requestedYear = Number(queryYearInput.value);
    const year = Number.isFinite(requestedYear) ? requestedYear : getActiveYearValue();
    if (!Number.isFinite(year)) throw new Error("조회 연도를 입력하세요.");
    dashboardYearValue = year;
    queryYearInput.value = String(year);
    const body = await api("/api/admin/year-classes?year=" + year, { method: "GET" });
    cachedYearClasses = Array.isArray(body) ? body : [];
    applyFilterAndRender();
    syncYearOptions();
    const assignmentYearFilter = document.getElementById("assignmentYearFilter");
    if (assignmentYearFilter) {
      assignmentYearFilter.value = String(year);
    }
    syncAssignmentYearClassOptions();
    document.getElementById("classManagementYearFilter").value = String(year);
    renderClassManagement();
    renderAssignmentManagement();
    await loadAssignmentPools();
    return body;
  }

  async function loadBootstrapAndDefaultYear() {
    const bootstrapParams = new URLSearchParams();
    bootstrapParams.set("poolLimit", "10");
    const auditLimit = Number(document.getElementById("auditLimit").value || 100);
    const auditOffset = Number(document.getElementById("auditOffset").value || 0);
    const auditActorTeacherIdRaw = document.getElementById("auditActorTeacherId").value.trim();
    const auditActorTeacherId = Number(auditActorTeacherIdRaw);
    const auditActionType = document.getElementById("auditActionType").value.trim();
    const auditKeyword = document.getElementById("auditKeyword").value.trim();
    const auditFromAt = document.getElementById("auditFromAt").value.trim();
    const auditToAt = document.getElementById("auditToAt").value.trim();
    const includeYearClasses = true;
    const includeActionTypes = true;
    const includePools = false;
    const includeAuditLogs = false;
    localStorage.setItem("qt_admin_bootstrap_include_year_classes", String(includeYearClasses));
    localStorage.setItem("qt_admin_bootstrap_include_action_types", String(includeActionTypes));
    localStorage.setItem("qt_admin_bootstrap_include_pools", String(includePools));
    localStorage.setItem("qt_admin_bootstrap_include_audit_logs", String(includeAuditLogs));
    if (Number.isFinite(auditLimit)) bootstrapParams.set("auditLimit", String(auditLimit));
    if (Number.isFinite(auditOffset)) bootstrapParams.set("auditOffset", String(auditOffset));
    if (Number.isFinite(auditActorTeacherId) && auditActorTeacherId > 0) {
      bootstrapParams.set("auditActorTeacherId", String(auditActorTeacherId));
    }
    if (auditActionType) bootstrapParams.set("auditActionType", auditActionType);
    if (auditKeyword) bootstrapParams.set("auditKeyword", auditKeyword);
    if (auditFromAt) bootstrapParams.set("auditFromAt", auditFromAt);
    if (auditToAt) bootstrapParams.set("auditToAt", auditToAt);
    bootstrapParams.set("includeYearClasses", String(includeYearClasses));
    bootstrapParams.set("includeActionTypes", String(includeActionTypes));
    bootstrapParams.set("includePools", String(includePools));
    bootstrapParams.set("includeAuditLogs", String(includeAuditLogs));
    const bootstrapPath = "/api/admin/bootstrap" + (bootstrapParams.toString() ? "?" + bootstrapParams : "");
    updateBootstrapRequestHint(bootstrapPath);
    const body = await api(bootstrapPath, { method: "GET" });
    const years = Array.isArray(body && body.years) ? body.years : [];
    const actionTypes = Array.isArray(body && body.auditLogActionTypes) ? body.auditLogActionTypes : [];
    const yearClasses = Array.isArray(body && body.yearClasses) ? body.yearClasses : [];
    const selectedYear = Number(body && body.selectedYear);
    const bootstrapTeachers = body && body.teachers;
    const bootstrapStudents = body && body.students;
    const bootstrapAuditLogs = body && body.auditLogs;
    const bootstrapPool = body && body.pool;
    const bootstrapAudit = body && body.audit;
    const datalist = document.getElementById("auditActionTypes");
    datalist.innerHTML = "";
    actionTypes.forEach(type => {
      const option = document.createElement("option");
      option.value = type;
      datalist.appendChild(option);
    });
    cachedYears = years;
    syncYearOptions();
    const activeYearValue = getActiveYearValue();
    if (Number.isFinite(activeYearValue)) {
      queryYearInput.value = String(activeYearValue);
      dashboardYearValue = activeYearValue;
      if (poolYearFilter) {
        poolYearFilter.value = String(activeYearValue);
      }
      const assignmentYearFilter = document.getElementById("assignmentYearFilter");
      if (assignmentYearFilter) {
        assignmentYearFilter.value = String(activeYearValue);
      }
    } else if (Number.isFinite(selectedYear)) {
      queryYearInput.value = String(selectedYear);
      dashboardYearValue = selectedYear;
      if (poolYearFilter) {
        poolYearFilter.value = String(selectedYear);
      }
      const assignmentYearFilter = document.getElementById("assignmentYearFilter");
      if (assignmentYearFilter) {
        assignmentYearFilter.value = String(selectedYear);
      }
    } else if (years.length > 0 && Number.isFinite(Number(years[0].yearValue))) {
      queryYearInput.value = String(years[0].yearValue);
      dashboardYearValue = Number(years[0].yearValue);
      if (poolYearFilter) {
        poolYearFilter.value = String(years[0].yearValue);
      }
      const assignmentYearFilter = document.getElementById("assignmentYearFilter");
      if (assignmentYearFilter) {
        assignmentYearFilter.value = String(years[0].yearValue);
      }
    }
    if (queryYearInput.value) {
      if (yearClasses.length > 0) {
        cachedYearClasses = yearClasses;
        applyFilterAndRender();
        document.getElementById("classManagementYearFilter").value = String(getActiveYearValue() || Number(queryYearInput.value));
        syncAssignmentYearClassOptions();
        renderClassManagement();
        renderAssignmentManagement();
      } else {
        await refreshYearClasses();
      }
    }
    renderYearManagement();
    if (bootstrapTeachers && Array.isArray(bootstrapTeachers.items)) {
      teacherPoolItems = bootstrapTeachers.items;
      teacherTotalCount = Number(bootstrapTeachers.totalCount || bootstrapTeachers.items.length || 0);
      teacherPage = 1;
      renderTeacherPoolPage();
    }
    if (bootstrapStudents && Array.isArray(bootstrapStudents.items)) {
      studentPoolItems = bootstrapStudents.items;
      studentTotalCount = Number(bootstrapStudents.totalCount || bootstrapStudents.items.length || 0);
      studentPage = 1;
      renderStudentPoolPage();
    }
    if (bootstrapAuditLogs) {
      renderAuditLogsResponse(bootstrapAuditLogs);
    }
    if (bootstrapPool) {
      // no-op: teacher/student pool page size is fixed to 10
    }
    if (bootstrapAudit) {
      if (Number.isFinite(Number(bootstrapAudit.actorTeacherId))) {
        document.getElementById("auditActorTeacherId").value = String(bootstrapAudit.actorTeacherId);
      } else {
        document.getElementById("auditActorTeacherId").value = "";
      }
      if (typeof bootstrapAudit.actionType === "string") document.getElementById("auditActionType").value = bootstrapAudit.actionType;
      if (typeof bootstrapAudit.keyword === "string") document.getElementById("auditKeyword").value = bootstrapAudit.keyword;
      if (typeof bootstrapAudit.fromAt === "string") document.getElementById("auditFromAt").value = bootstrapAudit.fromAt;
      if (typeof bootstrapAudit.toAt === "string") document.getElementById("auditToAt").value = bootstrapAudit.toAt;
      if (Number.isFinite(Number(bootstrapAudit.limit))) document.getElementById("auditLimit").value = String(bootstrapAudit.limit);
      if (Number.isFinite(Number(bootstrapAudit.offset))) document.getElementById("auditOffset").value = String(bootstrapAudit.offset);
    }
    updateBootstrapLoadedHint(body);
    updateBootstrapContractHint(body);
    render(body);
    return body;
  }

  function buildTeacherPoolQuery(limit, offset) {
    const year = getPoolYearValue();
    if (!Number.isFinite(Number(year))) {
      throw new Error("관리할 연도를 먼저 선택해 주세요.");
    }
    const params = new URLSearchParams();
    params.set("year", String(year));
    params.set("activeOnly", "true");
    params.set("limit", String(limit));
    params.set("offset", String(offset));
    return params;
  }

  function buildStudentPoolQuery(limit, offset) {
    const year = getPoolYearValue();
    if (!Number.isFinite(Number(year))) {
      throw new Error("관리할 연도를 먼저 선택해 주세요.");
    }
    const params = new URLSearchParams();
    params.set("year", String(year));
    params.set("activeOnly", "true");
    params.set("limit", String(limit));
    params.set("offset", String(offset));
    return params;
  }

  function teacherPageSize() {
    return 10;
  }

  function studentPageSize() {
    return 10;
  }

  function renderTeacherPoolPage() {
    const totalPages = Math.max(1, Math.ceil(teacherTotalCount / teacherPageSize()));
    teacherPage = Math.min(Math.max(teacherPage, 1), totalPages);
    teacherPageInfo.textContent = `${teacherPage} / ${totalPages}`;
    renderTeacherManagement();
    teacherPool.innerHTML = "";
    teacherPoolItems.forEach(item => {
      const div = document.createElement("div");
      div.className = "pool-item";
      div.innerHTML = `
        <div>
          <label><input type="checkbox" class="teacherSelect" value="${item.teacherId}" ${selectedTeacherIds.has(item.teacherId) ? "checked" : ""} /> <strong>${item.teacherName}</strong> <small>#${item.teacherId} / ${roleLabel(item.role)}</small></label>
        </div>
      `;
      const checkbox = div.querySelector(".teacherSelect");
      checkbox.addEventListener("change", function () {
        if (checkbox.checked) {
          selectedTeacherIds.add(item.teacherId);
        } else {
          selectedTeacherIds.delete(item.teacherId);
        }
      });
      const btn = document.createElement("button");
      btn.textContent = "이 반에 배정";
      btn.addEventListener("click", async function () {
        try {
          const yearClassId = getTargetYearClassId();
          if (!confirmAction(`교사 ${item.teacherId}를 반 ${yearClassId}에 배정할까요?`)) return;
          await runUndoAware(
            {
              execute: () => postAssignTeachers(yearClassId, [item.teacherId])
            },
            {
              label: `교사 ${item.teacherId} 배정 취소`,
              execute: () => runAndRefresh(() => deleteAssignTeachers(yearClassId, [item.teacherId]))
            }
          );
        } catch (e) {
          render(e.message);
        }
      });
      div.appendChild(btn);
      teacherPool.appendChild(div);
    });
  }

  function renderStudentPoolPage() {
    const totalPages = Math.max(1, Math.ceil(studentTotalCount / studentPageSize()));
    studentPage = Math.min(Math.max(studentPage, 1), totalPages);
    studentPageInfo.textContent = `${studentPage} / ${totalPages}`;
    renderStudentManagement();
    studentPool.innerHTML = "";
    studentPoolItems.forEach(item => {
      const div = document.createElement("div");
      div.className = "pool-item";
      div.innerHTML = `
        <div>
          <label><input type="checkbox" class="studentSelect" value="${item.studentId}" ${selectedStudentIds.has(item.studentId) ? "checked" : ""} /> <strong>${item.studentName}</strong> <small>#${item.studentId} / ${formatSchoolGrade(item.schoolGrade)}</small></label>
        </div>
      `;
      const checkbox = div.querySelector(".studentSelect");
      checkbox.addEventListener("change", function () {
        if (checkbox.checked) {
          selectedStudentIds.add(item.studentId);
        } else {
          selectedStudentIds.delete(item.studentId);
        }
      });
      const btn = document.createElement("button");
      btn.textContent = "이 반에 배정";
      btn.addEventListener("click", async function () {
        try {
          const yearClassId = getTargetYearClassId();
          if (!confirmAction(`학생 ${item.studentId}를 반 ${yearClassId}에 배정할까요?`)) return;
          await runUndoAware(
            {
              execute: () => postAssignStudents(yearClassId, [item.studentId])
            },
            {
              label: `학생 ${item.studentId} 배정 취소`,
              execute: () => runAndRefresh(() => deleteAssignStudents(yearClassId, [item.studentId]))
            }
          );
        } catch (e) {
          render(e.message);
        }
      });
      div.appendChild(btn);
      studentPool.appendChild(div);
    });
  }

  function buildAuditParams(defaultLimit) {
    const limit = Number(document.getElementById("auditLimit").value || defaultLimit);
    const offset = Number(document.getElementById("auditOffset").value || 0);
    const actorTeacherIdRaw = document.getElementById("auditActorTeacherId").value.trim();
    const actionType = document.getElementById("auditActionType").value.trim();
    const keyword = document.getElementById("auditKeyword").value.trim();
    const fromAt = document.getElementById("auditFromAt").value.trim();
    const toAt = document.getElementById("auditToAt").value.trim();
    localStorage.setItem("qt_admin_audit_action_type", actionType);
    localStorage.setItem("qt_admin_audit_keyword", keyword);
    localStorage.setItem("qt_admin_audit_from_at", fromAt);
    localStorage.setItem("qt_admin_audit_to_at", toAt);

    const params = new URLSearchParams();
    params.set("limit", String(limit));
    params.set("offset", String(offset));
    if (actorTeacherIdRaw) params.set("actorTeacherId", actorTeacherIdRaw);
    if (actionType) params.set("actionType", actionType);
    if (keyword) params.set("keyword", keyword);
    if (fromAt) params.set("fromAt", fromAt);
    if (toAt) params.set("toAt", toAt);
    return params;
  }

  function toDatetimeLocalString(date) {
    const pad = (n) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function setAuditPreset(days) {
    const now = new Date();
    const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
    document.getElementById("auditToAt").value = toDatetimeLocalString(now);
    document.getElementById("auditFromAt").value = toDatetimeLocalString(from);
    setStatus(`로그 기간 preset 적용: ${days}일`);
  }

  async function loadTeacherPool(page) {
    teacherPage = Math.max(1, page || 1);
    const size = teacherPageSize();
    const offset = (teacherPage - 1) * size;
    const response = await api("/api/admin/teachers?" + buildTeacherPoolQuery(size, offset).toString(), { method: "GET" });
    teacherPoolItems = response.items || [];
    teacherTotalCount = response.totalCount || 0;
    renderTeacherPoolPage();
    render(response);
  }

  async function loadStudentPool(page) {
    studentPage = Math.max(1, page || 1);
    const size = studentPageSize();
    const offset = (studentPage - 1) * size;
    const response = await api("/api/admin/students?" + buildStudentPoolQuery(size, offset).toString(), { method: "GET" });
    studentPoolItems = response.items || [];
    studentTotalCount = response.totalCount || 0;
    renderStudentPoolPage();
    render(response);
  }

  async function attemptAdminLogin() {
    try {
      setAdminLoginMessage("");
      setStatus("로그인 중...");
      const loginId = document.getElementById("loginId").value.trim();
      const password = document.getElementById("password").value.trim();
      const response = await fetch("/api/teacher/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ loginId, password })
      });
      const text = await response.text();
      let body;
      try {
        body = text ? JSON.parse(text) : null;
      } catch (_) {
        body = text;
      }
      if (!response.ok) throw new Error(extractErrorMessage(body, "로그인에 실패했습니다."));
      if (!body || !["ADMIN", "PASTOR", "DIRECTOR"].includes(String(body.role || "").toUpperCase())) {
        throw new Error("관리자 권한이 없습니다.");
      }
      persistAdminToken(body.accessToken || "");
      setActiveTab("dashboard");
      setStatus("로그인 성공");
      setAdminAuthenticated(true, body.teacherName);
      setAdminLoginMessage("");
      await loadBootstrapAndDefaultYear();
      refreshVisibleTabData();
      render(body);
    } catch (e) {
      setAdminAuthenticated(false);
      setStatus("로그인 실패");
      setAdminLoginMessage(extractErrorMessage(e && e.message ? e.message : e, "로그인에 실패했습니다."));
      render("로그인 실패: " + extractErrorMessage(e && e.message ? e.message : e, "로그인에 실패했습니다."));
    }
  }

  document.getElementById("btnLogin").addEventListener("click", async function () {
    await attemptAdminLogin();
  });

  ["loginId", "password"].forEach(function (id) {
    const input = document.getElementById(id);
    if (!input) {
      return;
    }
    input.addEventListener("keydown", function (event) {
      if (event.key !== "Enter") {
        return;
      }
      event.preventDefault();
      attemptAdminLogin();
    });
  });

  if (btnAdminLogout) {
    btnAdminLogout.addEventListener("click", function () {
      clearTokenAndReset();
    });
  }

  document.getElementById("btnOpenCreateYear").addEventListener("click", openCreateYearEditor);
  document.getElementById("btnCloseYearEditor").addEventListener("click", function () {
    document.getElementById("yearEditorPanel").classList.add("hidden");
  });
  document.getElementById("btnOpenCreateClass").addEventListener("click", openCreateClassEditor);
  document.getElementById("btnCloseClassEditor").addEventListener("click", function () {
    document.getElementById("classEditorPanel").classList.add("hidden");
  });
  document.getElementById("btnOpenCreateTeacher").addEventListener("click", openCreateTeacherEditor);
  document.getElementById("btnCloseTeacherEditor").addEventListener("click", function () {
    document.getElementById("teacherEditorPanel").classList.add("hidden");
  });
  document.getElementById("btnOpenCreateStudent").addEventListener("click", openCreateStudentEditor);
  document.getElementById("btnCloseStudentEditor").addEventListener("click", function () {
    document.getElementById("studentEditorPanel").classList.add("hidden");
  });
  document.getElementById("classManagementYearFilter").addEventListener("change", async function () {
    try {
      await reloadClassManagementForSelectedYear();
    } catch (e) {
      render(e.message);
    }
  });
  if (poolYearFilter) {
    poolYearFilter.addEventListener("change", async function () {
      try {
        teacherPage = 1;
        studentPage = 1;
        if ((localStorage.getItem("qt_admin_pool_tab") || "teachers") === "teachers") {
          await loadTeacherPool(1);
        } else {
          await loadStudentPool(1);
        }
      } catch (e) {
        render(e.message);
      }
    });
  }
  const assignmentYearFilter = document.getElementById("assignmentYearFilter");
  if (assignmentYearFilter) {
    assignmentYearFilter.addEventListener("change", async function () {
      try {
        const year = getAssignmentYearValue();
        if (!Number.isFinite(Number(year))) {
          throw new Error("배정할 연도를 먼저 선택해 주세요.");
        }
        queryYearInput.value = String(year);
        await refreshYearClasses();
      } catch (e) {
        render(e.message);
      }
    });
  }
  document.getElementById("assignmentYearClassSelect").addEventListener("change", function () {
    const yearClassId = currentAssignmentYearClassId();
    if (yearClassId) {
      setAssignmentTarget(yearClassId);
    } else {
      renderAssignmentManagement();
    }
  });

  document.getElementById("btnSubmitYearEditor").addEventListener("click", async function () {
    try {
      const yearValue = Number(document.getElementById("yearEditorValue").value);
      const active = document.getElementById("yearEditorActive").checked;
      const existingYear = yearEditorState.mode === "edit"
        ? cachedYears.find(item => item.id === yearEditorState.yearId)
        : null;
      const openToStudents = existingYear ? !!existingYear.openToStudents : false;
      const openToTeachers = existingYear ? !!existingYear.openToTeachers : false;

      let body;
      if (yearEditorState.mode === "edit" && yearEditorState.yearId) {
        body = await api("/api/admin/years/" + yearEditorState.yearId, {
          method: "PATCH",
          body: JSON.stringify({ openToStudents, openToTeachers, active })
        });
      } else {
        body = await api("/api/admin/years", {
          method: "POST",
          body: JSON.stringify({ yearValue, openToStudents, openToTeachers, active })
        });
      }
      document.getElementById("yearEditorPanel").classList.add("hidden");
      render(body);
      cachedYears = await api("/api/admin/years", { method: "GET" });
      renderYearManagement();
      syncYearOptions();
      setYearClassTab("years");
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnSubmitClassEditor").addEventListener("click", async function () {
    try {
      const yearValue = Number(document.getElementById("classEditorYearValue").value);
      const className = document.getElementById("classEditorName").value.trim();
      const sortOrder = Number(document.getElementById("classEditorSortOrder").value);
      const active = document.getElementById("classEditorActive").checked;

      if (!Number.isFinite(yearValue)) {
        throw new Error("연도를 선택해야 합니다.");
      }
      if (!className) {
        throw new Error("반 이름은 비어 있을 수 없습니다.");
      }
      if (!Number.isFinite(sortOrder)) {
        throw new Error("정렬 순서를 숫자로 입력해야 합니다.");
      }

      let body;
      if (classEditorState.mode === "edit" && classEditorState.yearClassId) {
        body = await api("/api/admin/year-classes/" + classEditorState.yearClassId, {
          method: "PATCH",
          body: JSON.stringify({ className, sortOrder, active })
        });
      } else {
        body = await api("/api/admin/year-classes", {
          method: "POST",
          body: JSON.stringify({ yearValue, className, sortOrder, active })
        });
      }
      document.getElementById("classEditorPanel").classList.add("hidden");
      render(body);
      queryYearInput.value = String(yearValue);
      document.getElementById("classManagementYearFilter").value = String(yearValue);
      await refreshYearClasses();
      setYearClassTab("classes");
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnSubmitTeacherEditor").addEventListener("click", async function () {
    try {
      const year = getPoolYearValue();
      if (!Number.isFinite(Number(year))) {
        throw new Error("관리할 연도를 먼저 선택해 주세요.");
      }
      const createPayload = {
        loginId: document.getElementById("teacherEditorLoginId").value.trim(),
        password: document.getElementById("teacherEditorPassword").value,
        teacherName: document.getElementById("teacherEditorName").value.trim(),
        contactNumber: document.getElementById("teacherEditorContactNumber").value.trim(),
        birthDate: normalizeBirthDateInput(document.getElementById("teacherEditorBirthDate").value),
        role: document.getElementById("teacherEditorRole").value,
        active: document.getElementById("teacherEditorActive").checked
      };

      let body;
      if (teacherEditorState.mode === "edit" && teacherEditorState.teacherId) {
        const updatePayload = {
          teacherName: createPayload.teacherName,
          contactNumber: createPayload.contactNumber,
          birthDate: createPayload.birthDate,
          role: createPayload.role,
          active: createPayload.active,
          password: createPayload.password
        };
        body = await api(`/api/admin/teachers/${teacherEditorState.teacherId}?year=${year}`, {
          method: "PATCH",
          body: JSON.stringify(updatePayload)
        });
      } else {
        body = await api(`/api/admin/teachers?year=${year}`, {
          method: "POST",
          body: JSON.stringify(createPayload)
        });
      }
      document.getElementById("teacherEditorPanel").classList.add("hidden");
      render(body);
      await loadTeacherPool(teacherPage);
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnSubmitStudentEditor").addEventListener("click", async function () {
    try {
      const year = getPoolYearValue();
      if (!Number.isFinite(Number(year))) {
        throw new Error("관리할 연도를 먼저 선택해 주세요.");
      }
      const payload = {
        studentName: document.getElementById("studentEditorName").value.trim(),
        schoolGrade: String(document.getElementById("studentEditorSchoolGrade").value || "").trim() || null,
        contactNumber: document.getElementById("studentEditorContactNumber").value.trim(),
        birthDate: normalizeBirthDateInput(document.getElementById("studentEditorBirthDate").value),
        active: document.getElementById("studentEditorActive").checked
      };

      let body;
      if (studentEditorState.mode === "edit" && studentEditorState.studentId) {
        body = await api(`/api/admin/students/${studentEditorState.studentId}?year=${year}`, {
          method: "PATCH",
          body: JSON.stringify(payload)
        });
      } else {
        body = await api(`/api/admin/students?year=${year}`, {
          method: "POST",
          body: JSON.stringify(payload)
        });
      }
      document.getElementById("studentEditorPanel").classList.add("hidden");
      render(body);
      await loadStudentPool(studentPage);
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnBootstrap").addEventListener("click", async function () {
    try {
      await loadBootstrapAndDefaultYear();
    } catch (e) {
      handleBootstrapFailure("", e);
    }
  });

  document.getElementById("btnGetYearClasses").addEventListener("click", async function () {
    try {
      render(await refreshYearClasses());
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnApplyFilter").addEventListener("click", function () {
    applyFilterAndRender();
  });

  document.getElementById("btnCreateYear").addEventListener("click", async function () {
    try {
      render(await api("/api/admin/years", {
        method: "POST",
        body: JSON.stringify({
          yearValue: Number(document.getElementById("createYearValue").value),
          openToStudents: document.getElementById("createOpenStudents").checked,
          openToTeachers: document.getElementById("createOpenTeachers").checked,
          active: document.getElementById("createYearActive").checked
        })
      }));
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnUpdateYear").addEventListener("click", async function () {
    try {
      const yearId = Number(document.getElementById("updateYearId").value);
      render(await api("/api/admin/years/" + yearId, {
        method: "PATCH",
        body: JSON.stringify({
          openToStudents: document.getElementById("updateOpenStudents").checked,
          openToTeachers: document.getElementById("updateOpenTeachers").checked,
          active: document.getElementById("updateYearActive").checked
        })
      }));
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnCreateYearClass").addEventListener("click", async function () {
    try {
      render(await api("/api/admin/year-classes", {
        method: "POST",
        body: JSON.stringify({
          yearValue: Number(document.getElementById("createClassYearValue").value),
          className: document.getElementById("createClassName").value.trim(),
          sortOrder: Number(document.getElementById("createClassSortOrder").value),
          active: document.getElementById("createClassActive").checked
        })
      }));
      await refreshYearClasses();
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnUpdateYearClass").addEventListener("click", async function () {
    try {
      const yearClassId = Number(document.getElementById("editYearClassId").value);
      render(await api("/api/admin/year-classes/" + yearClassId, {
        method: "PATCH",
        body: JSON.stringify({
          className: document.getElementById("editClassName").value.trim(),
          sortOrder: Number(document.getElementById("editSortOrder").value),
          active: document.getElementById("editClassActive").checked
        })
      }));
      await refreshYearClasses();
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignTeachers").addEventListener("click", async function () {
    try {
      const yearClassId = Number(document.getElementById("assignTeacherYearClassId").value);
      const teacherIds = parseIds(document.getElementById("assignTeacherIds").value);
      ensureNonEmptyIds(teacherIds, "teacherIds");
      if (!confirmAction(`교사 ${teacherIds.join(", ")}를 반 ${yearClassId}에 배정할까요?`)) return;
      await runUndoAware(
        {
          execute: () => postAssignTeachers(yearClassId, teacherIds)
        },
        {
          label: `교사 ${teacherIds.join(", ")} 배정 취소`,
          execute: () => runAndRefresh(() => deleteAssignTeachers(yearClassId, teacherIds))
        }
      );
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnUnassignTeachers").addEventListener("click", async function () {
    try {
      const yearClassId = Number(document.getElementById("assignTeacherYearClassId").value);
      const teacherIds = parseIds(document.getElementById("unassignTeacherIds").value);
      ensureNonEmptyIds(teacherIds, "teacherIds");
      if (!confirmAction(`교사 ${teacherIds.join(", ")}를 반 ${yearClassId}에서 해제할까요?`)) return;
      await runUndoAware(
        {
          execute: () => deleteAssignTeachers(yearClassId, teacherIds)
        },
        {
          label: `교사 ${teacherIds.join(", ")} 재배정`,
          execute: () => runAndRefresh(() => postAssignTeachers(yearClassId, teacherIds))
        }
      );
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignStudents").addEventListener("click", async function () {
    try {
      const yearClassId = Number(document.getElementById("assignStudentYearClassId").value);
      const studentIds = parseIds(document.getElementById("assignStudentIds").value);
      ensureNonEmptyIds(studentIds, "studentIds");
      if (!confirmAction(`학생 ${studentIds.join(", ")}를 반 ${yearClassId}에 배정할까요?`)) return;
      await runUndoAware(
        {
          execute: () => postAssignStudents(yearClassId, studentIds)
        },
        {
          label: `학생 ${studentIds.join(", ")} 배정 취소`,
          execute: () => runAndRefresh(() => deleteAssignStudents(yearClassId, studentIds))
        }
      );
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnUnassignStudents").addEventListener("click", async function () {
    try {
      const yearClassId = Number(document.getElementById("assignStudentYearClassId").value);
      const studentIds = parseIds(document.getElementById("unassignStudentIds").value);
      ensureNonEmptyIds(studentIds, "studentIds");
      if (!confirmAction(`학생 ${studentIds.join(", ")}를 반 ${yearClassId}에서 해제할까요?`)) return;
      await runUndoAware(
        {
          execute: () => deleteAssignStudents(yearClassId, studentIds)
        },
        {
          label: `학생 ${studentIds.join(", ")} 재배정`,
          execute: () => runAndRefresh(() => postAssignStudents(yearClassId, studentIds))
        }
      );
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnMoveStudents").addEventListener("click", async function () {
    try {
      const targetYearClassId = Number(document.getElementById("moveTargetYearClassId").value);
      const studentIds = parseIds(document.getElementById("moveStudentIds").value);
      ensureNonEmptyIds(studentIds, "studentIds");
      const sourceMap = studentSourceMap();
      const groupedSource = new Map();
      studentIds.forEach(studentId => {
        const src = sourceMap.get(studentId);
        if (!src) return;
        if (!groupedSource.has(src)) groupedSource.set(src, []);
        groupedSource.get(src).push(studentId);
      });

      if (!confirmAction(`학생 ${studentIds.join(", ")}를 반 ${targetYearClassId}로 이동할까요?`)) return;
      await runUndoAware(
        {
          execute: () => moveStudents(targetYearClassId, studentIds)
        },
        {
          label: `학생 ${studentIds.join(", ")} 이동 원복`,
          execute: async () => {
            for (const [sourceYearClassId, ids] of groupedSource.entries()) {
              await moveStudents(sourceYearClassId, ids);
            }
            await refreshYearClasses();
          }
        }
      );
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnGetAuditLogs").addEventListener("click", async function () {
    try {
      const params = buildAuditParams(100);

      const body = await api("/api/admin/audit-logs?" + params.toString(), { method: "GET" });
      renderAuditLogsResponse(body);
      render(body);
    } catch (e) {
      auditResult.innerHTML = `<div class="table-empty">${escapeHtml(e.message || "운영 로그 조회에 실패했습니다.")}</div>`;
      render(e.message);
    }
  });

  document.getElementById("btnDownloadAuditCsv").addEventListener("click", function () {
    try {
      const token = tokenOrThrow();
      const params = buildAuditParams(1000);

      const url = "/api/admin/audit-logs.csv?" + params.toString();
      fetch(url, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}` }
      }).then(async response => {
        if (!response.ok) throw new Error(await response.text());
        return response.blob();
      }).then(blob => {
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = "audit-logs.csv";
        a.click();
        URL.revokeObjectURL(a.href);
        setStatus("CSV 다운로드 완료");
      }).catch(e => {
        render(e.message);
      });
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnLoadActionTypes").addEventListener("click", async function () {
    try {
      const actionTypes = await api("/api/admin/audit-logs/action-types", { method: "GET" });
      const datalist = document.getElementById("auditActionTypes");
      datalist.innerHTML = "";
      actionTypes.forEach(type => {
        const option = document.createElement("option");
        option.value = type;
        datalist.appendChild(option);
      });
      render(actionTypes);
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnPresetToday").addEventListener("click", function () {
    setAuditPreset(1);
  });

  document.getElementById("btnPreset7d").addEventListener("click", function () {
    setAuditPreset(7);
  });

  document.getElementById("btnPreset30d").addEventListener("click", function () {
    setAuditPreset(30);
  });

  document.getElementById("btnTeacherPrev").addEventListener("click", async function () {
    try {
      if (teacherPage > 1) {
        await loadTeacherPool(teacherPage - 1);
      }
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnTeacherNext").addEventListener("click", async function () {
    try {
      const totalPages = Math.max(1, Math.ceil(teacherTotalCount / teacherPageSize()));
      if (teacherPage < totalPages) {
        await loadTeacherPool(teacherPage + 1);
      }
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnStudentPrev").addEventListener("click", async function () {
    try {
      if (studentPage > 1) {
        await loadStudentPool(studentPage - 1);
      }
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnStudentNext").addEventListener("click", async function () {
    try {
      const totalPages = Math.max(1, Math.ceil(studentTotalCount / studentPageSize()));
      if (studentPage < totalPages) {
        await loadStudentPool(studentPage + 1);
      }
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignmentAssignTeachers").addEventListener("click", async function () {
    try {
      const yearClassId = getTargetYearClassId();
      const teacherIds = Array.from(selectedAssignmentTeacherIds.values());
      ensureNonEmptyIds(teacherIds, "선택된 teacherIds");
      if (!confirmAction(`선택한 교사 ${teacherIds.length}명을 배정할까요?`)) return;
      await runUndoAware(
        {
          execute: () => postAssignTeachers(yearClassId, teacherIds)
        },
        {
          label: "선택 교사 배정 취소",
          execute: () => runAndRefresh(() => deleteAssignTeachers(yearClassId, teacherIds))
        }
      );
      selectedAssignmentTeacherIds.clear();
      renderAssignmentManagement();
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignmentUnassignTeachers").addEventListener("click", async function () {
    try {
      const yearClassId = getTargetYearClassId();
      const teacherIds = Array.from(selectedAssignmentTeacherIds.values());
      ensureNonEmptyIds(teacherIds, "선택된 teacherIds");
      if (!confirmAction(`선택한 교사 ${teacherIds.length}명을 해제할까요?`)) return;
      await runUndoAware(
        {
          execute: () => deleteAssignTeachers(yearClassId, teacherIds)
        },
        {
          label: "선택 교사 재배정",
          execute: () => runAndRefresh(() => postAssignTeachers(yearClassId, teacherIds))
        }
      );
      selectedAssignmentTeacherIds.clear();
      renderAssignmentManagement();
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignmentAssignStudents").addEventListener("click", async function () {
    try {
      const yearClassId = getTargetYearClassId();
      const studentIds = Array.from(selectedAssignmentStudentIds.values());
      ensureNonEmptyIds(studentIds, "선택된 studentIds");
      if (!confirmAction(`선택한 학생 ${studentIds.length}명을 배정할까요?`)) return;
      await runUndoAware(
        {
          execute: () => postAssignStudents(yearClassId, studentIds)
        },
        {
          label: "선택 학생 배정 취소",
          execute: () => runAndRefresh(() => deleteAssignStudents(yearClassId, studentIds))
        }
      );
      selectedAssignmentStudentIds.clear();
      renderAssignmentManagement();
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignmentUnassignStudents").addEventListener("click", async function () {
    try {
      const yearClassId = getTargetYearClassId();
      const studentIds = Array.from(selectedAssignmentStudentIds.values());
      ensureNonEmptyIds(studentIds, "선택된 studentIds");
      if (!confirmAction(`선택한 학생 ${studentIds.length}명을 해제할까요?`)) return;
      await runUndoAware(
        {
          execute: () => deleteAssignStudents(yearClassId, studentIds)
        },
        {
          label: "선택 학생 재배정",
          execute: () => runAndRefresh(() => postAssignStudents(yearClassId, studentIds))
        }
      );
      selectedAssignmentStudentIds.clear();
      renderAssignmentManagement();
    } catch (e) {
      render(e.message);
    }
  });

  updateBootstrapModeHint();

  if (!adminToken) {
    setAdminAuthenticated(false);
    setAdminLoginMessage("관리자 페이지는 로그인 후 사용할 수 있습니다.");
    setStatus("로그인 필요");
    render("관리자 페이지는 로그인 후 사용할 수 있습니다.");
    return;
  }

  if (adminToken) {
    validateAdminSession()
      .then(function (me) {
        if (!me) {
          return;
        }
        return loadBootstrapAndDefaultYear().then(function () {
          refreshVisibleTabData();
        });
      })
      .catch(function (e) {
        handleBootstrapFailure("자동 초기화 실패", e);
      });
  }

})();
