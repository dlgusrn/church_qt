(function () {
  const appRoot = document.getElementById("appRoot");
  const screenTitle = document.getElementById("screenTitle");
  const notice = document.getElementById("appNotice");
  const btnBack = document.getElementById("btnBack");
  const appToast = document.getElementById("appToast");
  const appHeader = document.querySelector(".app-header");
  const appKicker = document.querySelector(".kicker");
  const STUDENT_BANNER_IMAGE_URL = "";

  const TEACHER_TOKEN_KEY = "qt_teacher_access_token";
  const DEFAULT_STUDENT_YEAR = new Date().getFullYear();
  let cachedStudentCurrentYear = DEFAULT_STUDENT_YEAR;
  let pendingRequestCount = 0;
  let teacherMenuOutsideClickHandler = null;
  let teacherMenuEscapeHandler = null;
  const TEACHER_LOGIN_ID_KEY = "qt_teacher_last_login_id";
  const TEACHER_NAME_KEY = "qt_teacher_name";

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
    return;
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
    if (path === "/app/admin") {
      window.location.href = path;
      return;
    }
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

  function readTeacherNameFromToken() {
    const token = String(localStorage.getItem(TEACHER_TOKEN_KEY) || "").trim();
    if (!token) return "";
    const parts = token.split(".");
    if (parts.length < 2) return "";
    try {
      const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
      const decoded = JSON.parse(atob(normalized));
      return String(decoded && decoded.teacherName ? decoded.teacherName : "").trim();
    } catch (_) {
      return "";
    }
  }

  function resolveTeacherDisplayName() {
    const savedName = String(localStorage.getItem(TEACHER_NAME_KEY) || "").trim();
    if (savedName) {
      return savedName;
    }
    const tokenName = readTeacherNameFromToken();
    if (tokenName) {
      localStorage.setItem(TEACHER_NAME_KEY, tokenName);
    }
    return tokenName;
  }

  function readTeacherTokenClaims() {
    const token = String(localStorage.getItem(TEACHER_TOKEN_KEY) || "").trim();
    if (!token) return null;
    const parts = token.split(".");
    if (parts.length < 2) return null;
    try {
      const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
      return JSON.parse(atob(normalized));
    } catch (_) {
      return null;
    }
  }

  function isTeacherPasswordChangeRequired() {
    const claims = readTeacherTokenClaims();
    return !!(claims && claims.passwordChangeRequired === true);
  }

  function teacherHasPrivilegedCheckAccess() {
    const claims = readTeacherTokenClaims();
    const role = String(claims && claims.role ? claims.role : "").toUpperCase();
    return role === "ADMIN" || role === "PASTOR" || role === "DIRECTOR";
  }

  function teacherCanCheckAllStudents() {
    const claims = readTeacherTokenClaims();
    return !!(claims && claims.canCheckAllStudents === true);
  }

  function parseSchoolGradeOrder(value) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : -1;
  }

  async function canEditFullCheckForStudent(studentId, year) {
    const students = await requestWithTeacherAuth(`/api/teacher/me/students?year=${year}`);
    const target = (Array.isArray(students) ? students : []).find(item => Number(item.studentId) === Number(studentId));
    return !!(target && target.myClassStudent);
  }

  async function resolveTeacherCheckPermissions(studentId, year) {
    const students = await requestWithTeacherAuth(`/api/teacher/me/students?year=${year}`);
    const target = (Array.isArray(students) ? students : []).find(item => Number(item.studentId) === Number(studentId));
    const myClassStudent = !!(target && target.myClassStudent);
    const privileged = teacherHasPrivilegedCheckAccess();
    return {
      canEditQt: myClassStudent || privileged || teacherCanCheckAllStudents(),
      canEditNote: myClassStudent || privileged,
      canEditAttitude: true
    };
  }

  function sortByGradeName(items) {
    return (Array.isArray(items) ? items.slice() : []).sort((a, b) => {
      const gradeA = parseSchoolGradeOrder(a.schoolGrade);
      const gradeB = parseSchoolGradeOrder(b.schoolGrade);
      if (gradeA !== gradeB) return gradeB - gradeA;
      const nameA = String(a.studentName || a.displayName || "");
      const nameB = String(b.studentName || b.displayName || "");
      const nameCompare = nameA.localeCompare(nameB, "ko");
      if (nameCompare !== 0) return nameCompare;
      return Number(a.studentId || 0) - Number(b.studentId || 0);
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
      if (day.qtChecked) {
        markers.push({ type: "qt", html: '<span class="day-marker qt" aria-label="QT">🍇</span>' });
      }
      const noteCount = Number(day.noteCount || 0);
      for (let i = 0; i < noteCount; i += 1) {
        markers.push({ type: "note", html: '<span class="day-marker note" aria-label="노트">🍐</span>' });
      }
      if (day.attitudeChecked) {
        markers.push({ type: "attitude", html: '<span class="day-marker attitude" aria-label="태도">🫒</span>' });
      }
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
    const markerHtml = markers.map(marker => marker.html);

    if (markers.length === 1) {
      return `
        <div class="day-icons">
          <div class="day-icons-row single">${markerHtml[0]}</div>
        </div>
      `;
    }

    if (markers.length === 2) {
      return `
        <div class="day-icons">
          <div class="day-icons-row single">${markerHtml.join("")}</div>
        </div>
      `;
    }

    if (isDesktop && markers.length === 3) {
      return `
        <div class="day-icons">
          <div class="day-icons-row triple">${markerHtml.join("")}</div>
        </div>
      `;
    }

    const topMarkers = markers
      .filter(marker => marker.type === "qt" || marker.type === "attitude")
      .map(marker => marker.html);
    const bottomMarkers = markers
      .filter(marker => marker.type === "note")
      .map(marker => marker.html);

    if (topMarkers.length === 0 || bottomMarkers.length === 0) {
      return `
        <div class="day-icons">
          <div class="day-icons-row single">${markerHtml.join("")}</div>
        </div>
      `;
    }

    return `
      <div class="day-icons">
        <div class="day-icons-row top">${topMarkers.join("")}</div>
        <div class="day-icons-row bottom notes">${bottomMarkers.join("")}</div>
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
                <span class="birthday-type ${item.type === "teacher" ? "teacher" : "student"}">${escapeHtml(getBirthdayTypeLabel(item))}</span>
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

  function getBirthdayTypeLabel(item) {
    if (!item || item.type !== "teacher") {
      return "학생";
    }
    const role = String(item.role || "").toUpperCase();
    if (role === "PASTOR") return "전도사";
    if (role === "DIRECTOR") return "부장";
    if (role === "ADMIN") return "관리자";
    return "교사";
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
    const sorted = sortByGradeName(students);

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
            <span class="item-title">${escapeHtml(item.studentName || item.displayName)}</span>
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
          <div class="box"><div class="label">🍇 QT(포도)</div><div class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">🍐 노트(무화과)</div><div class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">🫒 태도(올리브)</div><div class="value">${body.summary.attitudeCount}</div></div>
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
    localStorage.removeItem(TEACHER_NAME_KEY);
    showToast("로그아웃되었습니다.");
    navigate("/app/teacher/login");
  }

  async function renderTeacherPasswordScreen() {
    setAppMode("teacher");
    clearError();
    setHeaderVisible(true);
    setKickerVisible(false);
    setTitle("비밀번호 변경");
    btnBack.classList.remove("hidden");
    const passwordChangeRequired = isTeacherPasswordChangeRequired();
    const passwordChangeCopy = passwordChangeRequired
      ? "최초 접속 시 비밀번호를 변경하셔야 합니다."
      : "현재 비밀번호를 확인한 뒤 새 비밀번호로 바꿉니다.";

    appRoot.innerHTML = `
      <section class="panel simple-panel">
        <div class="simple-hero">
          <h2 class="simple-title">비밀번호 변경</h2>
          <p class="simple-copy">${escapeHtml(passwordChangeCopy)}</p>
        </div>
        <div class="auth-row simple-auth-row password-change-grid">
          <label class="simple-field">
            <span>현재 비밀번호</span>
            <input id="teacherCurrentPassword" type="password" placeholder="현재 비밀번호" />
          </label>
          <label class="simple-field">
            <span>새 비밀번호</span>
            <input id="teacherNewPassword" type="password" placeholder="새 비밀번호" />
          </label>
          <label class="simple-field">
            <span>새 비밀번호 확인</span>
            <input id="teacherConfirmPassword" type="password" placeholder="새 비밀번호 다시 입력" />
          </label>
        </div>
        <div class="auth-submit simple-auth-submit password-change-actions">
          <button id="btnTeacherSubmitPassword">변경</button>
          <button id="btnTeacherPasswordBack" class="ghost" type="button">취소</button>
        </div>
      </section>
    `;

    async function submitPasswordChange() {
      try {
        clearError();
        const currentPassword = document.getElementById("teacherCurrentPassword").value;
        const newPassword = document.getElementById("teacherNewPassword").value;
        const confirmPassword = document.getElementById("teacherConfirmPassword").value;
        if (!currentPassword || !newPassword || !confirmPassword) {
          throw new Error("모든 비밀번호 항목을 입력하세요.");
        }
        if (newPassword !== confirmPassword) {
          throw new Error("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }
        await requestWithTeacherAuth("/api/teacher/me/password", {
          method: "POST",
          body: JSON.stringify({ currentPassword, newPassword, confirmPassword })
        }).then(function (body) {
          if (body && body.accessToken) {
            localStorage.setItem(TEACHER_TOKEN_KEY, body.accessToken);
          }
          if (body && body.teacherName) {
            localStorage.setItem(TEACHER_NAME_KEY, String(body.teacherName || "").trim());
          }
        });
        showToast("비밀번호가 변경되었습니다.");
        const year = await resolveTeacherYear();
        navigate(`/app/teacher/students?year=${year}`);
      } catch (e) {
        showError(normalizeErrorMessage(e.message));
      }
    }

    document.getElementById("btnTeacherSubmitPassword").addEventListener("click", submitPasswordChange);
    document.getElementById("btnTeacherPasswordBack").addEventListener("click", async function () {
      const year = await resolveTeacherYear();
      navigate(`/app/teacher/students?year=${year}`);
    });

    ["teacherCurrentPassword", "teacherNewPassword", "teacherConfirmPassword"].forEach(function (id) {
      const input = document.getElementById(id);
      input.addEventListener("keydown", function (event) {
        if (event.key !== "Enter") {
          return;
        }
        event.preventDefault();
        submitPasswordChange();
      });
    });
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
        localStorage.setItem(TEACHER_NAME_KEY, String(body.teacherName || "").trim());
        showToast("로그인 성공");
        const year = await resolveTeacherYear();
        navigate(body && body.passwordChangeRequired ? "/app/teacher/password" : `/app/teacher/students?year=${year}`);
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
    setHeaderVisible(false);
    setKickerVisible(false);
    setTitle("");
    btnBack.classList.add("hidden");

    const params = new URLSearchParams(window.location.search);
    let year = await resolveTeacherYear(params.get("year"));
    let students;
    try {
      students = await requestWithTeacherAuth(`/api/teacher/me/students?year=${year}`);
    } catch (e) {
      const message = normalizeErrorMessage(e.message);
      if (!message.includes("해당 연도가 존재하지 않습니다.")) {
        throw e;
      }
      year = await ensureStudentCurrentYear();
      students = await requestWithTeacherAuth(`/api/teacher/me/students?year=${year}`);
      history.replaceState({}, "", `/app/teacher/students?year=${year}`);
    }

    const teacherName = resolveTeacherDisplayName() || "선생님";

    appRoot.innerHTML = `
      <section class="panel">
        <div class="simple-hero teacher-student-hero">
          <div class="teacher-student-hero-head">
            <h2 class="simple-title">${escapeHtml(teacherName)} 선생님, 안녕하세요.</h2>
            <div class="teacher-toolbar-actions teacher-menu">
              <button
                id="btnTeacherMenu"
                class="icon-btn teacher-menu-trigger"
                type="button"
                aria-label="교사 메뉴 열기"
                aria-haspopup="menu"
                aria-expanded="false"
              >
                <span class="teacher-menu-icon" aria-hidden="true">👤</span>
              </button>
              <div id="teacherMenuDropdown" class="teacher-menu-dropdown hidden" role="menu">
                <button id="btnTeacherChangePassword" class="teacher-menu-item teacher-password-btn" type="button" role="menuitem">비밀번호 변경</button>
                <button id="btnTeacherLogout" class="teacher-menu-item teacher-toolbar-logout" type="button" role="menuitem">로그아웃</button>
              </div>
            </div>
          </div>
          <p class="simple-copy">체크할 학생을 선택해주세요.</p>
        </div>
        <div id="teacherStudentList" class="list" style="margin-top:10px;"></div>
      </section>
    `;

    const teacherMenu = document.querySelector(".teacher-menu");
    const teacherMenuButton = document.getElementById("btnTeacherMenu");
    const teacherMenuDropdown = document.getElementById("teacherMenuDropdown");

    if (teacherMenuOutsideClickHandler) {
      document.removeEventListener("click", teacherMenuOutsideClickHandler);
    }
    if (teacherMenuEscapeHandler) {
      document.removeEventListener("keydown", teacherMenuEscapeHandler);
    }

    function closeTeacherMenu() {
      teacherMenuDropdown.classList.add("hidden");
      teacherMenuButton.setAttribute("aria-expanded", "false");
    }

    function toggleTeacherMenu() {
      const shouldOpen = teacherMenuDropdown.classList.contains("hidden");
      teacherMenuDropdown.classList.toggle("hidden", !shouldOpen);
      teacherMenuButton.setAttribute("aria-expanded", shouldOpen ? "true" : "false");
    }

    teacherMenuButton.addEventListener("click", function (event) {
      event.stopPropagation();
      toggleTeacherMenu();
    });

    teacherMenuOutsideClickHandler = function (event) {
      if (!teacherMenu.contains(event.target)) {
        closeTeacherMenu();
      }
    };
    document.addEventListener("click", teacherMenuOutsideClickHandler);

    teacherMenuEscapeHandler = function (event) {
      if (event.key === "Escape") {
        closeTeacherMenu();
      }
    };
    document.addEventListener("keydown", teacherMenuEscapeHandler);

    document.getElementById("btnTeacherChangePassword").addEventListener("click", function () {
      closeTeacherMenu();
      navigate("/app/teacher/password");
    });
    document.getElementById("btnTeacherLogout").addEventListener("click", function () {
      closeTeacherMenu();
      teacherLogout();
    });
    function renderTeacherStudentList() {
      if (students.length === 0) {
        document.getElementById("teacherStudentList").innerHTML = `
          <div class="empty-state">표시할 학생이 없습니다.</div>
        `;
        return;
      }

      const myClassStudents = sortByGradeName(students.filter(item => item.myClassStudent));
      const otherStudents = sortByGradeName(students.filter(item => !item.myClassStudent));
      const myClassTitle = String((myClassStudents[0] && myClassStudents[0].myClassName) || "").trim() || "본인 반";
      const renderStudentCards = items => items.map(item => `
        <button class="item-card teacher-student-card" data-student-id="${item.studentId}">
          <div class="item-head">
            <span class="item-title">${escapeHtml(item.studentName || item.displayName)}</span>
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

      document.getElementById("teacherStudentList").innerHTML = `
        ${myClassStudents.length ? `
          <section class="teacher-student-section">
            <div class="teacher-student-section-head">
              <h3 class="teacher-student-section-title">${escapeHtml(myClassTitle)}</h3>
              <span class="teacher-student-section-count">${myClassStudents.length}명</span>
            </div>
            <div class="list">${renderStudentCards(myClassStudents)}</div>
          </section>
        ` : ""}
        ${otherStudents.length ? `
          <section class="teacher-student-section">
            <div class="teacher-student-section-head">
              <h3 class="teacher-student-section-title">주일학교 전체</h3>
              <span class="teacher-student-section-count">${otherStudents.length}명</span>
            </div>
            <div class="list">${renderStudentCards(otherStudents)}</div>
          </section>
        ` : ""}
      `;

      appRoot.querySelectorAll(".teacher-student-card").forEach(btn => {
        btn.addEventListener("click", () => {
          const studentId = btn.dataset.studentId;
          navigate(buildPathWithYearMonth(`/app/teacher/students/${studentId}/calendar`, year, new Date().getMonth() + 1));
        });
      });
    }
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
    let baseQt = false;
    let baseAttitude = false;
    let baseNoteCount = 0;
    const autoAdvanceNextDay = true;
    const permissions = await resolveTeacherCheckPermissions(studentId, year);

    function getCurrentNoteCount() {
      return Number(document.getElementById("noteCountInput").value || 0);
    }

    function setCurrentNoteCount(value) {
      const safeValue = Math.max(0, Math.min(3, Number(value) || 0));
      document.getElementById("noteCountInput").value = String(safeValue);
      document.getElementById("noteCountValue").textContent = String(safeValue);
    }

    function applyCheckFieldPermissions() {
      const toggleQt = document.getElementById("toggleQt");
      const noteStepper = document.querySelector(".note-stepper");
      const qtLabel = toggleQt ? toggleQt.closest("label") : null;
      if (toggleQt) {
        toggleQt.disabled = !permissions.canEditQt;
      }
      if (qtLabel) {
        qtLabel.classList.toggle("disabled", !permissions.canEditQt);
      }
      if (noteStepper) {
        noteStepper.classList.toggle("disabled", !permissions.canEditNote);
      }
    }

    function hasUnsavedChanges() {
      if (!selectedDate) return false;
      const currentQt = document.getElementById("toggleQt").checked;
      const currentAttitude = document.getElementById("toggleAttitude").checked;
      const currentNoteCount = getCurrentNoteCount();
      return currentQt !== baseQt || currentAttitude !== baseAttitude || currentNoteCount !== baseNoteCount;
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

    function adjustSummaryAfterSave(previousQt, previousAttitude, previousNoteCount, nextQt, nextAttitude, nextNoteCount) {
      const qtDelta = (nextQt ? 1 : 0) - (previousQt ? 1 : 0);
      const attitudeDelta = (nextAttitude ? 1 : 0) - (previousAttitude ? 1 : 0);
      const noteDelta = nextNoteCount - previousNoteCount;
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
      if (!body.editable) {
        return;
      }
      if (selectedDate && selectedDate !== dateText && !confirmDiscardIfNeeded()) {
        return;
      }
      const target = (body.days || []).find(day => day.date === dateText);
      selectedDate = dateText;
      baseQt = !!(target && target.qtChecked);
      baseAttitude = !!(target && target.attitudeChecked);
      baseNoteCount = Number(target && target.noteCount ? target.noteCount : 0);
      document.getElementById("toggleQt").checked = baseQt;
      document.getElementById("toggleAttitude").checked = baseAttitude;
      setCurrentNoteCount(baseNoteCount);
      document.getElementById("checkPanel").classList.remove("hidden");
      applyCheckFieldPermissions();
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
      const currentNoteCount = getCurrentNoteCount();
      const changed = currentQt !== baseQt || currentAttitude !== baseAttitude || currentNoteCount !== baseNoteCount;
      saveButton.disabled = !changed;
      saveButton.textContent = changed ? "저장" : "변경 없음";
    }

    function renderCalendarOnly() {
      document.getElementById("calendarWrap").innerHTML = renderCalendarGrid(body.days, selectedDate);
      if (body.editable) {
        document.querySelectorAll("#calendarWrap .day-cell.selectable").forEach(btn => {
          btn.addEventListener("click", () => syncPanelFromDate(btn.dataset.date));
        });
      }
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
          <div class="box"><div class="label">🍇 QT(포도)</div><div id="summaryQtCount" class="value">${body.summary.qtCount}</div></div>
          <div class="box"><div class="label">🍐 노트(무화과)</div><div id="summaryNoteCount" class="value">${body.summary.noteCount}</div></div>
          <div class="box"><div class="label">🫒 태도(올리브)</div><div id="summaryAttitudeCount" class="value">${body.summary.attitudeCount}</div></div>
          <div class="box total-box"><div class="label">총 개수</div><div id="summaryTotalCount" class="value">${body.summary.totalCount}</div></div>
        </div>
        <div class="calendar-nav">
          <button id="btnPrevMonth" class="ghost">이전달</button>
          <div class="calendar-title">${year}년 ${month}월</div>
          <button id="btnTodayMonthTeacher" class="today-cta">오늘</button>
          <button id="btnNextMonth" class="ghost">다음달</button>
        </div>
        <div id="calendarWrap"></div>
        ${body.editable ? "" : '<div class="legend">이 연도에는 편성되지 않아 조회만 가능합니다.</div>'}
        ${renderBirthdayList(body.birthdays)}

        <div id="checkPanel" class="check-panel check-panel-fixed hidden${body.editable ? "" : " disabled"}">
          <div class="quick-row">
            <button id="btnMarkAll" class="ghost" type="button">전체 체크</button>
            <button id="btnClearAll" class="ghost" type="button">전체 해제</button>
          </div>
          <div class="switch-row">
            <label><input id="toggleQt" type="checkbox" /> QT</label>
            <span class="switch-divider" aria-hidden="true">|</span>
            <div class="note-stepper">
              <span class="note-stepper-label">노트</span>
              <button id="btnNoteMinus" class="ghost note-stepper-btn" type="button">-</button>
              <span id="noteCountValue" class="note-stepper-value">0</span>
              <button id="btnNotePlus" class="ghost note-stepper-btn" type="button">+</button>
              <input id="noteCountInput" type="hidden" value="0" />
            </div>
            <span class="switch-divider" aria-hidden="true">|</span>
            <label><input id="toggleAttitude" type="checkbox" /> 태도</label>
          </div>
          ${permissions.canEditQt && permissions.canEditNote ? "" : permissions.canEditQt ? '<div class="legend">다른 반 학생은 QT와 태도만 체크할 수 있습니다.</div>' : '<div class="legend">다른 반 학생은 태도만 체크할 수 있습니다.</div>'}
          <div class="save-row">
            <button id="btnSaveCheck">저장</button>
            <button id="btnCancelSelect" class="ghost">닫기</button>
          </div>
        </div>
        <div class="check-panel-spacer"></div>
      </section>
    `;

    renderCalendarOnly();
    applyCheckFieldPermissions();

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
      if (permissions.canEditQt) {
        document.getElementById("toggleQt").checked = true;
      }
      if (permissions.canEditNote) {
        setCurrentNoteCount(3);
      }
      document.getElementById("toggleAttitude").checked = true;
      updateSaveButtonState();
    });

    document.getElementById("btnClearAll").addEventListener("click", () => {
      if (permissions.canEditQt) {
        document.getElementById("toggleQt").checked = false;
      }
      if (permissions.canEditNote) {
        setCurrentNoteCount(0);
      }
      document.getElementById("toggleAttitude").checked = false;
      updateSaveButtonState();
    });

    document.getElementById("toggleQt").addEventListener("change", updateSaveButtonState);
    document.getElementById("toggleAttitude").addEventListener("change", updateSaveButtonState);
    document.getElementById("btnNoteMinus").addEventListener("click", () => {
      if (!permissions.canEditNote) return;
      setCurrentNoteCount(getCurrentNoteCount() - 1);
      updateSaveButtonState();
    });
    document.getElementById("btnNotePlus").addEventListener("click", () => {
      if (!permissions.canEditNote) return;
      setCurrentNoteCount(getCurrentNoteCount() + 1);
      updateSaveButtonState();
    });

    const todayIso = new Date().toISOString().slice(0, 10);
    if (body.editable && (body.days || []).some(day => day.date === todayIso)) {
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
        const previousNoteCount = Number(dayTarget && dayTarget.noteCount ? dayTarget.noteCount : 0);
        const qtChecked = document.getElementById("toggleQt").checked;
        const attitudeChecked = document.getElementById("toggleAttitude").checked;
        const noteCount = getCurrentNoteCount();
        await requestWithTeacherAuth("/api/teacher/check", {
          method: "POST",
          body: JSON.stringify({
            studentId: Number(studentId),
            year,
            date: selectedDate,
            qtChecked,
            attitudeChecked,
            noteCount
          })
        });
        if (dayTarget) {
          dayTarget.qtChecked = qtChecked;
          dayTarget.attitudeChecked = attitudeChecked;
          dayTarget.noteCount = noteCount;
        }
        baseQt = qtChecked;
        baseAttitude = attitudeChecked;
        baseNoteCount = noteCount;
        adjustSummaryAfterSave(previousQt, previousAttitude, previousNoteCount, qtChecked, attitudeChecked, noteCount);
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
        if (isTeacherPasswordChangeRequired()) {
          navigate("/app/teacher/password");
          return;
        }
        const year = await resolveTeacherYear();
        navigate(`/app/teacher/students?year=${year}`);
        return;
      }

      if (path === "/app/teacher/login") {
        if (localStorage.getItem(TEACHER_TOKEN_KEY)) {
          if (isTeacherPasswordChangeRequired()) {
            navigate("/app/teacher/password");
            return;
          }
          const year = await resolveTeacherYear();
          navigate(`/app/teacher/students?year=${year}`);
          return;
        }
        await renderTeacherLoginScreen();
        return;
      }

      if (path === "/app/teacher/students") {
        if (isTeacherPasswordChangeRequired()) {
          navigate("/app/teacher/password");
          return;
        }
        await renderTeacherStudentsScreen();
        return;
      }

      if (path === "/app/teacher/password") {
        await renderTeacherPasswordScreen();
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
    if (path === "/app/teacher/password") {
      const year = parseYear(new URLSearchParams(window.location.search).get("year"), new Date().getFullYear());
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
