package com.church.qt.domain.yearclass;

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
        name = "year_class_students",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ycs_student_per_year", columnNames = {"year_id", "student_id"}),
                @UniqueConstraint(name = "uq_ycs_year_class_student", columnNames = {"year_class_id", "student_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YearClassStudent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "year_id", nullable = false)
    private Year year;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "year_class_id", nullable = false)
    private YearClass yearClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Builder
    public YearClassStudent(
            Year year,
            YearClass yearClass,
            Student student
    ) {
        this.year = year;
        this.yearClass = yearClass;
        this.student = student;
    }

    public void moveToYearClass(YearClass yearClass) {
        this.yearClass = yearClass;
        this.year = yearClass.getYear();
    }
}
