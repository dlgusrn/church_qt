package com.church.qt.domain.yearclass;

import com.church.qt.common.BaseTimeEntity;
import com.church.qt.domain.teacher.Teacher;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "year_class_teachers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_yct", columnNames = {"year_class_id", "teacher_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YearClassTeacher extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "year_class_id", nullable = false)
    private YearClass yearClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "assignment_role", nullable = false, length = 20)
    private String assignmentRole;

    @Builder
    public YearClassTeacher(YearClass yearClass, Teacher teacher, String assignmentRole) {
        this.yearClass = yearClass;
        this.teacher = teacher;
        this.assignmentRole = assignmentRole == null || assignmentRole.isBlank() ? "ASSISTANT" : assignmentRole;
    }

    public void updateAssignmentRole(String assignmentRole) {
        this.assignmentRole = assignmentRole == null || assignmentRole.isBlank() ? "ASSISTANT" : assignmentRole;
    }
}
