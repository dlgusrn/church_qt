package com.church.qt.domain.year;

import com.church.qt.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "years")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Year extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_value", nullable = false, unique = true)
    private Integer yearValue;

    @Column(name = "is_open_to_students", nullable = false)
    private Boolean openToStudents;

    @Column(name = "is_open_to_teachers", nullable = false)
    private Boolean openToTeachers;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "fruit_reset_date")
    private LocalDate fruitResetDate;

    @Column(name = "fruit_festival_date")
    private LocalDate fruitFestivalDate;

    @Builder
    public Year(
            Integer yearValue,
            Boolean openToStudents,
            Boolean openToTeachers,
            Boolean active
    ) {
        this.yearValue = yearValue;
        this.openToStudents = openToStudents;
        this.openToTeachers = openToTeachers;
        this.active = active;
    }

    public void updateVisibility(boolean openToStudents, boolean openToTeachers) {
        this.openToStudents = openToStudents;
        this.openToTeachers = openToTeachers;
    }

    public void updateActive(boolean active) {
        this.active = active;
    }

    public void resetFruitCounts(LocalDate resetDate) {
        this.fruitResetDate = resetDate;
    }

    public void updateFruitFestivalDate(LocalDate festivalDate) {
        this.fruitFestivalDate = festivalDate;
    }
}
