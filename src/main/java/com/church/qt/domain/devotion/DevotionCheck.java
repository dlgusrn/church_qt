package com.church.qt.domain.devotion;

import com.church.qt.common.BaseTimeEntity;
import com.church.qt.domain.student.Student;
import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.year.Year;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "devotion_checks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_devotion_checks", columnNames = {"year_id", "student_id", "check_date"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevotionCheck extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "year_id", nullable = false)
    private Year year;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(name = "qt_checked", nullable = false)
    private Boolean qtChecked;

    @Column(name = "attitude_checked", nullable = false)
    private Boolean attitudeChecked;

    @Column(name = "note_checked", nullable = false)
    private Boolean noteChecked;

    @Column(name = "note_count", nullable = false)
    private Integer noteCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checked_by_teacher_id", nullable = false)
    private Teacher checkedByTeacher;

    @Builder
    public DevotionCheck(
            Year year,
            Student student,
            LocalDate checkDate,
            Boolean qtChecked,
            Boolean attitudeChecked,
            Boolean noteChecked,
            Integer noteCount,
            Teacher checkedByTeacher
    ) {
        this.year = year;
        this.student = student;
        this.checkDate = checkDate;
        this.qtChecked = qtChecked;
        this.attitudeChecked = attitudeChecked;
        this.noteCount = normalizeNoteCount(noteCount, noteChecked);
        this.noteChecked = this.noteCount > 0;
        this.checkedByTeacher = checkedByTeacher;
    }

    public void updateChecks(boolean qtChecked, boolean attitudeChecked, int noteCount, Teacher teacher) {
        this.qtChecked = qtChecked;
        this.attitudeChecked = attitudeChecked;
        this.noteCount = normalizeNoteCount(noteCount, noteCount > 0);
        this.noteChecked = this.noteCount > 0;
        this.checkedByTeacher = teacher;
    }

    public boolean isEmptyCheck() {
        return !qtChecked && !attitudeChecked && noteCount == 0;
    }

    private int normalizeNoteCount(Integer noteCount, Boolean noteChecked) {
        if (noteCount != null) {
            return Math.max(0, Math.min(3, noteCount));
        }
        return Boolean.TRUE.equals(noteChecked) ? 1 : 0;
    }
}
