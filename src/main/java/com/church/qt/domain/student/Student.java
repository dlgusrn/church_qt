package com.church.qt.domain.student;

import com.church.qt.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "students")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_name", nullable = false, length = 50)
    private String studentName;

    @Column(name = "school_grade")
    private Integer schoolGrade;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(name = "birth_date", length = 8)
    private String birthDate;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Builder
    public Student(
            String studentName,
            Integer schoolGrade,
            String contactNumber,
            String birthDate,
            Boolean active
    ) {
        this.studentName = studentName;
        this.schoolGrade = schoolGrade;
        this.contactNumber = contactNumber;
        this.birthDate = birthDate;
        this.active = active;
    }

    public void updateInfo(
            String studentName,
            Integer schoolGrade,
            String contactNumber,
            String birthDate,
            Boolean active
    ) {
        this.studentName = studentName;
        this.schoolGrade = schoolGrade;
        this.contactNumber = contactNumber;
        this.birthDate = birthDate;
        this.active = active;
    }

    public String getDisplayName() {
        if (schoolGrade == null) {
            return studentName;
        }
        return studentName + "(" + schoolGrade + "학년)";
    }
}
