package com.church.qt.studentapp;

import com.church.qt.domain.devotion.DevotionCheck;
import com.church.qt.domain.devotion.DevotionCheckRepository;
import com.church.qt.domain.student.Student;
import com.church.qt.domain.student.StudentRepository;
import com.church.qt.domain.year.Year;
import com.church.qt.domain.year.YearRepository;
import com.church.qt.domain.yearclass.YearClassStudentRepository;
import com.church.qt.domain.teacher.TeacherRepository;
import com.church.qt.domain.yearstudent.YearStudent;
import com.church.qt.domain.yearstudent.YearStudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAppService {

    private final YearRepository yearRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final YearClassStudentRepository yearClassStudentRepository;
    private final YearStudentRepository yearStudentRepository;
    private final DevotionCheckRepository devotionCheckRepository;

    @Transactional(readOnly = true)
    public List<StudentListResponse> getStudents() {
        Year latestOpenYear = getLatestOpenYear();

        return yearStudentRepository
                .findActiveByYearIdOrderByGradeDescNameAsc(latestOpenYear.getId())
                .stream()
                .map(StudentListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentCurrentYearResponse getCurrentYear() {
        return new StudentCurrentYearResponse(getLatestOpenYear().getYearValue());
    }

    @Transactional(readOnly = true)
    public StudentCalendarResponse getStudentCalendar(Long studentId, Integer yearValue, Integer month) {
        Year year = yearRepository.findByYearValue(yearValue)
                .orElse(null);
        Long yearId = year == null ? null : year.getId();

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));
        boolean editable = yearId != null && yearClassStudentRepository.existsByYearIdAndStudentId(yearId, studentId);
        YearStudent yearStudent = yearId == null ? null : yearStudentRepository.findByYearIdAndStudentId(yearId, studentId)
                .orElse(null);

        YearMonth yearMonth = YearMonth.of(yearValue, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        List<DevotionCheck> checks = yearId == null
                ? List.of()
                : devotionCheckRepository.findByYearIdAndStudentIdAndCheckDateBetweenOrderByCheckDateAsc(
                        yearId,
                        studentId,
                        startDate,
                        endDate
                );

        Map<LocalDate, DevotionCheck> checkMap = checks.stream()
                .collect(Collectors.toMap(DevotionCheck::getCheckDate, Function.identity()));

        List<StudentCalendarBirthdayResponse> birthdays = buildBirthdayList(yearId, yearMonth);
        Map<String, List<StudentCalendarBirthdayResponse>> birthdayMap = birthdays.stream()
                .collect(Collectors.groupingBy(StudentCalendarBirthdayResponse::date));

        List<StudentCalendarDayResponse> days = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    DevotionCheck check = checkMap.get(date);
                    boolean qtChecked = check != null && Boolean.TRUE.equals(check.getQtChecked());
                    boolean attitudeChecked = check != null && Boolean.TRUE.equals(check.getAttitudeChecked());
                    int noteCount = check == null ? 0 : Math.max(0, check.getNoteCount());
                    boolean isBirthday = birthdayMap.containsKey(date.toString());

                    return new StudentCalendarDayResponse(
                            date.toString(),
                            qtChecked,
                            attitudeChecked,
                            noteCount,
                            date.equals(today),
                            isBirthday
                    );
                })
                .toList();

        long qtCount = yearId == null ? 0L : devotionCheckRepository.countByYearIdAndStudentIdAndQtCheckedTrue(yearId, studentId);
        long attitudeCount = yearId == null ? 0L : devotionCheckRepository.countByYearIdAndStudentIdAndAttitudeCheckedTrue(yearId, studentId);
        long noteCount = yearId == null ? 0L : devotionCheckRepository.sumNoteCountByYearIdAndStudentId(yearId, studentId);

        return new StudentCalendarResponse(
                student.getId(),
                student.getStudentName(),
                yearStudent == null ? student.getStudentName() : yearStudent.buildDisplayName(),
                yearValue,
                month,
                editable,
                new StudentCalendarSummaryResponse(
                        qtCount,
                        attitudeCount,
                        noteCount,
                        qtCount + attitudeCount + noteCount
                ),
                days,
                birthdays
        );
    }

    private List<StudentCalendarBirthdayResponse> buildBirthdayList(Long yearId, YearMonth yearMonth) {
        List<StudentCalendarBirthdayResponse> teacherBirthdays = teacherRepository.findByActiveTrueOrderByTeacherNameAscIdAsc().stream()
                .map(teacher -> toBirthdayResponse(
                        teacher.getBirthDate(),
                        yearMonth,
                        "teacher",
                        teacher.getTeacherName(),
                        teacher.getEffectiveRole().name()
                ))
                .filter(response -> response != null)
                .toList();

        List<StudentCalendarBirthdayResponse> studentBirthdays = yearId == null
                ? List.of()
                : yearStudentRepository.findActiveByYearIdOrderByGradeDescNameAsc(yearId).stream()
                        .map(yearStudent -> toBirthdayResponse(
                                yearStudent.getStudent().getBirthDate(),
                                yearMonth,
                                "student",
                                yearStudent.getStudent().getStudentName(),
                                null
                        ))
                        .filter(response -> response != null)
                        .toList();

        return java.util.stream.Stream.concat(teacherBirthdays.stream(), studentBirthdays.stream())
                .sorted(Comparator
                        .comparingInt((StudentCalendarBirthdayResponse response) -> "teacher".equals(response.type()) ? 0 : 1)
                        .thenComparing(StudentCalendarBirthdayResponse::date)
                        .thenComparing(StudentCalendarBirthdayResponse::name))
                .toList();
    }

    private StudentCalendarBirthdayResponse toBirthdayResponse(String birthDate, YearMonth yearMonth, String type, String name, String role) {
        String monthDay = extractMonthDay(birthDate);
        if (monthDay == null) {
            return null;
        }

        int month = Integer.parseInt(monthDay.substring(0, 2));
        int day = Integer.parseInt(monthDay.substring(2, 4));
        if (month != yearMonth.getMonthValue() || day < 1 || day > yearMonth.lengthOfMonth()) {
            return null;
        }

        LocalDate date = yearMonth.atDay(day);
        return new StudentCalendarBirthdayResponse(date.toString(), type, name, role);
    }

    private String extractMonthDay(String birthDate) {
        String digits = birthDate == null ? "" : birthDate.replaceAll("[^0-9]", "");
        if (digits.length() != 6 && digits.length() != 8) {
            return null;
        }
        return digits.substring(digits.length() - 4);
    }

    private Year getLatestOpenYear() {
        return yearRepository.findFirstByOpenToStudentsTrueAndActiveTrueOrderByYearValueDesc()
                .orElseThrow(() -> new IllegalStateException("학생에게 공개된 활성 연도가 없습니다."));
    }
}
