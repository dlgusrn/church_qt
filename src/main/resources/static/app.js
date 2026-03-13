(function () {
  const appRoot = document.getElementById("appRoot");
  const screenTitle = document.getElementById("screenTitle");
  const notice = document.getElementById("appNotice");
  const btnBack = document.getElementById("btnBack");
  const appToast = document.getElementById("appToast");
  const appLoading = document.getElementById("appLoading");

  const TEACHER_TOKEN_KEY = "qt_teacher_access_token";
  const DEFAULT_STUDENT_YEAR = 2026;
  let cachedStudentCurrentYear = DEFAULT_STUDENT_YEAR;
  let pendingRequestCount = 0;
  const AUTO_ADVANCE_KEY = "qt_teacher_auto_advance_next_day";
  const TEACHER_LOGIN_ID_KEY = "qt_teacher_last_login_id";
  const TEACHER_STUDENT_KEYWORD_KEY = "qt_teacher_students_keyword";
  const TEACHER_STUDENT_SORT_KEY = "qt_teacher_students_sort";

  const weekdayNames = ["일", "월", "화", "수", "목", "금", "토"];

  function setAppMode(mode) {
    document.body.dataset.appMode = mode;
  }

  function showError(message) {
    notice.textContent = message;
    notice.classList.remove("hidden");
  }

  function clearError() {
    notice.textContent = "";
    notice.classList.add("hidden");
  }

  function setLoading(loading) {
    if (loading) {
      appLoading.classList.remove("hidden");
    } else {
      appLoading.classList.add("hidden");
    }
  }

  function showToast(message) {
    appToast.textContent = message;
    appToast.classList.remove("hidden");
    window.setTimeout(() => {
      appToast.classList.add("hidden");
    }, 1600);
  }

  function normalizeErrorMessage(rawMessage) {
    const message = String(rawMessage || "");
    if (message.includes("유효하지 않은 인증 토큰")) return "인증이 만료되었습니다. 다시 로그인하세요.";
    if (message.includes("로그인이 필요")) return "로그인이 필요합니다.";
    return message;
  }

  function isYearNotFoundError(error) {
    const message = normalizeErrorMessage(error && error.message);
    return message.includes("해당 연도가 존재하지 않습니다.");
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
    const current = `${window.location.pathname}${window.location.search}`;
    if (current === path) return;
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

  function sortByGradeDescName(items) {
    return (Array.isArray(items) ? items.slice() : []).sort((a, b) => {
      const gradeA = Number.isFinite(Number(a.schoolGrade)) ? Number(a.schoolGrade) : -1;
      const gradeB = Number.isFinite(Number(b.schoolGrade)) ? Number(b.schoolGrade) : -1;
      if (gradeA !== gradeB) return gradeB - gradeA;
      const nameA = String(a.displayName || a.studentName || "");
      const nameB = String(b.displayName || b.studentName || "");
      return nameA.localeCompare(nameB, "ko");
    });
  }

  async function request(path, options) {
    pendingRequestCount += 1;
    setLoading(true);
    try {
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
    } finally {
      pendingRequestCount -= 1;
      if (pendingRequestCount <= 0) {
        pendingRequestCount = 0;
        setLoading(false);
      }
    }
  }

  async function requestWithTeacherAuth(path, options) {
    const token = localStorage.getItem(TEACHER_TOKEN_KEY);
    if (!token) {
      navigate("/app/teacher/login");
      throw new Error("로그인이 필요합니다.");
    }
    try {
      return await request(path, {
        ...options,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
          ...(options && options.headers ? options.headers : {})
        }
      });
    } catch (e) {
      const message = normalizeErrorMessage(e && e.message);
      if (message.includes("다시 로그인")) {
        localStorage.removeItem(TEACHER_TOKEN_KEY);
        showToast(message);
        navigate("/app/teacher/login");
      }
      throw new Error(message);
    }
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

  async function resolveTeacherYear(rawYear) {
    const parsedYear = parseYear(rawYear, new Date().getFullYear());
    const currentYear = await ensureStudentCurrentYear();
    if (!Number.isInteger(parsedYear) || parsedYear < 2000 || parsedYear > 2100) {
      return currentYear;
    }
    return parsedYear;
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

  function focusTodayCell(containerSelector) {
    const todayCell = document.querySelector(`${containerSelector} .day-cell.today`);
    if (!todayCell) return;
    window.setTimeout(() => {
      try {
        todayCell.scrollIntoView({ block: "center", inline: "nearest", behavior: "smooth" });
      } catch (_) {
        // no-op for unsupported browser behavior options
      }
    }, 40);
  }

  async function renderStudentPickScreen() {
    setAppMode("student");
    clearError();
    setTitle("학생 선택");
    btnBack.classList.add("hidden");

    await ensureStudentCurrentYear();
    const students = await request("/api/students");
    const sorted = sortByGradeDescName(students);

    appRoot.innerHTML = `
      <section class="panel">
        <label>이름 검색 <input id="studentSearchKeyword" type="text" placeholder="학생 이름 검색" /></label>
        <div id="studentPickList" class="list" style="margin-top:10px;"></div>
      </section>
    `;

    function renderStudentPickList() {
      const keyword = String(document.getElementById("studentSearchKeyword").value || "").trim().toLowerCase();
      const filtered = sorted.filter(item => {
        if (!keyword) return true;
        const displayName = String(item.displayName || item.studentName || "").toLowerCase();
        return displayName.includes(keyword);
      });
      if (filtered.length === 0) {
        document.getElementById("studentPickList").innerHTML = `
          <div class="empty-state">검색 결과가 없습니다.</div>
        `;
        return;
      }

      document.getElementById("studentPickList").innerHTML = filtered.map(item => `
        <button class="item-card student-pick" data-student-id="${item.studentId}">
          <div class="item-head">
            <span class="item-title">${escapeHtml(item.displayName || item.studentName)}</span>
          </div>
          <div class="item-sub">${escapeHtml(item.schoolGrade || "-")}학년</div>
        </button>
      `).join("");

      appRoot.querySelectorAll(".student-pick").forEach(btn => {
        btn.addEventListener("click", () => {
          const studentId = btn.dataset.studentId;
          const now = new Date();
          const { year, month } = readYearMonthFromQuery(cachedStudentCurrentYear);
          const safeYear = parseYear(year, cachedStudentCurrentYear || DEFAULT_STUDENT_YEAR);
          const safeMonth = parseMonth(month, now.getMonth() + 1);
          navigate(buildPathWithYearMonth(`/app/student/calendar/${studentId}`, safeYear, safeMonth));
        });
      });
    }

    document.getElementById("studentSearchKeyword").addEventListener("input", renderStudentPickList);
    renderStudentPickList();
  }

  async function renderStudentCalendarScreen(studentId) {
    setAppMode("student");
    clearError();
    setTitle("학생 달력");
    btnBack.classList.remove("hidden");

    await ensureStudentCurrentYear();
    let { year, month } = readYearMonthFromQuery(cachedStudentCurrentYear || DEFAULT_STUDENT_YEAR);
    let body;
    try {
      body = await request(`/api/students/${studentId}/calendar?year=${year}&month=${month}`);
    } catch (e) {
      if (!isYearNotFoundError(e)) {
        throw e;
      }
      year = await ensureStudentCurrentYear();
      month = parseMonth(month, new Date().getMonth() + 1);
      body = await request(`/api/students/${studentId}/calendar?year=${year}&month=${month}`);
      history.replaceState({}, "", buildPathWithYearMonth(`/app/student/calendar/${studentId}`, year, month));
    }
    const prevDate = new Date(year, month - 2, 1);
    const nextDate = new Date(year, month, 1);

    appRoot.innerHTML = `
      <section class="panel">
        <div class="compact-head">
          <div class="item-title">${escapeHtml(body.displayName || body.studentName)}</div>
          <div class="item-sub">${escapeHtml(body.studentName || "")}</div>
        </div>
        <div class="summary">
          <div class="box"><div class="label">QT 개수</div><div class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">노트 개수</div><div class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">총 개수</div><div class="value">${body.summary.totalCount}</div></div>
        </div>
        <div class="calendar-nav">
          <button id="btnPrevMonth" class="ghost">이전달</button>
          <div class="calendar-title">${year}년 ${month}월</div>
          <button id="btnTodayMonth" class="today-cta">오늘</button>
          <button id="btnNextMonth" class="ghost">다음달</button>
        </div>
        ${renderCalendarGrid(body.days)}
        <div class="legend">🍇 QT 완료 · 🫒 노트 완료 · 파란 배경은 오늘</div>
      </section>
    `;

    document.getElementById("btnPrevMonth").addEventListener("click", () => {
      navigate(buildPathWithYearMonth(`/app/student/calendar/${studentId}`, prevDate.getFullYear(), prevDate.getMonth() + 1));
    });
    document.getElementById("btnTodayMonth").addEventListener("click", () => {
      const today = new Date();
      navigate(buildPathWithYearMonth(`/app/student/calendar/${studentId}`, today.getFullYear(), today.getMonth() + 1));
    });
    document.getElementById("btnNextMonth").addEventListener("click", () => {
      navigate(buildPathWithYearMonth(`/app/student/calendar/${studentId}`, nextDate.getFullYear(), nextDate.getMonth() + 1));
    });

    focusTodayCell(".calendar");
  }

  function teacherLogout() {
    localStorage.removeItem(TEACHER_TOKEN_KEY);
    showToast("로그아웃되었습니다.");
    navigate("/app/teacher/login");
  }

  async function renderTeacherLoginScreen() {
    setAppMode("teacher");
    clearError();
    setTitle("교사 로그인");
    btnBack.classList.add("hidden");

    const savedLoginId = localStorage.getItem(TEACHER_LOGIN_ID_KEY) || "";
    appRoot.innerHTML = `
      <section class="panel">
        <div class="auth-row">
          <label>아이디 <input id="teacherLoginId" type="text" value="${escapeHtml(savedLoginId)}" placeholder="loginId" /></label>
          <label>비밀번호 <input id="teacherPassword" type="password" placeholder="password" /></label>
        </div>
        <div class="auth-submit">
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
        localStorage.setItem(TEACHER_LOGIN_ID_KEY, loginId);
        const body = await request("/api/teacher/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ loginId, password })
        });
        localStorage.setItem(TEACHER_TOKEN_KEY, body.accessToken);
        showToast("로그인 성공");
        const year = await resolveTeacherYear();
        navigate(`/app/teacher/students?year=${year}`);
      } catch (e) {
        showError(e.message);
      }
    });

    function submitOnEnter(event) {
      if (event.key === "Enter") {
        event.preventDefault();
        document.getElementById("btnTeacherLogin").click();
      }
    }

    document.getElementById("teacherLoginId").addEventListener("keydown", submitOnEnter);
    document.getElementById("teacherPassword").addEventListener("keydown", submitOnEnter);
  }

  async function renderTeacherStudentsScreen() {
    setAppMode("teacher");
    clearError();
    setTitle("교사 학생 목록");
    btnBack.classList.add("hidden");

    const params = new URLSearchParams(window.location.search);
    let year = await resolveTeacherYear(params.get("year"));
    let students;
    try {
      students = sortByGradeDescName(await requestWithTeacherAuth(`/api/teacher/me/students?year=${year}`));
    } catch (e) {
      const message = normalizeErrorMessage(e.message);
      if (!message.includes("해당 연도가 존재하지 않습니다.")) {
        throw e;
      }
      year = await ensureStudentCurrentYear();
      students = sortByGradeDescName(await requestWithTeacherAuth(`/api/teacher/me/students?year=${year}`));
      history.replaceState({}, "", `/app/teacher/students?year=${year}`);
    }

    const savedKeyword = localStorage.getItem(TEACHER_STUDENT_KEYWORD_KEY) || "";
    const savedSort = localStorage.getItem(TEACHER_STUDENT_SORT_KEY) || "grade_name";

    appRoot.innerHTML = `
      <section class="panel">
        <div class="row">
          <label>이름 검색 <input id="teacherStudentKeyword" type="text" value="${escapeHtml(savedKeyword)}" placeholder="학생 이름 검색" /></label>
          <label>정렬
            <select id="teacherStudentSort">
              <option value="grade_name">학년/이름</option>
              <option value="total_desc">총 개수 높은순</option>
              <option value="total_asc">총 개수 낮은순</option>
            </select>
          </label>
          <button id="btnTeacherLogout" class="ghost">로그아웃</button>
        </div>
        <div id="teacherStudentList" class="list" style="margin-top:10px;"></div>
      </section>
    `;
    document.getElementById("teacherStudentSort").value =
      ["grade_name", "total_desc", "total_asc"].includes(savedSort) ? savedSort : "grade_name";

    document.getElementById("btnTeacherLogout").addEventListener("click", teacherLogout);
    function renderTeacherStudentList() {
      const rawKeyword = String(document.getElementById("teacherStudentKeyword").value || "").trim();
      const keyword = rawKeyword.toLowerCase();
      const sortKey = String(document.getElementById("teacherStudentSort").value || "grade_name");
      localStorage.setItem(TEACHER_STUDENT_KEYWORD_KEY, rawKeyword);
      localStorage.setItem(TEACHER_STUDENT_SORT_KEY, sortKey);
      const filtered = students.filter(item => {
        if (!keyword) return true;
        const name = String(item.displayName || item.studentName || "").toLowerCase();
        return name.includes(keyword);
      });

      if (filtered.length === 0) {
        document.getElementById("teacherStudentList").innerHTML = `
          <div class="empty-state">검색 결과가 없습니다.</div>
        `;
        return;
      }

      const sortedRows = filtered.slice().sort((a, b) => {
        if (sortKey === "total_desc") return b.totalCount - a.totalCount;
        if (sortKey === "total_asc") return a.totalCount - b.totalCount;
        const gradeA = Number.isFinite(Number(a.schoolGrade)) ? Number(a.schoolGrade) : -1;
        const gradeB = Number.isFinite(Number(b.schoolGrade)) ? Number(b.schoolGrade) : -1;
        if (gradeA !== gradeB) return gradeB - gradeA;
        const nameA = String(a.displayName || a.studentName || "");
        const nameB = String(b.displayName || b.studentName || "");
        return nameA.localeCompare(nameB, "ko");
      });
      document.getElementById("teacherStudentList").innerHTML = sortedRows.map(item => `
        <button class="item-card teacher-student-card" data-student-id="${item.studentId}">
          <div class="item-head">
            <span class="item-title">${escapeHtml(item.displayName || item.studentName)}</span>
          </div>
          <div class="item-sub">${escapeHtml(item.schoolGrade || "-")}학년</div>
          <div class="teacher-stats">
            <span class="badge qt">QT ${item.qtCount}</span>
            <span class="badge note">노트 ${item.noteCount}</span>
            <span class="badge total">총 ${item.totalCount}</span>
          </div>
        </button>
      `).join("");

      appRoot.querySelectorAll(".teacher-student-card").forEach(btn => {
        btn.addEventListener("click", () => {
          const studentId = btn.dataset.studentId;
          navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, year, new Date().getMonth() + 1));
        });
      });
    }

    document.getElementById("teacherStudentKeyword").addEventListener("input", renderTeacherStudentList);
    document.getElementById("teacherStudentSort").addEventListener("change", renderTeacherStudentList);
    renderTeacherStudentList();
  }

  async function renderTeacherCalendarScreen(studentId) {
    setAppMode("teacher");
    clearError();
    setTitle("학생 체크");
    btnBack.classList.remove("hidden");

    let { year, month } = readYearMonthFromQuery();
    let body;
    try {
      body = await requestWithTeacherAuth(`/api/students/${studentId}/calendar?year=${year}&month=${month}`);
    } catch (e) {
      if (!isYearNotFoundError(e)) {
        throw e;
      }
      year = await ensureStudentCurrentYear();
      month = parseMonth(month, new Date().getMonth() + 1);
      body = await requestWithTeacherAuth(`/api/students/${studentId}/calendar?year=${year}&month=${month}`);
      history.replaceState({}, "", buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, year, month));
    }
    let selectedDate = null;
    let selectedQt = false;
    let selectedNote = false;
    let baseQt = false;
    let baseNote = false;
    const savedAutoAdvance = localStorage.getItem(AUTO_ADVANCE_KEY);
    let autoAdvanceNextDay = savedAutoAdvance == null ? true : savedAutoAdvance === "true";

    function updateDayMoveButtons() {
      const prevBtn = document.getElementById("btnPrevDay");
      const nextBtn = document.getElementById("btnNextDay");
      if (!prevBtn || !nextBtn) return;
      if (!selectedDate) {
        prevBtn.disabled = true;
        nextBtn.disabled = true;
        return;
      }
      const idx = (body.days || []).findIndex(day => day.date === selectedDate);
      prevBtn.disabled = idx <= 0;
      nextBtn.disabled = idx < 0 || idx >= (body.days || []).length - 1;
    }

    function hasUnsavedChanges() {
      if (!selectedDate) return false;
      const currentQt = document.getElementById("toggleQt").checked;
      const currentNote = document.getElementById("toggleNote").checked;
      return currentQt !== baseQt || currentNote !== baseNote;
    }

    function confirmDiscardIfNeeded() {
      if (!hasUnsavedChanges()) return true;
      return window.confirm("저장되지 않은 변경이 있습니다. 이동하시겠어요?");
    }

    function moveSelectedDate(step, forceMove) {
      if (!forceMove && !confirmDiscardIfNeeded()) return;
      if (!selectedDate) return;
      const idx = (body.days || []).findIndex(day => day.date === selectedDate);
      if (idx < 0) return;
      const nextIdx = idx + step;
      if (nextIdx < 0 || nextIdx >= (body.days || []).length) return;
      syncPanelFromDate(body.days[nextIdx].date);
    }

    function adjustSummaryAfterSave(previousQt, previousNote, nextQt, nextNote) {
      const qtDelta = (nextQt ? 1 : 0) - (previousQt ? 1 : 0);
      const noteDelta = (nextNote ? 1 : 0) - (previousNote ? 1 : 0);
      body.summary.qtCount += qtDelta;
      body.summary.noteCount += noteDelta;
      body.summary.totalCount += qtDelta + noteDelta;
      document.getElementById("summaryQtCount").textContent = String(body.summary.qtCount);
      document.getElementById("summaryNoteCount").textContent = String(body.summary.noteCount);
      document.getElementById("summaryTotalCount").textContent = String(body.summary.totalCount);
    }

    function syncPanelFromDate(dateText) {
      if (selectedDate && selectedDate !== dateText && !confirmDiscardIfNeeded()) {
        return;
      }
      const target = (body.days || []).find(day => day.date === dateText);
      selectedDate = dateText;
      baseQt = !!(target && target.qtChecked);
      baseNote = !!(target && target.noteChecked);
      selectedQt = baseQt;
      selectedNote = baseNote;
      document.getElementById("selectedDateText").textContent = dateText;
      document.getElementById("toggleQt").checked = selectedQt;
      document.getElementById("toggleNote").checked = selectedNote;
      document.getElementById("checkPanel").classList.remove("hidden");
      renderCalendarOnly();
      updateDayMoveButtons();
      updateSaveButtonState();
    }

    function updateSaveButtonState() {
      const saveButton = document.getElementById("btnSaveCheck");
      if (!saveButton) return;
      if (!selectedDate) {
        saveButton.disabled = true;
        saveButton.textContent = "저장";
        return;
      }
      const currentQt = document.getElementById("toggleQt").checked;
      const currentNote = document.getElementById("toggleNote").checked;
      const changed = currentQt !== baseQt || currentNote !== baseNote;
      saveButton.disabled = !changed;
      saveButton.textContent = changed ? "저장" : "변경 없음";
    }

    function renderCalendarOnly() {
      document.getElementById("calendarWrap").innerHTML = renderCalendarGrid(body.days, selectedDate);
      document.querySelectorAll("#calendarWrap .day-cell.selectable").forEach(btn => {
        btn.addEventListener("click", () => syncPanelFromDate(btn.dataset.date));
      });
      if (!selectedDate) {
        focusTodayCell("#calendarWrap");
      }
    }

    const prevDate = new Date(year, month - 2, 1);
    const nextDate = new Date(year, month, 1);

    appRoot.innerHTML = `
      <section class="panel">
        <div class="compact-head">
          <div class="item-title">${escapeHtml(body.displayName || body.studentName)}</div>
          <div class="item-sub">${escapeHtml(body.studentName || "")}</div>
        </div>
        <div class="summary">
          <div class="box"><div class="label">QT 개수</div><div id="summaryQtCount" class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">노트 개수</div><div id="summaryNoteCount" class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">총 개수</div><div id="summaryTotalCount" class="value">${body.summary.totalCount}</div></div>
        </div>
        <div class="calendar-nav">
          <button id="btnPrevMonth" class="ghost">이전달</button>
          <div class="calendar-title">${year}년 ${month}월</div>
          <button id="btnTodayMonthTeacher" class="ghost">오늘</button>
          <button id="btnNextMonth" class="ghost">다음달</button>
        </div>
        <div id="calendarWrap"></div>
        <div class="legend">날짜를 선택하면 아래 패널에서 QT/노트를 체크합니다.</div>

        <div id="checkPanel" class="check-panel check-panel-fixed hidden">
          <div class="check-title">선택 날짜: <span id="selectedDateText">-</span></div>
          <div class="quick-row">
            <button id="btnPrevDay" class="ghost" type="button">← 이전 날짜</button>
            <button id="btnNextDay" class="ghost" type="button">다음 날짜 →</button>
          </div>
          <div class="switch-row">
            <label><input id="toggleAutoAdvance" type="checkbox" ${autoAdvanceNextDay ? "checked" : ""} /> 저장 후 다음 날짜로 이동</label>
          </div>
          <div class="quick-row">
            <button id="btnMarkAll" class="ghost" type="button">둘 다 체크</button>
            <button id="btnClearAll" class="ghost" type="button">전체 해제</button>
          </div>
          <div class="switch-row">
            <label><input id="toggleQt" type="checkbox" /> 🍇 QT 완료</label>
            <label><input id="toggleNote" type="checkbox" /> 🫒 노트 완료</label>
          </div>
          <div class="save-row">
            <button id="btnSaveCheck">저장</button>
            <button id="btnCancelSelect" class="ghost">선택 해제</button>
          </div>
        </div>
        <div class="check-panel-spacer"></div>
      </section>
    `;

    renderCalendarOnly();

    document.getElementById("btnPrevMonth").addEventListener("click", () => {
      if (!confirmDiscardIfNeeded()) return;
      navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, prevDate.getFullYear(), prevDate.getMonth() + 1));
    });
    document.getElementById("btnTodayMonthTeacher").addEventListener("click", () => {
      if (!confirmDiscardIfNeeded()) return;
      const today = new Date();
      navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, today.getFullYear(), today.getMonth() + 1));
    });
    document.getElementById("btnNextMonth").addEventListener("click", () => {
      if (!confirmDiscardIfNeeded()) return;
      navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, nextDate.getFullYear(), nextDate.getMonth() + 1));
    });

    document.getElementById("btnCancelSelect").addEventListener("click", () => {
      selectedDate = null;
      document.getElementById("checkPanel").classList.add("hidden");
      renderCalendarOnly();
      updateDayMoveButtons();
      updateSaveButtonState();
    });

    document.getElementById("btnPrevDay").addEventListener("click", () => moveSelectedDate(-1));
    document.getElementById("btnNextDay").addEventListener("click", () => moveSelectedDate(1));
    document.getElementById("toggleAutoAdvance").addEventListener("change", e => {
      autoAdvanceNextDay = e.target.checked;
      localStorage.setItem(AUTO_ADVANCE_KEY, String(autoAdvanceNextDay));
    });

    document.getElementById("btnMarkAll").addEventListener("click", () => {
      document.getElementById("toggleQt").checked = true;
      document.getElementById("toggleNote").checked = true;
      updateSaveButtonState();
    });

    document.getElementById("btnClearAll").addEventListener("click", () => {
      document.getElementById("toggleQt").checked = false;
      document.getElementById("toggleNote").checked = false;
      updateSaveButtonState();
    });

    document.getElementById("toggleQt").addEventListener("change", updateSaveButtonState);
    document.getElementById("toggleNote").addEventListener("change", updateSaveButtonState);

    const todayIso = new Date().toISOString().slice(0, 10);
    if ((body.days || []).some(day => day.date === todayIso)) {
      syncPanelFromDate(todayIso);
    }
    updateDayMoveButtons();
    updateSaveButtonState();

    document.getElementById("btnSaveCheck").addEventListener("click", async () => {
      const saveButton = document.getElementById("btnSaveCheck");
      try {
        if (!selectedDate) {
          throw new Error("먼저 날짜를 선택하세요.");
        }
        if (saveButton.disabled) {
          return;
        }
        saveButton.disabled = true;
        saveButton.textContent = "저장 중...";
        const dayTarget = (body.days || []).find(day => day.date === selectedDate);
        const previousQt = !!(dayTarget && dayTarget.qtChecked);
        const previousNote = !!(dayTarget && dayTarget.noteChecked);
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
        if (dayTarget) {
          dayTarget.qtChecked = qtChecked;
          dayTarget.noteChecked = noteChecked;
        }
        baseQt = qtChecked;
        baseNote = noteChecked;
        selectedQt = qtChecked;
        selectedNote = noteChecked;
        adjustSummaryAfterSave(previousQt, previousNote, qtChecked, noteChecked);
        renderCalendarOnly();
        updateDayMoveButtons();
        updateSaveButtonState();
        showToast("저장되었습니다.");
        if (autoAdvanceNextDay) {
          moveSelectedDate(1, true);
        }
      } catch (e) {
        showError(normalizeErrorMessage(e.message));
      } finally {
        updateSaveButtonState();
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

      if (path === "/app/teacher") {
        const hasToken = !!localStorage.getItem(TEACHER_TOKEN_KEY);
        if (!hasToken) {
          navigate("/app/teacher/login");
          return;
        }
        const year = await resolveTeacherYear();
        navigate(`/app/teacher/students?year=${year}`);
        return;
      }

      if (path === "/app/teacher/login") {
        if (localStorage.getItem(TEACHER_TOKEN_KEY)) {
          const year = await resolveTeacherYear();
          navigate(`/app/teacher/students?year=${year}`);
          return;
        }
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

      if (path.startsWith("/app/teacher")) {
        navigate("/app/teacher/login");
        return;
      }
      navigate("/app/student");
    } catch (e) {
      showError(normalizeErrorMessage(e.message || "화면 로딩 실패"));
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
      const year = parseYear(params.get("year"), new Date().getFullYear());
      navigate(`/app/teacher/students?year=${year}`);
      return;
    }
    history.back();
  });

  window.addEventListener("popstate", renderRoute);
  renderRoute();
})();
