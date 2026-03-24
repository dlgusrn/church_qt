package com.church.qt.domain.teacher;

import com.church.qt.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "teachers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Teacher extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "teacher_name", nullable = false, length = 50)
    private String teacherName;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(name = "birth_date", length = 8)
    private String birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TeacherRole role;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "password_change_required", nullable = false)
    private Boolean passwordChangeRequired;

    @Column(name = "can_check_all_students", nullable = false)
    private Boolean canCheckAllStudents;

    @Builder
    public Teacher(
            String loginId,
            String passwordHash,
            String teacherName,
            String contactNumber,
            String birthDate,
            TeacherRole role,
            Boolean active,
            Boolean passwordChangeRequired,
            Boolean canCheckAllStudents
    ) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.teacherName = teacherName;
        this.contactNumber = contactNumber;
        this.birthDate = birthDate;
        this.role = role;
        this.active = active;
        this.passwordChangeRequired = passwordChangeRequired == null ? false : passwordChangeRequired;
        this.canCheckAllStudents = canCheckAllStudents == null ? false : canCheckAllStudents;
    }

    public void updateInfo(
            String teacherName,
            String contactNumber,
            String birthDate,
            TeacherRole role,
            Boolean active,
            Boolean canCheckAllStudents
    ) {
        this.teacherName = teacherName;
        this.contactNumber = contactNumber;
        this.birthDate = birthDate;
        this.role = role;
        this.active = active;
        this.canCheckAllStudents = canCheckAllStudents == null ? false : canCheckAllStudents;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updatePasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public TeacherRole getEffectiveRole() {
        return this.role == null ? TeacherRole.TEACHER : this.role;
    }

    public boolean isAdmin() {
        return getEffectiveRole() == TeacherRole.ADMIN;
    }

    public boolean canCheckAllStudents() {
        return Boolean.TRUE.equals(canCheckAllStudents);
    }
}
