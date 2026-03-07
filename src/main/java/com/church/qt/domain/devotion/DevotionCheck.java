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

    @Column(name = "note_checked", nullable = false)
    private Boolean noteChecked;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checked_by_teacher_id", nullable = false)
    private Teacher checkedByTeacher;

    @Builder
    public DevotionCheck(
            Year year,
            Student student,
            LocalDate checkDate,
            Boolean qtChecked,
            Boolean noteChecked,
            Teacher checkedByTeacher
    ) {
        this.year = year;
        this.student = student;
        this.checkDate = checkDate;
        this.qtChecked = qtChecked;
        this.noteChecked = noteChecked;
        this.checkedByTeacher = checkedByTeacher;
    }

    public void updateChecks(boolean qtChecked, boolean noteChecked, Teacher teacher) {
        this.qtChecked = qtChecked;
        this.noteChecked = noteChecked;
        this.checkedByTeacher = teacher;
    }

    public boolean isEmptyCheck() {
        return !qtChecked && !noteChecked;
    }
}