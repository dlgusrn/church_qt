(function () {
  const appRoot = document.getElementById("appRoot");
  const screenTitle = document.getElementById("screenTitle");
  const notice = document.getElementById("appNotice");
  const btnBack = document.getElementById("btnBack");

  const TEACHER_TOKEN_KEY = "qt_teacher_access_token";
  const DEFAULT_STUDENT_YEAR = 2026;
  let cachedStudentCurrentYear = DEFAULT_STUDENT_YEAR;

  const weekdayNames = ["일", "월", "화", "수", "목", "금", "토"];

  function showError(message) {
    notice.textContent = message;
    notice.classList.remove("hidden");
  }

  function clearError() {
    notice.textContent = "";
    notice.classList.add("hidden");
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function setTitle(title) {
    screenTitle.textContent = title;
  }

  function navigate(path) {
    if (window.location.pathname === path) return;
    history.pushState({}, "", path);
    renderRoute();
  }

  function parseYear(rawValue, fallbackYear) {
    if (!rawValue) return fallbackYear;
    const parsed = Number(rawValue);
    if (!Number.isInteger(parsed) || parsed < 2000 || parsed > 2100) return fallbackYear;
    return parsed;
  }

  function parseMonth(rawValue, fallbackMonth) {
    if (!rawValue) return fallbackMonth;
    const parsed = Number(rawValue);
    if (!Number.isInteger(parsed) || parsed < 1 || parsed > 12) return fallbackMonth;
    return parsed;
  }

  function readYearMonthFromQuery(fallbackYear) {
    const now = new Date();
    const params = new URLSearchParams(window.location.search);
    const year = parseYear(params.get("year"), fallbackYear || now.getFullYear());
    const month = parseMonth(params.get("month"), now.getMonth() + 1);
    return {
      year,
      month
    };
  }

  function buildPathWithYearMonth(path, year, month) {
    return `${path}?year=${year}&month=${month}`;
  }

  async function request(path, options) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch (_) {
      body = text;
    }
    if (!response.ok) {
      const message = typeof body === "string" ? body : JSON.stringify(body);
      throw new Error(message || "요청 실패");
    }
    return body;
  }

  async function requestWithTeacherAuth(path, options) {
    const token = localStorage.getItem(TEACHER_TOKEN_KEY);
    if (!token) {
      navigate("/app/teacher/login");
      throw new Error("로그인이 필요합니다.");
    }
    return request(path, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        ...(options && options.headers ? options.headers : {})
      }
    });
  }

  async function ensureStudentCurrentYear() {
    try {
      const body = await request("/api/students/current-year");
      const year = Number(body && body.year);
      if (Number.isInteger(year) && year >= 2000 && year <= 2100) {
        cachedStudentCurrentYear = year;
      } else {
        cachedStudentCurrentYear = DEFAULT_STUDENT_YEAR;
      }
    } catch (_) {
      cachedStudentCurrentYear = DEFAULT_STUDENT_YEAR;
    }
    return cachedStudentCurrentYear;
  }

  function renderCalendarGrid(days, selectedDate) {
    if (!Array.isArray(days) || days.length === 0) {
      return "<p>달력 데이터가 없습니다.</p>";
    }

    const first = days[0];
    const [y, m, d] = first.date.split("-").map(Number);
    const firstDow = new Date(y, m - 1, d).getDay();

    const cells = [];
    for (let i = 0; i < firstDow; i += 1) {
      cells.push('<div class="day-cell"></div>');
    }

    days.forEach(day => {
      const dayNum = Number(day.date.split("-")[2]);
      const icons = [];
      if (day.qtChecked) icons.push("🍇");
      if (day.noteChecked) icons.push("🫒");
      const todayClass = day.isToday ? " today" : "";
      const selectedClass = selectedDate && selectedDate === day.date ? " selected" : "";
      cells.push(`
        <button class="day-cell selectable${todayClass}${selectedClass}" data-date="${day.date}">
          <div class="day-num">${dayNum}</div>
          <div class="day-icons">${icons.join(" ")}</div>
        </button>
      `);
    });

    return `
      <div class="calendar">
        <div class="weekday-row">
          ${weekdayNames.map(name => `<div class="weekday">${name}</div>`).join("")}
        </div>
        <div class="day-grid">${cells.join("")}</div>
      </div>
    `;
  }

  async function renderStudentPickScreen() {
    clearError();
    setTitle("학생 선택");
    btnBack.classList.add("hidden");

    await ensureStudentCurrentYear();
    const students = await request("/api/students");
    const sorted = Array.isArray(students)
      ? students.slice().sort((a, b) => String(a.displayName || "").localeCompare(String(b.displayName || ""), "ko"))
      : [];

    appRoot.innerHTML = `
      <section class="panel">
        <div class="list">
          ${sorted.map(item => `
            <button class="item-card student-pick" data-student-id="${item.studentId}">
              <div class="item-head">
                <span class="item-title">${escapeHtml(item.displayName || item.studentName)}</span>
              </div>
              <div class="item-sub">${escapeHtml(item.schoolGrade || "-")}학년</div>
            </button>
          `).join("")}
        </div>
      </section>
    `;

    appRoot.querySelectorAll(".student-pick").forEach(btn => {
      btn.addEventListener("click", () => {
        const studentId = btn.dataset.studentId;
        const now = new Date();
        const { year, month } = readYearMonthFromQuery(cachedStudentCurrentYear);
        const safeYear = year || cachedStudentCurrentYear || DEFAULT_STUDENT_YEAR;
        const safeMonth = month || (now.getMonth() + 1);
        navigate(buildPathWithYearMonth(`/app/student/calendar/${studentId}`, safeYear, safeMonth));
      });
    });
  }

  async function renderStudentCalendarScreen(studentId) {
    clearError();
    setTitle("학생 달력");
    btnBack.classList.remove("hidden");

    await ensureStudentCurrentYear();
    const { year, month } = readYearMonthFromQuery(cachedStudentCurrentYear || DEFAULT_STUDENT_YEAR);
    const body = await request(`/api/students/${studentId}/calendar?year=${year}&month=${month}`);

    const prevDate = new Date(year, month - 2, 1);
    const nextDate = new Date(year, month, 1);

    appRoot.innerHTML = `
      <section class="panel">
        <div class="item-title">${escapeHtml(body.displayName || body.studentName)}</div>
        <div class="summary">
          <div class="box"><div class="label">QT 개수</div><div class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">노트 개수</div><div class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">총 개수</div><div class="value">${body.summary.totalCount}</div></div>
        </div>
        <div class="calendar-nav">
          <button id="btnPrevMonth" class="ghost">이전달</button>
          <div class="calendar-title">${year}년 ${month}월</div>
          <button id="btnNextMonth" class="ghost">다음달</button>
        </div>
        ${renderCalendarGrid(body.days)}
        <div class="legend">🍇 QT 완료 · 🫒 노트 완료 · 파란 배경은 오늘</div>
      </section>
    `;

    document.getElementById("btnPrevMonth").addEventListener("click", () => {
      navigate(buildPathWithYearMonth(`/app/student/calendar/${studentId}`, prevDate.getFullYear(), prevDate.getMonth() + 1));
    });
    document.getElementById("btnNextMonth").addEventListener("click", () => {
      navigate(buildPathWithYearMonth(`/app/student/calendar/${studentId}`, nextDate.getFullYear(), nextDate.getMonth() + 1));
    });
  }

  function teacherLogout() {
    localStorage.removeItem(TEACHER_TOKEN_KEY);
    navigate("/app/teacher/login");
  }

  async function renderTeacherLoginScreen() {
    clearError();
    setTitle("교사 로그인");
    btnBack.classList.add("hidden");

    appRoot.innerHTML = `
      <section class="panel">
        <div class="row">
          <label>아이디 <input id="teacherLoginId" type="text" placeholder="loginId" /></label>
          <label>비밀번호 <input id="teacherPassword" type="password" placeholder="password" /></label>
        </div>
        <div class="row" style="margin-top:8px;">
          <button id="btnTeacherLogin">로그인</button>
        </div>
      </section>
    `;

    document.getElementById("btnTeacherLogin").addEventListener("click", async () => {
      try {
        clearError();
        const loginId = document.getElementById("teacherLoginId").value.trim();
        const password = document.getElementById("teacherPassword").value;
        if (!loginId || !password) {
          throw new Error("아이디/비밀번호를 입력하세요.");
        }
        const body = await request("/api/teacher/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ loginId, password })
        });
        localStorage.setItem(TEACHER_TOKEN_KEY, body.accessToken);
        const year = new Date().getFullYear();
        navigate(`/app/teacher/students?year=${year}`);
      } catch (e) {
        showError(e.message);
      }
    });
  }

  async function renderTeacherStudentsScreen() {
    clearError();
    setTitle("교사 학생 목록");
    btnBack.classList.add("hidden");

    const params = new URLSearchParams(window.location.search);
    const year = Number(params.get("year")) || new Date().getFullYear();
    const students = await requestWithTeacherAuth(`/api/teacher/me/students?year=${year}`);

    appRoot.innerHTML = `
      <section class="panel">
        <div class="row">
          <label>조회 연도 <input id="teacherYear" type="number" value="${year}" /></label>
          <button id="btnTeacherYearLoad" class="ghost">조회</button>
          <button id="btnTeacherLogout" class="ghost">로그아웃</button>
        </div>
        <div class="list" style="margin-top:10px;">
          ${(students || []).map(item => `
            <button class="item-card teacher-student-card" data-student-id="${item.studentId}">
              <div class="item-head">
                <span class="item-title">${escapeHtml(item.displayName || item.studentName)}</span>
              </div>
              <div class="teacher-stats">
                <span class="badge qt">QT ${item.qtCount}</span>
                <span class="badge note">노트 ${item.noteCount}</span>
                <span class="badge total">총 ${item.totalCount}</span>
              </div>
            </button>
          `).join("")}
        </div>
      </section>
    `;

    document.getElementById("btnTeacherLogout").addEventListener("click", teacherLogout);
    document.getElementById("btnTeacherYearLoad").addEventListener("click", () => {
      const selectedYear = Number(document.getElementById("teacherYear").value);
      navigate(`/app/teacher/students?year=${selectedYear}`);
    });
    appRoot.querySelectorAll(".teacher-student-card").forEach(btn => {
      btn.addEventListener("click", () => {
        const studentId = btn.dataset.studentId;
        navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, year, new Date().getMonth() + 1));
      });
    });
  }

  async function renderTeacherCalendarScreen(studentId) {
    clearError();
    setTitle("학생 체크");
    btnBack.classList.remove("hidden");

    const { year, month } = readYearMonthFromQuery();
    const body = await requestWithTeacherAuth(`/api/students/${studentId}/calendar?year=${year}&month=${month}`);

    let selectedDate = null;
    let selectedQt = false;
    let selectedNote = false;

    function syncPanelFromDate(dateText) {
      const target = (body.days || []).find(day => day.date === dateText);
      selectedDate = dateText;
      selectedQt = !!(target && target.qtChecked);
      selectedNote = !!(target && target.noteChecked);
      document.getElementById("selectedDateText").textContent = dateText;
      document.getElementById("toggleQt").checked = selectedQt;
      document.getElementById("toggleNote").checked = selectedNote;
      document.getElementById("checkPanel").classList.remove("hidden");
      renderCalendarOnly();
    }

    function renderCalendarOnly() {
      document.getElementById("calendarWrap").innerHTML = renderCalendarGrid(body.days, selectedDate);
      document.querySelectorAll("#calendarWrap .day-cell.selectable").forEach(btn => {
        btn.addEventListener("click", () => syncPanelFromDate(btn.dataset.date));
      });
    }

    const prevDate = new Date(year, month - 2, 1);
    const nextDate = new Date(year, month, 1);

    appRoot.innerHTML = `
      <section class="panel">
        <div class="item-title">${escapeHtml(body.displayName || body.studentName)}</div>
        <div class="summary">
          <div class="box"><div class="label">QT 개수</div><div class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">노트 개수</div><div class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">총 개수</div><div class="value">${body.summary.totalCount}</div></div>
        </div>
        <div class="calendar-nav">
          <button id="btnPrevMonth" class="ghost">이전달</button>
          <div class="calendar-title">${year}년 ${month}월</div>
          <button id="btnNextMonth" class="ghost">다음달</button>
        </div>
        <div id="calendarWrap"></div>
        <div class="legend">날짜를 선택하면 아래 패널에서 QT/노트를 체크합니다.</div>

        <div id="checkPanel" class="check-panel hidden">
          <div class="check-title">선택 날짜: <span id="selectedDateText">-</span></div>
          <div class="switch-row">
            <label><input id="toggleQt" type="checkbox" /> 🍇 QT 완료</label>
            <label><input id="toggleNote" type="checkbox" /> 🫒 노트 완료</label>
          </div>
          <div class="save-row">
            <button id="btnSaveCheck">저장</button>
            <button id="btnCancelSelect" class="ghost">선택 해제</button>
          </div>
        </div>
      </section>
    `;

    renderCalendarOnly();

    document.getElementById("btnPrevMonth").addEventListener("click", () => {
      navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, prevDate.getFullYear(), prevDate.getMonth() + 1));
    });
    document.getElementById("btnNextMonth").addEventListener("click", () => {
      navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, nextDate.getFullYear(), nextDate.getMonth() + 1));
    });

    document.getElementById("btnCancelSelect").addEventListener("click", () => {
      selectedDate = null;
      document.getElementById("checkPanel").classList.add("hidden");
      renderCalendarOnly();
    });

    document.getElementById("btnSaveCheck").addEventListener("click", async () => {
      try {
        if (!selectedDate) {
          throw new Error("먼저 날짜를 선택하세요.");
        }
        const qtChecked = document.getElementById("toggleQt").checked;
        const noteChecked = document.getElementById("toggleNote").checked;
        await requestWithTeacherAuth("/api/teacher/check", {
          method: "POST",
          body: JSON.stringify({
            studentId: Number(studentId),
            year,
            date: selectedDate,
            qtChecked,
            noteChecked
          })
        });
        navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, year, month));
      } catch (e) {
        showError(e.message);
      }
    });
  }

  async function renderRoute() {
    try {
      clearError();
      const path = window.location.pathname;

      if (path === "/app/student") {
        await renderStudentPickScreen();
        return;
      }

      const studentCalendarMatch = path.match(/^\/app\/student\/calendar\/(\d+)$/);
      if (studentCalendarMatch) {
        await renderStudentCalendarScreen(Number(studentCalendarMatch[1]));
        return;
      }

      if (path === "/app/teacher/login") {
        await renderTeacherLoginScreen();
        return;
      }

      if (path === "/app/teacher/students") {
        await renderTeacherStudentsScreen();
        return;
      }

      const teacherCalendarMatch = path.match(/^\/app\/teacher\/students\/(\d+)\/calendar$/);
      if (teacherCalendarMatch) {
        await renderTeacherCalendarScreen(Number(teacherCalendarMatch[1]));
        return;
      }

      navigate("/app/student");
    } catch (e) {
      showError(e.message || "화면 로딩 실패");
    }
  }

  btnBack.addEventListener("click", () => {
    const path = window.location.pathname;
    if (path.startsWith("/app/student/calendar/")) {
      navigate("/app/student");
      return;
    }
    if (path.startsWith("/app/teacher/students/") && path.endsWith("/calendar")) {
      const params = new URLSearchParams(window.location.search);
      const year = Number(params.get("year")) || new Date().getFullYear();
      navigate(`/app/teacher/students?year=${year}`);
      return;
    }
    history.back();
  });

  window.addEventListener("popstate", renderRoute);
  renderRoute();
})();
