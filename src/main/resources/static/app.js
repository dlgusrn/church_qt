(function () {
  const appRoot = document.getElementById("appRoot");
  const screenTitle = document.getElementById("screenTitle");
  const notice = document.getElementById("appNotice");
  const btnBack = document.getElementById("btnBack");
  const appToast = document.getElementById("appToast");
  const appLoading = document.getElementById("appLoading");
  const appHeader = document.querySelector(".app-header");
  const appKicker = document.querySelector(".kicker");
  const STUDENT_BANNER_IMAGE_URL = "";

  const TEACHER_TOKEN_KEY = "qt_teacher_access_token";
  const DEFAULT_STUDENT_YEAR = new Date().getFullYear();
  let cachedStudentCurrentYear = DEFAULT_STUDENT_YEAR;
  let pendingRequestCount = 0;
  const TEACHER_LOGIN_ID_KEY = "qt_teacher_last_login_id";
  const TEACHER_STUDENT_KEYWORD_KEY = "qt_teacher_students_keyword";
  const TEACHER_STUDENT_SORT_KEY = "qt_teacher_students_sort";

  const weekdayNames = ["일", "월", "화", "수", "목", "금", "토"];
  let lastDesktopMarkerLayout = isDesktopMarkerLayout();

  function setAppMode(mode) {
    document.body.dataset.appMode = mode;
  }

  function isDesktopMarkerLayout() {
    return !!(window.matchMedia && window.matchMedia("(min-width: 721px)").matches);
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
    const path = window.location.pathname;
    if (
      document.body.dataset.appMode === "student" ||
      (path.startsWith("/app/teacher/students/") && path.endsWith("/calendar"))
    ) {
      appLoading.classList.add("hidden");
      return;
    }
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

  function formatStudentHeading(displayName, studentName, schoolGrade) {
    const safeDisplayName = String(displayName || "").trim();
    if (safeDisplayName) {
      return safeDisplayName;
    }

    const safeStudentName = String(studentName || "").trim();
    const parsedGrade = Number(schoolGrade);
    if (Number.isInteger(parsedGrade) && parsedGrade > 0) {
      return `${safeStudentName} (${parsedGrade}학년)`;
    }
    return safeStudentName;
  }

  function renderStudentBanner() {
    if (!STUDENT_BANNER_IMAGE_URL) {
      return "";
    }
    return `
      <section class="student-banner" aria-label="학생 배너 영역">
        <div class="student-banner-frame">
          <img class="student-banner-image" src="${escapeHtml(STUDENT_BANNER_IMAGE_URL)}" alt="" />
        </div>
      </section>
    `;
  }

  function setTitle(title) {
    screenTitle.textContent = title;
  }

  function setHeaderVisible(visible) {
    if (!appHeader) return;
    appHeader.classList.toggle("hidden", !visible);
  }

  function setKickerVisible(visible) {
    if (!appKicker) return;
    appKicker.classList.toggle("hidden", !visible);
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
      const markers = [];
      if (day.qtChecked) markers.push('<span class="day-marker qt" aria-label="QT">🍇</span>');
      if (day.noteChecked) markers.push('<span class="day-marker note" aria-label="노트">🍐</span>');
      if (day.attitudeChecked) markers.push('<span class="day-marker attitude" aria-label="태도">🫒</span>');
      const todayClass = day.isToday ? " today" : "";
      const selectedClass = selectedDate && selectedDate === day.date ? " selected" : "";
      cells.push(`
        <button class="day-cell selectable${todayClass}${selectedClass}" data-date="${day.date}">
          <div class="day-num-row">
            <div class="day-num">${dayNum}</div>
            ${day.isBirthday ? '<div class="day-birthday" aria-label="생일 있음">🎂</div>' : ""}
          </div>
          ${renderDayMarkers(markers)}
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

  function renderDayMarkers(markers) {
    if (!Array.isArray(markers) || markers.length === 0) {
      return '<div class="day-icons"></div>';
    }

    const isDesktop = isDesktopMarkerLayout();

    if (markers.length === 1) {
      return `
        <div class="day-icons">
          <div class="day-icons-row single">${markers[0]}</div>
        </div>
      `;
    }

    if (markers.length === 2) {
      return `
        <div class="day-icons">
          <div class="day-icons-row bottom">${markers.join("")}</div>
        </div>
      `;
    }

    if (markers.length === 3 && isDesktop) {
      return `
        <div class="day-icons">
          <div class="day-icons-row triple">${markers.join("")}</div>
        </div>
      `;
    }

    return `
      <div class="day-icons">
        <div class="day-icons-row top">${markers[0]}</div>
        <div class="day-icons-row bottom">${markers.slice(1, 3).join("")}</div>
      </div>
    `;
  }

  function renderBirthdayList(birthdays) {
    if (!Array.isArray(birthdays) || birthdays.length === 0) {
      return "";
    }

    return `
      <section class="birthday-panel">
        <div class="birthday-title">이달의 생일자</div>
        <div class="birthday-list">
          ${birthdays.map(item => `
            <div class="birthday-item">
              <div class="birthday-item-head">
                <span class="birthday-date">${formatBirthdayDate(item.date)}</span>
                <span class="birthday-type ${item.type === "teacher" ? "teacher" : "student"}">${item.type === "teacher" ? "교사" : "학생"}</span>
              </div>
              <div class="birthday-name">🎂 ${escapeHtml(item.name || "")}</div>
            </div>
          `).join("")}
        </div>
      </section>
    `;
  }

  function formatBirthdayDate(dateText) {
    if (!dateText || typeof dateText !== "string") return "-";
    const parts = dateText.split("-").map(Number);
    if (parts.length !== 3 || parts.some(Number.isNaN)) return dateText;
    return `${parts[1]}월 ${parts[2]}일`;
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
    setHeaderVisible(false);
    setKickerVisible(true);
    setTitle("");
    btnBack.classList.add("hidden");

    await ensureStudentCurrentYear();
    const students = await request("/api/students");
    const sorted = sortByGradeDescName(students);

    appRoot.innerHTML = `
      ${renderStudentBanner()}
      <section class="panel simple-panel simple-panel-plain">
        <label class="simple-field">
          <input id="studentSearchKeyword" type="text" placeholder="이름 검색" />
        </label>
        <div id="studentPickList" class="list simple-list"></div>
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
        <button class="item-card simple-choice student-pick" data-student-id="${item.studentId}">
          <div class="item-head">
            <span class="item-title">${escapeHtml(item.displayName || item.studentName)}</span>
            <span class="simple-meta">${escapeHtml(item.schoolGrade || "-")}학년</span>
          </div>
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
    setHeaderVisible(false);
    setKickerVisible(true);
    setTitle("");
    btnBack.classList.add("hidden");

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
      ${renderStudentBanner()}
      <section class="panel">
        <div class="compact-head compact-head-row">
          <button id="btnStudentCalendarBack" class="icon-btn compact-back-btn" aria-label="뒤로가기">←</button>
          <div class="item-title">${escapeHtml(formatStudentHeading(body.displayName, body.studentName, body.schoolGrade))}</div>
        </div>
        <div class="summary">
          <div class="box"><div class="label">🍇 QT 개수</div><div class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">🍐 노트 개수</div><div class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">🫒 태도 개수</div><div class="value">${body.summary.attitudeCount}</div></div>
          <div class="box total-box"><div class="label">총 개수</div><div class="value">${body.summary.totalCount}</div></div>
        </div>
        <div class="calendar-nav">
          <button id="btnPrevMonth" class="ghost">이전달</button>
          <div class="calendar-title">${year}년 ${month}월</div>
          <button id="btnTodayMonth" class="today-cta">오늘</button>
          <button id="btnNextMonth" class="ghost">다음달</button>
        </div>
        ${renderCalendarGrid(body.days)}
        <div class="legend">🎂 생일 · 파란 배경은 오늘</div>
        ${renderBirthdayList(body.birthdays)}
      </section>
    `;

    document.getElementById("btnStudentCalendarBack").addEventListener("click", () => {
      navigate("/app/student");
    });

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
    setHeaderVisible(false);
    setKickerVisible(true);
    setTitle("");
    btnBack.classList.add("hidden");

    const savedLoginId = localStorage.getItem(TEACHER_LOGIN_ID_KEY) || "";
    appRoot.innerHTML = `
      <section class="panel simple-panel auth-panel">
        <div class="simple-hero">
          <h2 class="simple-title">로그인</h2>
          <p class="simple-copy">최초 비밀번호는 생년월일 8자리입니다.</p>
        </div>
        <div class="auth-row simple-auth-row">
          <label class="simple-field">
            <span>아이디</span>
            <input id="teacherLoginId" type="text" value="${escapeHtml(savedLoginId)}" placeholder="아이디 입력" />
          </label>
          <label class="simple-field">
            <span>비밀번호</span>
            <input id="teacherPassword" type="password" placeholder="비밀번호 입력" />
          </label>
        </div>
        <div class="auth-submit simple-auth-submit">
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
    setHeaderVisible(true);
    setKickerVisible(false);
    setTitle("학생 선택");
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
            <span class="badge attitude">태도 ${item.attitudeCount}</span>
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
    const params = new URLSearchParams(window.location.search);
    const source = String(params.get("source") || "").trim().toLowerCase();
    const fromAdmin = source === "admin";
    setHeaderVisible(false);
    setKickerVisible(true);
    setTitle("");
    btnBack.classList.add("hidden");

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
      history.replaceState({}, "", `${buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, year, month)}${fromAdmin ? "&source=admin" : ""}`);
    }
    let selectedDate = null;
    let selectedQt = false;
    let selectedAttitude = false;
    let selectedNote = false;
    let baseQt = false;
    let baseAttitude = false;
    let baseNote = false;
    const autoAdvanceNextDay = true;

    function hasUnsavedChanges() {
      if (!selectedDate) return false;
      const currentQt = document.getElementById("toggleQt").checked;
      const currentAttitude = document.getElementById("toggleAttitude").checked;
      const currentNote = document.getElementById("toggleNote").checked;
      return currentQt !== baseQt || currentAttitude !== baseAttitude || currentNote !== baseNote;
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

    function adjustSummaryAfterSave(previousQt, previousAttitude, previousNote, nextQt, nextAttitude, nextNote) {
      const qtDelta = (nextQt ? 1 : 0) - (previousQt ? 1 : 0);
      const attitudeDelta = (nextAttitude ? 1 : 0) - (previousAttitude ? 1 : 0);
      const noteDelta = (nextNote ? 1 : 0) - (previousNote ? 1 : 0);
      body.summary.qtCount += qtDelta;
      body.summary.attitudeCount += attitudeDelta;
      body.summary.noteCount += noteDelta;
      body.summary.totalCount += qtDelta + attitudeDelta + noteDelta;
      document.getElementById("summaryQtCount").textContent = String(body.summary.qtCount);
      document.getElementById("summaryAttitudeCount").textContent = String(body.summary.attitudeCount);
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
      baseAttitude = !!(target && target.attitudeChecked);
      baseNote = !!(target && target.noteChecked);
      selectedQt = baseQt;
      selectedAttitude = baseAttitude;
      selectedNote = baseNote;
      document.getElementById("toggleQt").checked = selectedQt;
      document.getElementById("toggleAttitude").checked = selectedAttitude;
      document.getElementById("toggleNote").checked = selectedNote;
      document.getElementById("checkPanel").classList.remove("hidden");
      renderCalendarOnly();
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
      const currentAttitude = document.getElementById("toggleAttitude").checked;
      const currentNote = document.getElementById("toggleNote").checked;
      const changed = currentQt !== baseQt || currentAttitude !== baseAttitude || currentNote !== baseNote;
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
        <div class="compact-head compact-head-row">
          <button id="btnTeacherCalendarBack" class="icon-btn compact-back-btn" aria-label="뒤로가기">←</button>
          <div>
            <div class="item-title">${escapeHtml(body.displayName || body.studentName)}</div>
          </div>
        </div>
        <div class="summary">
          <div class="box"><div class="label">🍇 QT 개수</div><div id="summaryQtCount" class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">🍐 노트 개수</div><div id="summaryNoteCount" class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">🫒 태도 개수</div><div id="summaryAttitudeCount" class="value">${body.summary.attitudeCount}</div></div>
          <div class="box total-box"><div class="label">총 개수</div><div id="summaryTotalCount" class="value">${body.summary.totalCount}</div></div>
        </div>
        <div class="calendar-nav">
          <button id="btnPrevMonth" class="ghost">이전달</button>
          <div class="calendar-title">${year}년 ${month}월</div>
          <button id="btnTodayMonthTeacher" class="today-cta">오늘</button>
          <button id="btnNextMonth" class="ghost">다음달</button>
        </div>
        <div id="calendarWrap"></div>
        ${renderBirthdayList(body.birthdays)}

        <div id="checkPanel" class="check-panel check-panel-fixed hidden">
          <div class="quick-row">
            <button id="btnMarkAll" class="ghost" type="button">전체 체크</button>
            <button id="btnClearAll" class="ghost" type="button">전체 해제</button>
          </div>
          <div class="switch-row">
            <label><input id="toggleQt" type="checkbox" /> QT 완료</label>
            <label><input id="toggleNote" type="checkbox" /> 노트 완료</label>
            <label><input id="toggleAttitude" type="checkbox" /> 태도 완료</label>
          </div>
          <div class="save-row">
            <button id="btnSaveCheck">저장</button>
            <button id="btnCancelSelect" class="ghost">닫기</button>
          </div>
        </div>
        <div class="check-panel-spacer"></div>
      </section>
    `;

    renderCalendarOnly();

    document.getElementById("btnTeacherCalendarBack").addEventListener("click", () => {
      if (fromAdmin) {
        navigate("/app/admin");
        return;
      }
      navigate(`/app/teacher/students?year=${year}`);
    });

    document.getElementById("btnPrevMonth").addEventListener("click", () => {
      if (!confirmDiscardIfNeeded()) return;
      navigate(`${buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, prevDate.getFullYear(), prevDate.getMonth() + 1)}${fromAdmin ? "&source=admin" : ""}`);
    });
    document.getElementById("btnTodayMonthTeacher").addEventListener("click", () => {
      if (!confirmDiscardIfNeeded()) return;
      const today = new Date();
      navigate(`${buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, today.getFullYear(), today.getMonth() + 1)}${fromAdmin ? "&source=admin" : ""}`);
    });
    document.getElementById("btnNextMonth").addEventListener("click", () => {
      if (!confirmDiscardIfNeeded()) return;
      navigate(`${buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, nextDate.getFullYear(), nextDate.getMonth() + 1)}${fromAdmin ? "&source=admin" : ""}`);
    });

    document.getElementById("btnCancelSelect").addEventListener("click", () => {
      selectedDate = null;
      document.getElementById("checkPanel").classList.add("hidden");
      renderCalendarOnly();
      updateSaveButtonState();
    });

    document.getElementById("btnMarkAll").addEventListener("click", () => {
      document.getElementById("toggleQt").checked = true;
      document.getElementById("toggleAttitude").checked = true;
      document.getElementById("toggleNote").checked = true;
      updateSaveButtonState();
    });

    document.getElementById("btnClearAll").addEventListener("click", () => {
      document.getElementById("toggleQt").checked = false;
      document.getElementById("toggleAttitude").checked = false;
      document.getElementById("toggleNote").checked = false;
      updateSaveButtonState();
    });

    document.getElementById("toggleQt").addEventListener("change", updateSaveButtonState);
    document.getElementById("toggleAttitude").addEventListener("change", updateSaveButtonState);
    document.getElementById("toggleNote").addEventListener("change", updateSaveButtonState);

    const todayIso = new Date().toISOString().slice(0, 10);
    if ((body.days || []).some(day => day.date === todayIso)) {
      syncPanelFromDate(todayIso);
    }
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
        const previousAttitude = !!(dayTarget && dayTarget.attitudeChecked);
        const previousNote = !!(dayTarget && dayTarget.noteChecked);
        const qtChecked = document.getElementById("toggleQt").checked;
        const attitudeChecked = document.getElementById("toggleAttitude").checked;
        const noteChecked = document.getElementById("toggleNote").checked;
        await requestWithTeacherAuth("/api/teacher/check", {
          method: "POST",
          body: JSON.stringify({
            studentId: Number(studentId),
            year,
            date: selectedDate,
            qtChecked,
            attitudeChecked,
            noteChecked
          })
        });
        if (dayTarget) {
          dayTarget.qtChecked = qtChecked;
          dayTarget.attitudeChecked = attitudeChecked;
          dayTarget.noteChecked = noteChecked;
        }
        baseQt = qtChecked;
        baseAttitude = attitudeChecked;
        baseNote = noteChecked;
        selectedQt = qtChecked;
        selectedAttitude = attitudeChecked;
        selectedNote = noteChecked;
        adjustSummaryAfterSave(previousQt, previousAttitude, previousNote, qtChecked, attitudeChecked, noteChecked);
        renderCalendarOnly();
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
      if (String(params.get("source") || "").trim().toLowerCase() === "admin") {
        navigate("/app/admin");
        return;
      }
      const year = parseYear(params.get("year"), new Date().getFullYear());
      navigate(`/app/teacher/students?year=${year}`);
      return;
    }
    history.back();
  });

  function handleViewportMarkerLayoutChange() {
    const nextDesktopMarkerLayout = isDesktopMarkerLayout();
    if (nextDesktopMarkerLayout === lastDesktopMarkerLayout) {
      return;
    }
    lastDesktopMarkerLayout = nextDesktopMarkerLayout;

    const path = window.location.pathname;
    if (
      path.startsWith("/app/student/calendar/") ||
      (path.startsWith("/app/teacher/students/") && path.endsWith("/calendar"))
    ) {
      renderRoute();
    }
  }

  window.addEventListener("resize", handleViewportMarkerLayoutChange);

  window.addEventListener("popstate", renderRoute);
  renderRoute();
})();
