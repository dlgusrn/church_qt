package com.church.qt.domain.yearteacher;

import com.church.qt.common.BaseTimeEntity;
import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.year.Year;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "year_teachers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_year_teachers_year_teacher", columnNames = {"year_id", "teacher_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YearTeacher extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "year_id", nullable = false)
    private Year year;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Builder
    public YearTeacher(Year year, Teacher teacher, Integer sortOrder, Boolean active) {
        this.year = year;
        this.teacher = teacher;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.active = active == null || active;
    }

    public void update(Integer sortOrder, Boolean active) {
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.active = active == null || active;
    }
}
