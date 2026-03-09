(function () {
  const result = document.getElementById("result");
  const tokenInput = document.getElementById("token");
  const statusLine = document.getElementById("statusLine");
  const yearClassList = document.getElementById("yearClassList");
  const queryYearInput = document.getElementById("queryYear");
  const auditResult = document.getElementById("auditResult");
  const teacherPool = document.getElementById("teacherPool");
  const studentPool = document.getElementById("studentPool");
  const teacherPageInfo = document.getElementById("teacherPageInfo");
  const studentPageInfo = document.getElementById("studentPageInfo");
  const undoHint = document.getElementById("undoHint");
  const opsStatusCard = document.getElementById("opsStatusCard");
  const opsUndoCard = document.getElementById("opsUndoCard");
  const opsContractCard = document.getElementById("opsContractCard");
  const opsRequestCard = document.getElementById("opsRequestCard");

  let cachedYearClasses = [];
  let teacherPoolItems = [];
  let studentPoolItems = [];
  let teacherTotalCount = 0;
  let studentTotalCount = 0;
  let teacherPage = 1;
  let studentPage = 1;
  const selectedTeacherIds = new Set();
  const selectedStudentIds = new Set();
  let lastUndoAction = null;
  const bootstrapUiState = {
    mode: "전체",
    contract: "-",
    loaded: "없음",
    request: "-"
  };

  const savedToken = localStorage.getItem("qt_admin_token");
  if (savedToken) tokenInput.value = savedToken;

  const savedPoolKeyword = localStorage.getItem("qt_admin_pool_keyword");
  const savedPoolActiveOnly = localStorage.getItem("qt_admin_pool_active_only");
  const savedPoolPageSize = localStorage.getItem("qt_admin_pool_page_size");
  const savedAuditActionType = localStorage.getItem("qt_admin_audit_action_type");
  const savedAuditKeyword = localStorage.getItem("qt_admin_audit_keyword");
  const savedAuditFromAt = localStorage.getItem("qt_admin_audit_from_at");
  const savedAuditToAt = localStorage.getItem("qt_admin_audit_to_at");
  const savedBootstrapIncludeYearClasses = localStorage.getItem("qt_admin_bootstrap_include_year_classes");
  const savedBootstrapIncludeActionTypes = localStorage.getItem("qt_admin_bootstrap_include_action_types");
  const savedBootstrapIncludePools = localStorage.getItem("qt_admin_bootstrap_include_pools");
  const savedBootstrapIncludeAuditLogs = localStorage.getItem("qt_admin_bootstrap_include_audit_logs");

  if (savedPoolKeyword) document.getElementById("poolKeyword").value = savedPoolKeyword;
  if (savedPoolActiveOnly) document.getElementById("poolActiveOnly").checked = savedPoolActiveOnly === "true";
  if (savedPoolPageSize) document.getElementById("poolPageSize").value = savedPoolPageSize;
  if (savedAuditActionType) document.getElementById("auditActionType").value = savedAuditActionType;
  if (savedAuditKeyword) document.getElementById("auditKeyword").value = savedAuditKeyword;
  if (savedAuditFromAt) document.getElementById("auditFromAt").value = savedAuditFromAt;
  if (savedAuditToAt) document.getElementById("auditToAt").value = savedAuditToAt;
  if (savedBootstrapIncludeYearClasses) document.getElementById("bootstrapIncludeYearClasses").checked = savedBootstrapIncludeYearClasses === "true";
  if (savedBootstrapIncludeActionTypes) document.getElementById("bootstrapIncludeActionTypes").checked = savedBootstrapIncludeActionTypes === "true";
  if (savedBootstrapIncludePools) document.getElementById("bootstrapIncludePools").checked = savedBootstrapIncludePools === "true";
  if (savedBootstrapIncludeAuditLogs) document.getElementById("bootstrapIncludeAuditLogs").checked = savedBootstrapIncludeAuditLogs === "true";

  function render(data) {
    result.textContent = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  }

  function setStatus(message) {
    statusLine.textContent = message;
    if (opsStatusCard) opsStatusCard.textContent = message;
  }

  function setUndoAction(action) {
    lastUndoAction = action;
    const undoMessage = action ? `되돌리기 가능: ${action.label}` : "되돌릴 작업 없음";
    undoHint.textContent = undoMessage;
    if (opsUndoCard) opsUndoCard.textContent = undoMessage;
  }

  function resetUiState() {
    cachedYearClasses = [];
    teacherPoolItems = [];
    studentPoolItems = [];
    teacherTotalCount = 0;
    studentTotalCount = 0;
    teacherPage = 1;
    studentPage = 1;
    selectedTeacherIds.clear();
    selectedStudentIds.clear();
    setUndoAction(null);
    yearClassList.innerHTML = "";
    teacherPool.innerHTML = "";
    studentPool.innerHTML = "";
    teacherPageInfo.textContent = "0 / 0";
    studentPageInfo.textContent = "0 / 0";
    auditResult.textContent = "";
    bootstrapUiState.loaded = "없음";
    bootstrapUiState.contract = "-";
    bootstrapUiState.request = "-";
    updateBootstrapSummaryHint();
  }

  function clearTokenAndReset(message) {
    localStorage.removeItem("qt_admin_token");
    tokenInput.value = "";
    resetUiState();
    setStatus("토큰 만료/무효");
    render(message);
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
      if (tokenInput.value.trim()) {
        loadBootstrapAndDefaultYear().catch(function (e) {
          handleBootstrapFailure("include 옵션 적용 실패", e);
        });
      }
    });
  }

  function updateBootstrapModeHint() {
    const includeYearClasses = document.getElementById("bootstrapIncludeYearClasses").checked;
    const includeActionTypes = document.getElementById("bootstrapIncludeActionTypes").checked;
    const includePools = document.getElementById("bootstrapIncludePools").checked;
    const includeAuditLogs = document.getElementById("bootstrapIncludeAuditLogs").checked;
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
    document.getElementById("bootstrapSummaryHint").textContent = summaryText;
    if (opsContractCard) opsContractCard.textContent = bootstrapUiState.contract;
    if (opsRequestCard) opsRequestCard.textContent = bootstrapUiState.request;
  }

  function applyBootstrapIncludePreset(preset) {
    const includeYearClassesEl = document.getElementById("bootstrapIncludeYearClasses");
    const includeActionTypesEl = document.getElementById("bootstrapIncludeActionTypes");
    const includePoolsEl = document.getElementById("bootstrapIncludePools");
    const includeAuditLogsEl = document.getElementById("bootstrapIncludeAuditLogs");
    includeYearClassesEl.checked = !!preset.includeYearClasses;
    includeActionTypesEl.checked = !!preset.includeActionTypes;
    includePoolsEl.checked = !!preset.includePools;
    includeAuditLogsEl.checked = !!preset.includeAuditLogs;
    localStorage.setItem("qt_admin_bootstrap_include_year_classes", String(includeYearClassesEl.checked));
    localStorage.setItem("qt_admin_bootstrap_include_action_types", String(includeActionTypesEl.checked));
    localStorage.setItem("qt_admin_bootstrap_include_pools", String(includePoolsEl.checked));
    localStorage.setItem("qt_admin_bootstrap_include_audit_logs", String(includeAuditLogsEl.checked));
    updateBootstrapModeHint();
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
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
    const token = tokenInput.value.trim();
    if (!token) throw new Error("토큰이 비어 있습니다.");
    return token;
  }

  function getTargetYearClassId() {
    const value = Number(document.getElementById("assignTeacherYearClassId").value || document.getElementById("assignStudentYearClassId").value);
    if (!Number.isFinite(value)) throw new Error("배정 대상 yearClassId를 입력하세요.");
    return value;
  }

  function setAssignmentTarget(yearClassId) {
    document.getElementById("assignTeacherYearClassId").value = String(yearClassId);
    document.getElementById("assignStudentYearClassId").value = String(yearClassId);
    document.getElementById("queryYearClassId").value = String(yearClassId);
    setStatus("작업 대상 반 설정: " + yearClassId);
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
      throw new Error(typeof body === "string" ? body : JSON.stringify(body));
    }
    setStatus("요청 성공");
    return body;
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

  function applyFilterAndRender() {
    const classKeyword = document.getElementById("filterClassName").value.trim();
    const teacherKeyword = document.getElementById("filterTeacherName").value.trim();
    const studentKeyword = document.getElementById("filterStudentName").value.trim();
    const activeOnly = document.getElementById("filterActiveOnly").checked;

    const filtered = cachedYearClasses.filter(item => {
      if (activeOnly && !item.active) return false;
      if (!includesText(item.className, classKeyword)) return false;
      if (teacherKeyword && !(item.teachers || []).some(t => includesText(t.teacherName, teacherKeyword))) return false;
      if (studentKeyword && !(item.students || []).some(s => includesText(s.studentName, studentKeyword))) return false;
      return true;
    });

    yearClassList.innerHTML = "";
    filtered.forEach(item => {
      const card = document.createElement("article");
      card.className = "card";
      card.innerHTML = `
        <h3>${escapeHtml(item.className)} (#${item.yearClassId})</h3>
        <p class="meta">연도 ${item.yearValue} / 정렬 ${item.sortOrder} / ${item.active ? "활성" : "비활성"}</p>
        <div class="card-actions">
          <button class="ghost btnSetTarget" data-year-class-id="${item.yearClassId}">이 반을 작업 대상으로 설정</button>
        </div>
        <div class="chip-row">
          <label>반명 <input class="inline-input className" type="text" value="${escapeHtml(item.className)}" /></label>
          <label>정렬 <input class="inline-input sortOrder" type="number" value="${item.sortOrder}" /></label>
          <label><input class="activeFlag" type="checkbox" ${item.active ? "checked" : ""}/> 활성</label>
          <button class="btnSaveClass" data-year-class-id="${item.yearClassId}">반 정보 저장</button>
        </div>
        <strong>교사 (${(item.teachers || []).length})</strong>
        <ul class="mini-list">${(item.teachers || []).map(t => `<li>${escapeHtml(t.teacherName)} (${t.teacherId}) <button class="danger btnRemoveTeacher" data-year-class-id="${item.yearClassId}" data-teacher-id="${t.teacherId}">해제</button></li>`).join("") || "<li>없음</li>"}</ul>
        <div class="chip-row">
          <label>교사 ID들 <input class="inline-input teacherIdsInput" type="text" placeholder="1,2,3" /></label>
          <button class="ghost btnBulkRemoveTeachers" data-year-class-id="${item.yearClassId}">교사 일괄 해제</button>
        </div>
        <strong>학생 (${(item.students || []).length})</strong>
        <ul class="mini-list">${(item.students || []).map(s => `<li>${escapeHtml(s.studentName)} (${s.studentId}) <button class="danger btnRemoveStudent" data-year-class-id="${item.yearClassId}" data-student-id="${s.studentId}">해제</button></li>`).join("") || "<li>없음</li>"}</ul>
        <div class="chip-row">
          <label>학생 ID들 <input class="inline-input studentIdsInput" type="text" placeholder="11,12,13" /></label>
          <button class="ghost btnBulkRemoveStudents" data-year-class-id="${item.yearClassId}">학생 일괄 해제</button>
        </div>
        <div class="chip-row">
          <label>이동 대상 반ID <input class="inline-input moveTargetInput" type="number" placeholder="2" /></label>
          <label>학생 ID들 <input class="inline-input moveStudentIdsInput" type="text" placeholder="11,12,13" /></label>
          <button class="btnMoveFromCard">학생 이동</button>
        </div>
      `;
      yearClassList.appendChild(card);

      card.querySelector(".btnSetTarget").addEventListener("click", function () {
        setAssignmentTarget(item.yearClassId);
      });

      card.querySelector(".btnSaveClass").addEventListener("click", async function () {
        try {
          const className = card.querySelector(".className").value.trim();
          const sortOrder = Number(card.querySelector(".sortOrder").value);
          const active = card.querySelector(".activeFlag").checked;
          const body = await api("/api/admin/year-classes/" + item.yearClassId, {
            method: "PATCH",
            body: JSON.stringify({ className, sortOrder, active })
          });
          render(body);
          await refreshYearClasses();
        } catch (e) {
          render(e.message);
        }
      });

      card.querySelectorAll(".btnRemoveTeacher").forEach(btn => {
        btn.addEventListener("click", async function () {
          try {
            const teacherId = Number(btn.dataset.teacherId);
            if (!confirmAction(`교사 ${teacherId}를 반 ${item.yearClassId}에서 해제할까요?`)) return;
            await runUndoAware(
              {
                execute: () => deleteAssignTeachers(item.yearClassId, [teacherId])
              },
              {
                label: `교사 ${teacherId} 재배정`,
                execute: () => runAndRefresh(() => postAssignTeachers(item.yearClassId, [teacherId]))
              }
            );
          } catch (e) {
            render(e.message);
          }
        });
      });

      card.querySelector(".btnBulkRemoveTeachers").addEventListener("click", async function () {
        try {
          const teacherIds = parseIds(card.querySelector(".teacherIdsInput").value);
          ensureNonEmptyIds(teacherIds, "teacherIds");
          if (!confirmAction(`교사 ${teacherIds.join(", ")}를 반 ${item.yearClassId}에서 해제할까요?`)) return;
          await runUndoAware(
            {
              execute: () => deleteAssignTeachers(item.yearClassId, teacherIds)
            },
            {
              label: `교사 ${teacherIds.join(", ")} 재배정`,
              execute: () => runAndRefresh(() => postAssignTeachers(item.yearClassId, teacherIds))
            }
          );
        } catch (e) {
          render(e.message);
        }
      });

      card.querySelectorAll(".btnRemoveStudent").forEach(btn => {
        btn.addEventListener("click", async function () {
          try {
            const studentId = Number(btn.dataset.studentId);
            if (!confirmAction(`학생 ${studentId}를 반 ${item.yearClassId}에서 해제할까요?`)) return;
            await runUndoAware(
              {
                execute: () => deleteAssignStudents(item.yearClassId, [studentId])
              },
              {
                label: `학생 ${studentId} 재배정`,
                execute: () => runAndRefresh(() => postAssignStudents(item.yearClassId, [studentId]))
              }
            );
          } catch (e) {
            render(e.message);
          }
        });
      });

      card.querySelector(".btnBulkRemoveStudents").addEventListener("click", async function () {
        try {
          const studentIds = parseIds(card.querySelector(".studentIdsInput").value);
          ensureNonEmptyIds(studentIds, "studentIds");
          if (!confirmAction(`학생 ${studentIds.join(", ")}를 반 ${item.yearClassId}에서 해제할까요?`)) return;
          await runUndoAware(
            {
              execute: () => deleteAssignStudents(item.yearClassId, studentIds)
            },
            {
              label: `학생 ${studentIds.join(", ")} 재배정`,
              execute: () => runAndRefresh(() => postAssignStudents(item.yearClassId, studentIds))
            }
          );
        } catch (e) {
          render(e.message);
        }
      });

      card.querySelector(".btnMoveFromCard").addEventListener("click", async function () {
        try {
          const targetYearClassId = Number(card.querySelector(".moveTargetInput").value);
          const studentIds = parseIds(card.querySelector(".moveStudentIdsInput").value);
          ensureNonEmptyIds(studentIds, "studentIds");
          if (!confirmAction(`학생 ${studentIds.join(", ")}를 반 ${targetYearClassId}로 이동할까요?`)) return;
          await runUndoAware(
            {
              execute: () => moveStudents(targetYearClassId, studentIds)
            },
            {
              label: `학생 ${studentIds.join(", ")} 원복`,
              execute: () => runAndRefresh(() => moveStudents(item.yearClassId, studentIds))
            }
          );
        } catch (e) {
          render(e.message);
        }
      });
    });

    setStatus(`필터 적용: ${filtered.length}개 반 표시`);
  }

  async function refreshYearClasses() {
    const year = Number(queryYearInput.value);
    if (!Number.isFinite(year)) throw new Error("조회 연도를 입력하세요.");
    const body = await api("/api/admin/year-classes?year=" + year, { method: "GET" });
    cachedYearClasses = Array.isArray(body) ? body : [];
    applyFilterAndRender();
    return body;
  }

  async function loadBootstrapAndDefaultYear() {
    const requestedYear = Number(queryYearInput.value);
    const bootstrapParams = new URLSearchParams();
    if (Number.isFinite(requestedYear)) {
      bootstrapParams.set("year", String(requestedYear));
    }
    const keyword = document.getElementById("poolKeyword").value.trim();
    const activeOnly = document.getElementById("poolActiveOnly").checked;
    const limit = pageSize();
    if (keyword) bootstrapParams.set("poolKeyword", keyword);
    bootstrapParams.set("poolActiveOnly", String(activeOnly));
    bootstrapParams.set("poolLimit", String(limit));
    const auditLimit = Number(document.getElementById("auditLimit").value || 100);
    const auditOffset = Number(document.getElementById("auditOffset").value || 0);
    const auditActorTeacherIdRaw = document.getElementById("auditActorTeacherId").value.trim();
    const auditActorTeacherId = Number(auditActorTeacherIdRaw);
    const auditActionType = document.getElementById("auditActionType").value.trim();
    const auditKeyword = document.getElementById("auditKeyword").value.trim();
    const auditFromAt = document.getElementById("auditFromAt").value.trim();
    const auditToAt = document.getElementById("auditToAt").value.trim();
    const includeYearClasses = document.getElementById("bootstrapIncludeYearClasses").checked;
    const includeActionTypes = document.getElementById("bootstrapIncludeActionTypes").checked;
    const includePools = document.getElementById("bootstrapIncludePools").checked;
    const includeAuditLogs = document.getElementById("bootstrapIncludeAuditLogs").checked;
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
    if (Number.isFinite(selectedYear)) {
      queryYearInput.value = String(selectedYear);
    } else if (years.length > 0 && Number.isFinite(Number(years[0].yearValue))) {
      queryYearInput.value = String(years[0].yearValue);
    }
    if (queryYearInput.value) {
      if (yearClasses.length > 0) {
        cachedYearClasses = yearClasses;
        applyFilterAndRender();
      } else {
        await refreshYearClasses();
      }
    }
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
      auditResult.textContent = JSON.stringify(bootstrapAuditLogs, null, 2);
    }
    if (bootstrapPool) {
      if (typeof bootstrapPool.keyword === "string") document.getElementById("poolKeyword").value = bootstrapPool.keyword;
      if (typeof bootstrapPool.activeOnly === "boolean") document.getElementById("poolActiveOnly").checked = bootstrapPool.activeOnly;
      if (Number.isFinite(Number(bootstrapPool.limit))) document.getElementById("poolPageSize").value = String(bootstrapPool.limit);
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

  function buildPoolQuery(limit, offset) {
    const keyword = document.getElementById("poolKeyword").value.trim();
    const activeOnly = document.getElementById("poolActiveOnly").checked;
    localStorage.setItem("qt_admin_pool_keyword", keyword);
    localStorage.setItem("qt_admin_pool_active_only", String(activeOnly));
    localStorage.setItem("qt_admin_pool_page_size", String(pageSize()));
    const params = new URLSearchParams();
    params.set("activeOnly", String(activeOnly));
    params.set("limit", String(limit));
    params.set("offset", String(offset));
    if (keyword) params.set("keyword", keyword);
    return params;
  }

  function pageSize() {
    const value = Number(document.getElementById("poolPageSize").value || 20);
    return Math.min(Math.max(value, 5), 200);
  }

  function renderTeacherPoolPage() {
    const totalPages = Math.max(1, Math.ceil(teacherTotalCount / pageSize()));
    teacherPage = Math.min(Math.max(teacherPage, 1), totalPages);
    teacherPageInfo.textContent = `${teacherPage} / ${totalPages} (${teacherTotalCount})`;
    teacherPool.innerHTML = "";
    teacherPoolItems.forEach(item => {
      const div = document.createElement("div");
      div.className = "pool-item";
      div.innerHTML = `
        <div>
          <label><input type="checkbox" class="teacherSelect" value="${item.teacherId}" ${selectedTeacherIds.has(item.teacherId) ? "checked" : ""} /> <strong>${item.teacherName}</strong> <small>#${item.teacherId} / ${item.role}</small></label>
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
    const totalPages = Math.max(1, Math.ceil(studentTotalCount / pageSize()));
    studentPage = Math.min(Math.max(studentPage, 1), totalPages);
    studentPageInfo.textContent = `${studentPage} / ${totalPages} (${studentTotalCount})`;
    studentPool.innerHTML = "";
    studentPoolItems.forEach(item => {
      const div = document.createElement("div");
      div.className = "pool-item";
      div.innerHTML = `
        <div>
          <label><input type="checkbox" class="studentSelect" value="${item.studentId}" ${selectedStudentIds.has(item.studentId) ? "checked" : ""} /> <strong>${item.studentName}</strong> <small>#${item.studentId} / ${item.schoolGrade || "-"}학년</small></label>
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
    const size = pageSize();
    const offset = (teacherPage - 1) * size;
    const response = await api("/api/admin/teachers?" + buildPoolQuery(size, offset).toString(), { method: "GET" });
    teacherPoolItems = response.items || [];
    teacherTotalCount = response.totalCount || 0;
    renderTeacherPoolPage();
    render(response);
  }

  async function loadStudentPool(page) {
    studentPage = Math.max(1, page || 1);
    const size = pageSize();
    const offset = (studentPage - 1) * size;
    const response = await api("/api/admin/students?" + buildPoolQuery(size, offset).toString(), { method: "GET" });
    studentPoolItems = response.items || [];
    studentTotalCount = response.totalCount || 0;
    renderStudentPoolPage();
    render(response);
  }

  document.getElementById("btnLogin").addEventListener("click", async function () {
    try {
      setStatus("로그인 중...");
      const loginId = document.getElementById("loginId").value.trim();
      const password = document.getElementById("password").value.trim();
      const response = await fetch("/api/teacher/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ loginId, password })
      });
      const body = await response.json();
      if (!response.ok) throw new Error(JSON.stringify(body));
      tokenInput.value = body.accessToken || "";
      localStorage.setItem("qt_admin_token", tokenInput.value);
      setStatus("로그인 성공");
      await loadBootstrapAndDefaultYear();
      render(body);
    } catch (e) {
      setStatus("로그인 실패");
      render("로그인 실패: " + e.message);
    }
  });

  document.getElementById("btnSaveToken").addEventListener("click", function () {
    localStorage.setItem("qt_admin_token", tokenInput.value.trim());
    setStatus("토큰 저장 완료");
    loadBootstrapAndDefaultYear()
      .then(function () {
        render("토큰 저장 후 초기화 완료");
      })
      .catch(function (e) {
        handleBootstrapFailure("토큰 저장 완료, 초기화 실패", e);
      });
  });

  document.getElementById("btnClearToken").addEventListener("click", function () {
    localStorage.removeItem("qt_admin_token");
    tokenInput.value = "";
    resetUiState();
    setStatus("토큰 삭제 완료");
    render("토큰 삭제 완료");
  });

  document.getElementById("btnGetYears").addEventListener("click", async function () {
    try {
      render(await api("/api/admin/years", { method: "GET" }));
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

  document.getElementById("btnGetYearClass").addEventListener("click", async function () {
    try {
      const yearClassId = Number(document.getElementById("queryYearClassId").value);
      render(await api("/api/admin/year-classes/" + yearClassId, { method: "GET" }));
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
      auditResult.textContent = JSON.stringify(body, null, 2);
      render(body);
    } catch (e) {
      auditResult.textContent = e.message;
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

  document.getElementById("btnLoadTeacherPool").addEventListener("click", async function () {
    try {
      await loadTeacherPool(1);
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnLoadStudentPool").addEventListener("click", async function () {
    try {
      await loadStudentPool(1);
    } catch (e) {
      render(e.message);
    }
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
      const totalPages = Math.max(1, Math.ceil(teacherTotalCount / pageSize()));
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
      const totalPages = Math.max(1, Math.ceil(studentTotalCount / pageSize()));
      if (studentPage < totalPages) {
        await loadStudentPool(studentPage + 1);
      }
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignSelectedTeachers").addEventListener("click", async function () {
    try {
      const yearClassId = getTargetYearClassId();
      const teacherIds = Array.from(selectedTeacherIds.values());
      ensureNonEmptyIds(teacherIds, "선택된 teacherIds");
      if (!confirmAction(`선택 교사 ${teacherIds.join(", ")}를 반 ${yearClassId}에 배정할까요?`)) return;
      await runUndoAware(
        {
          execute: () => postAssignTeachers(yearClassId, teacherIds)
        },
        {
          label: `선택 교사 배정 취소`,
          execute: () => runAndRefresh(() => deleteAssignTeachers(yearClassId, teacherIds))
        }
      );
      selectedTeacherIds.clear();
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnAssignSelectedStudents").addEventListener("click", async function () {
    try {
      const yearClassId = getTargetYearClassId();
      const studentIds = Array.from(selectedStudentIds.values());
      ensureNonEmptyIds(studentIds, "선택된 studentIds");
      if (!confirmAction(`선택 학생 ${studentIds.join(", ")}를 반 ${yearClassId}에 배정할까요?`)) return;
      await runUndoAware(
        {
          execute: () => postAssignStudents(yearClassId, studentIds)
        },
        {
          label: `선택 학생 배정 취소`,
          execute: () => runAndRefresh(() => deleteAssignStudents(yearClassId, studentIds))
        }
      );
      selectedStudentIds.clear();
    } catch (e) {
      render(e.message);
    }
  });

  document.getElementById("btnUndoLastAction").addEventListener("click", async function () {
    try {
      if (!lastUndoAction) {
        render("되돌릴 작업이 없습니다.");
        return;
      }
      if (!confirmAction(`마지막 작업을 되돌릴까요? (${lastUndoAction.label})`)) return;
      await lastUndoAction.execute();
      setUndoAction(null);
      render("마지막 작업 되돌리기 완료");
    } catch (e) {
      render(e.message);
    }
  });

  bindBootstrapToggle("bootstrapIncludeYearClasses", "qt_admin_bootstrap_include_year_classes");
  bindBootstrapToggle("bootstrapIncludeActionTypes", "qt_admin_bootstrap_include_action_types");
  bindBootstrapToggle("bootstrapIncludePools", "qt_admin_bootstrap_include_pools");
  bindBootstrapToggle("bootstrapIncludeAuditLogs", "qt_admin_bootstrap_include_audit_logs");
  updateBootstrapModeHint();

  document.getElementById("btnBootstrapPresetFull").addEventListener("click", function () {
    applyBootstrapIncludePreset({
      includeYearClasses: true,
      includeActionTypes: true,
      includePools: true,
      includeAuditLogs: true
    });
    if (tokenInput.value.trim()) {
      loadBootstrapAndDefaultYear().catch(function (e) {
        handleBootstrapFailure("프리셋 적용 실패", e);
      });
    }
  });

  document.getElementById("btnBootstrapPresetLite").addEventListener("click", function () {
    applyBootstrapIncludePreset({
      includeYearClasses: true,
      includeActionTypes: true,
      includePools: false,
      includeAuditLogs: false
    });
    if (tokenInput.value.trim()) {
      loadBootstrapAndDefaultYear().catch(function (e) {
        handleBootstrapFailure("프리셋 적용 실패", e);
      });
    }
  });

  if (savedToken) {
    loadBootstrapAndDefaultYear().catch(function (e) {
      handleBootstrapFailure("자동 초기화 실패", e);
    });
  }
})();
