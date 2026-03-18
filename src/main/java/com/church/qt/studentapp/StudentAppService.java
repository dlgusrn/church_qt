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
                .orElseThrow(() -> new IllegalArgumentException("해당 연도가 존재하지 않습니다."));

        if (!yearClassStudentRepository.existsByYearIdAndStudentId(year.getId(), studentId)) {
            throw new IllegalArgumentException("해당 학생은 선택한 연도에 편성되어 있지 않습니다.");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));
        YearStudent yearStudent = yearStudentRepository.findByYearIdAndStudentId(year.getId(), studentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 학생은 선택한 연도에 속해 있지 않습니다."));

        YearMonth yearMonth = YearMonth.of(yearValue, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        List<DevotionCheck> checks = devotionCheckRepository
                .findByYearIdAndStudentIdAndCheckDateBetweenOrderByCheckDateAsc(
                        year.getId(),
                        studentId,
                        startDate,
                        endDate
                );

        Map<LocalDate, DevotionCheck> checkMap = checks.stream()
                .collect(Collectors.toMap(DevotionCheck::getCheckDate, Function.identity()));

        List<StudentCalendarBirthdayResponse> birthdays = buildBirthdayList(year.getId(), yearMonth);
        Map<String, List<StudentCalendarBirthdayResponse>> birthdayMap = birthdays.stream()
                .collect(Collectors.groupingBy(StudentCalendarBirthdayResponse::date));

        List<StudentCalendarDayResponse> days = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    DevotionCheck check = checkMap.get(date);
                    boolean qtChecked = check != null && Boolean.TRUE.equals(check.getQtChecked());
                    boolean attitudeChecked = check != null && Boolean.TRUE.equals(check.getAttitudeChecked());
                    boolean noteChecked = check != null && Boolean.TRUE.equals(check.getNoteChecked());
                    boolean isBirthday = birthdayMap.containsKey(date.toString());

                    return new StudentCalendarDayResponse(
                            date.toString(),
                            qtChecked,
                            attitudeChecked,
                            noteChecked,
                            date.equals(today),
                            isBirthday
                    );
                })
                .toList();

        long qtCount = devotionCheckRepository.countByYearIdAndStudentIdAndQtCheckedTrue(year.getId(), studentId);
        long attitudeCount = devotionCheckRepository.countByYearIdAndStudentIdAndAttitudeCheckedTrue(year.getId(), studentId);
        long noteCount = devotionCheckRepository.countByYearIdAndStudentIdAndNoteCheckedTrue(year.getId(), studentId);

        return new StudentCalendarResponse(
                student.getId(),
                student.getStudentName(),
                yearStudent.buildDisplayName(),
                yearValue,
                month,
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
                .map(teacher -> toBirthdayResponse(teacher.getBirthDate(), yearMonth, "teacher", teacher.getTeacherName()))
                .filter(response -> response != null)
                .toList();

        List<StudentCalendarBirthdayResponse> studentBirthdays = yearStudentRepository
                .findActiveByYearIdOrderByGradeDescNameAsc(yearId).stream()
                .map(yearStudent -> toBirthdayResponse(
                        yearStudent.getStudent().getBirthDate(),
                        yearMonth,
                        "student",
                        yearStudent.getStudent().getStudentName()
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

    private StudentCalendarBirthdayResponse toBirthdayResponse(String birthDate, YearMonth yearMonth, String type, String name) {
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
        return new StudentCalendarBirthdayResponse(date.toString(), type, name);
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
