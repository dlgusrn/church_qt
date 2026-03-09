(function () {
  const statusText = document.getElementById("statusText");
  const result = document.getElementById("result");
  const auditResult = document.getElementById("auditResult");

  const tokenInput = document.getElementById("token");
  const loginIdInput = document.getElementById("loginId");
  const passwordInput = document.getElementById("password");
  const queryYearInput = document.getElementById("queryYear");
  const targetYearClassIdInput = document.getElementById("targetYearClassId");

  const teacherPoolEl = document.getElementById("teacherPool");
  const studentPoolEl = document.getElementById("studentPool");
  const classTableWrap = document.getElementById("classTableWrap");

  const statYears = document.getElementById("statYears");
  const statClasses = document.getElementById("statClasses");
  const statTeachers = document.getElementById("statTeachers");
  const statStudents = document.getElementById("statStudents");
  const statAudit = document.getElementById("statAudit");
  const statSchema = document.getElementById("statSchema");

  let cachedYearClasses = [];
  let teacherPoolItems = [];
  let studentPoolItems = [];
  const selectedTeacherIds = new Set();
  const selectedStudentIds = new Set();

  const savedToken = localStorage.getItem("qt_ops_token");
  if (savedToken) tokenInput.value = savedToken;

  function setStatus(message) {
    statusText.textContent = message;
  }

  function render(data) {
    result.textContent = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  }

  function renderAudit(data) {
    auditResult.textContent = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  }

  function tokenOrThrow() {
    const token = tokenInput.value.trim();
    if (!token) throw new Error("토큰이 비어 있습니다.");
    return token;
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

  function renderClassTable(items) {
    if (!Array.isArray(items) || items.length === 0) {
      classTableWrap.innerHTML = "<p>조회된 반이 없습니다.</p>";
      return;
    }
    classTableWrap.innerHTML = `
      <table>
        <thead>
          <tr>
            <th>반 ID</th>
            <th>반명</th>
            <th>교사 수</th>
            <th>학생 수</th>
            <th>상태</th>
            <th>선택</th>
          </tr>
        </thead>
        <tbody>
          ${items.map(item => `
            <tr>
              <td>${item.yearClassId}</td>
              <td>${item.className}</td>
              <td>${(item.teachers || []).length}</td>
              <td>${(item.students || []).length}</td>
              <td>${item.active ? "활성" : "비활성"}</td>
              <td><button class="ghost btn-select-class" data-class-id="${item.yearClassId}">대상 지정</button></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
    classTableWrap.querySelectorAll(".btn-select-class").forEach(btn => {
      btn.addEventListener("click", () => {
        targetYearClassIdInput.value = btn.dataset.classId;
        setStatus("대상 반 지정: " + btn.dataset.classId);
      });
    });
  }

  function renderPool(container, items, selectedSet, type) {
    if (!Array.isArray(items) || items.length === 0) {
      container.innerHTML = "<p>데이터 없음</p>";
      return;
    }
    container.innerHTML = items.map(item => {
      const id = type === "teacher" ? item.teacherId : item.studentId;
      const name = type === "teacher" ? item.teacherName : item.studentName;
      const checked = selectedSet.has(id) ? "checked" : "";
      return `
        <label class="pool-item">
          <span>${name} (#${id})</span>
          <input type="checkbox" data-id="${id}" ${checked} />
        </label>
      `;
    }).join("");

    container.querySelectorAll("input[type='checkbox']").forEach(cb => {
      cb.addEventListener("change", () => {
        const id = Number(cb.dataset.id);
        if (cb.checked) selectedSet.add(id);
        else selectedSet.delete(id);
      });
    });
  }

  async function loadBootstrap() {
    const year = Number(queryYearInput.value);
    const params = new URLSearchParams();
    if (Number.isFinite(year)) params.set("year", String(year));
    params.set("includeYearClasses", "true");
    params.set("includeActionTypes", "false");
    params.set("includePools", "true");
    params.set("includeAuditLogs", "true");
    params.set("poolActiveOnly", "true");
    params.set("poolLimit", "20");
    params.set("auditLimit", "50");
    params.set("auditOffset", "0");

    const body = await api("/api/admin/bootstrap?" + params.toString(), { method: "GET" });
    render(body);

    const years = Array.isArray(body.years) ? body.years : [];
    const yearClasses = Array.isArray(body.yearClasses) ? body.yearClasses : [];
    const teachers = body.teachers && Array.isArray(body.teachers.items) ? body.teachers.items : [];
    const students = body.students && Array.isArray(body.students.items) ? body.students.items : [];
    const auditLogs = body.auditLogs && Array.isArray(body.auditLogs.items) ? body.auditLogs.items : [];

    cachedYearClasses = yearClasses;
    teacherPoolItems = teachers;
    studentPoolItems = students;
    selectedTeacherIds.clear();
    selectedStudentIds.clear();

    statYears.textContent = String(years.length);
    statClasses.textContent = String(yearClasses.length);
    statTeachers.textContent = String(teachers.length);
    statStudents.textContent = String(students.length);
    statAudit.textContent = String(auditLogs.length);
    statSchema.textContent = body.schemaVersion || "-";

    renderClassTable(yearClasses);
    renderPool(teacherPoolEl, teacherPoolItems, selectedTeacherIds, "teacher");
    renderPool(studentPoolEl, studentPoolItems, selectedStudentIds, "student");
    renderAudit(body.auditLogs || {});
  }

  async function loadYearClasses() {
    const year = Number(queryYearInput.value);
    if (!Number.isFinite(year)) throw new Error("조회 연도를 입력하세요.");
    const body = await api("/api/admin/year-classes?year=" + year, { method: "GET" });
    cachedYearClasses = Array.isArray(body) ? body : [];
    statClasses.textContent = String(cachedYearClasses.length);
    renderClassTable(cachedYearClasses);
    render(body);
  }

  function poolQuery() {
    const keyword = document.getElementById("poolKeyword").value.trim();
    const activeOnly = document.getElementById("poolActiveOnly").checked;
    const limit = Number(document.getElementById("poolLimit").value || 20);
    const params = new URLSearchParams();
    params.set("activeOnly", String(activeOnly));
    params.set("limit", String(Math.min(Math.max(limit, 5), 200)));
    params.set("offset", "0");
    if (keyword) params.set("keyword", keyword);
    return params.toString();
  }

  async function loadTeacherPool() {
    const body = await api("/api/admin/teachers?" + poolQuery(), { method: "GET" });
    teacherPoolItems = Array.isArray(body.items) ? body.items : [];
    selectedTeacherIds.clear();
    statTeachers.textContent = String(teacherPoolItems.length);
    renderPool(teacherPoolEl, teacherPoolItems, selectedTeacherIds, "teacher");
    render(body);
  }

  async function loadStudentPool() {
    const body = await api("/api/admin/students?" + poolQuery(), { method: "GET" });
    studentPoolItems = Array.isArray(body.items) ? body.items : [];
    selectedStudentIds.clear();
    statStudents.textContent = String(studentPoolItems.length);
    renderPool(studentPoolEl, studentPoolItems, selectedStudentIds, "student");
    render(body);
  }

  async function assignSelectedTeachers() {
    const yearClassId = Number(targetYearClassIdInput.value);
    if (!Number.isFinite(yearClassId)) throw new Error("대상 반 ID를 입력하세요.");
    const teacherIds = Array.from(selectedTeacherIds);
    if (teacherIds.length === 0) throw new Error("선택된 교사가 없습니다.");
    const body = await api("/api/admin/year-classes/" + yearClassId + "/teachers", {
      method: "POST",
      body: JSON.stringify({ teacherIds })
    });
    render(body);
    await loadYearClasses();
  }

  async function assignSelectedStudents() {
    const yearClassId = Number(targetYearClassIdInput.value);
    if (!Number.isFinite(yearClassId)) throw new Error("대상 반 ID를 입력하세요.");
    const studentIds = Array.from(selectedStudentIds);
    if (studentIds.length === 0) throw new Error("선택된 학생이 없습니다.");
    const body = await api("/api/admin/year-classes/" + yearClassId + "/students", {
      method: "POST",
      body: JSON.stringify({ studentIds })
    });
    render(body);
    await loadYearClasses();
  }

  async function loadAuditLogs() {
    const limit = Number(document.getElementById("auditLimit").value || 100);
    const offset = Number(document.getElementById("auditOffset").value || 0);
    const actionType = document.getElementById("auditActionType").value.trim();
    const keyword = document.getElementById("auditKeyword").value.trim();
    const params = new URLSearchParams();
    params.set("limit", String(limit));
    params.set("offset", String(offset));
    if (actionType) params.set("actionType", actionType);
    if (keyword) params.set("keyword", keyword);
    const body = await api("/api/admin/audit-logs?" + params.toString(), { method: "GET" });
    const count = body && Array.isArray(body.items) ? body.items.length : 0;
    statAudit.textContent = String(count);
    renderAudit(body);
    render(body);
  }

  async function login() {
    const loginId = loginIdInput.value.trim();
    const password = passwordInput.value;
    if (!loginId || !password) throw new Error("아이디/비밀번호를 입력하세요.");
    setStatus("로그인 중...");
    const response = await fetch("/api/teacher/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ loginId, password })
    });
    const body = await response.json();
    if (!response.ok || !body.accessToken) {
      throw new Error("로그인 실패");
    }
    tokenInput.value = body.accessToken;
    localStorage.setItem("qt_ops_token", body.accessToken);
    setStatus("로그인 성공");
    render(body);
    await loadBootstrap();
  }

  document.getElementById("btnLogin").addEventListener("click", () => login().catch(e => render(e.message)));
  document.getElementById("btnClearToken").addEventListener("click", () => {
    tokenInput.value = "";
    localStorage.removeItem("qt_ops_token");
    setStatus("로그아웃 완료");
    render("토큰 삭제 완료");
  });

  document.getElementById("btnRefreshBootstrap").addEventListener("click", () => loadBootstrap().catch(e => render(e.message)));
  document.getElementById("btnLoadClasses").addEventListener("click", () => loadYearClasses().catch(e => render(e.message)));
  document.getElementById("btnLoadTeacherPool").addEventListener("click", () => loadTeacherPool().catch(e => render(e.message)));
  document.getElementById("btnLoadStudentPool").addEventListener("click", () => loadStudentPool().catch(e => render(e.message)));
  document.getElementById("btnAssignTeachers").addEventListener("click", () => assignSelectedTeachers().catch(e => render(e.message)));
  document.getElementById("btnAssignStudents").addEventListener("click", () => assignSelectedStudents().catch(e => render(e.message)));
  document.getElementById("btnLoadAudit").addEventListener("click", () => loadAuditLogs().catch(e => render(e.message)));

  if (tokenInput.value.trim()) {
    loadBootstrap().catch(e => render(e.message));
  }
})();
