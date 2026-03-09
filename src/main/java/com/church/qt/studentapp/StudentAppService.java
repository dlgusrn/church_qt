package com.church.qt.studentapp;

import com.church.qt.domain.devotion.DevotionCheck;
import com.church.qt.domain.devotion.DevotionCheckRepository;
import com.church.qt.domain.student.Student;
import com.church.qt.domain.student.StudentRepository;
import com.church.qt.domain.year.Year;
import com.church.qt.domain.year.YearRepository;
import com.church.qt.domain.yearclass.YearClassStudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAppService {

    private final YearRepository yearRepository;
    private final StudentRepository studentRepository;
    private final YearClassStudentRepository yearClassStudentRepository;
    private final DevotionCheckRepository devotionCheckRepository;

    @Transactional(readOnly = true)
    public List<StudentListResponse> getStudents() {
        Year latestOpenYear = getLatestOpenYear();

        return yearClassStudentRepository
                .findByYearIdAndStudentActiveTrueOrderByStudentSchoolGradeDescStudentStudentNameAsc(latestOpenYear.getId())
                .stream()
                .map(yearClassStudent -> StudentListResponse.from(yearClassStudent.getStudent()))
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

        List<StudentCalendarDayResponse> days = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    DevotionCheck check = checkMap.get(date);
                    boolean qtChecked = check != null && Boolean.TRUE.equals(check.getQtChecked());
                    boolean noteChecked = check != null && Boolean.TRUE.equals(check.getNoteChecked());

                    return new StudentCalendarDayResponse(
                            date.toString(),
                            qtChecked,
                            noteChecked,
                            date.equals(today)
                    );
                })
                .toList();

        long qtCount = devotionCheckRepository.countByYearIdAndStudentIdAndQtCheckedTrue(year.getId(), studentId);
        long noteCount = devotionCheckRepository.countByYearIdAndStudentIdAndNoteCheckedTrue(year.getId(), studentId);

        return new StudentCalendarResponse(
                student.getId(),
                student.getStudentName(),
                student.getDisplayName(),
                yearValue,
                month,
                new StudentCalendarSummaryResponse(
                        qtCount,
                        noteCount,
                        qtCount + noteCount
                ),
                days
        );
    }

    private Year getLatestOpenYear() {
        return yearRepository.findFirstByOpenToStudentsTrueAndActiveTrueOrderByYearValueDesc()
                .orElseThrow(() -> new IllegalStateException("학생에게 공개된 활성 연도가 없습니다."));
    }
}
