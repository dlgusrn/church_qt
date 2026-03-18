package com.church.qt.domain.yearstudent;

import com.church.qt.common.BaseTimeEntity;
import com.church.qt.domain.student.Student;
import com.church.qt.domain.year.Year;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "year_students",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_year_students_year_student", columnNames = {"year_id", "student_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YearStudent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "year_id", nullable = false)
    private Year year;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "school_grade", length = 20)
    private String schoolGrade;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Builder
    public YearStudent(Year year, Student student, String schoolGrade, Integer sortOrder, Boolean active) {
        this.year = year;
        this.student = student;
        this.schoolGrade = schoolGrade;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.active = active == null || active;
    }

    public void update(String schoolGrade, Integer sortOrder, Boolean active) {
        this.schoolGrade = schoolGrade;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.active = active == null || active;
    }

    public String buildDisplayName() {
        if (schoolGrade == null || schoolGrade.isBlank()) {
            return student.getStudentName();
        }
        return student.getStudentName() + "(" + schoolGrade + "학년)";
    }
}
