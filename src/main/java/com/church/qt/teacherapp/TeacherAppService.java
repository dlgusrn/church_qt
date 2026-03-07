package com.church.qt.teacherapp;

import com.church.qt.domain.devotion.DevotionCheck;
import com.church.qt.domain.devotion.DevotionCheckRepository;
import com.church.qt.domain.student.Student;
import com.church.qt.domain.student.StudentRepository;
import com.church.qt.domain.teacher.Teacher;
import com.church.qt.domain.teacher.TeacherRepository;
import com.church.qt.domain.year.Year;
import com.church.qt.domain.year.YearRepository;
import com.church.qt.domain.yearclass.YearClassStudentRepository;
import com.church.qt.domain.yearclass.YearClassTeacherRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherAppService {

    private final DevotionCheckRepository devotionCheckRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final YearRepository yearRepository;
    private final YearClassStudentRepository yearClassStudentRepository;
    private final YearClassTeacherRepository yearClassTeacherRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.jwt-secret}")
    private String jwtSecret;

    @Value("${app.auth.access-token-expiration-seconds}")
    private long accessTokenExpirationSeconds;

    @Transactional(readOnly = true)
    public TeacherLoginResponse login(TeacherLoginRequest request) {
        Teacher teacher = teacherRepository.findByLoginIdAndActiveTrue(request.loginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), teacher.getPasswordHash())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = createAccessToken(teacher);
        return TeacherLoginResponse.from(teacher, accessToken);
    }

    @Transactional(readOnly = true)
    public List<TeacherStudentListResponse> getStudents(Long teacherId, Integer yearValue) {
        return yearClassTeacherRepository.findTeacherStudents(teacherId, yearValue);
    }

    @Transactional
    public void updateCheck(Long teacherId, TeacherCheckRequest request) {

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("교사가 존재하지 않습니다."));

        Year year = yearRepository.findByYearValue(request.year())
                .orElseThrow(() -> new IllegalArgumentException("연도가 존재하지 않습니다."));

        if (!yearClassStudentRepository.existsByYearIdAndStudentId(year.getId(), request.studentId())) {
            throw new IllegalArgumentException("해당 학생은 이 연도에 속하지 않습니다.");
        }

        if (!yearClassTeacherRepository.existsManageableStudent(teacherId, request.studentId(), request.year())) {
            throw new IllegalArgumentException("해당 학생은 이 교사가 관리하는 학생이 아닙니다.");
        }

        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new IllegalArgumentException("학생이 존재하지 않습니다."));

        DevotionCheck check = devotionCheckRepository
                .findByYearIdAndStudentIdAndCheckDate(year.getId(), student.getId(), request.date())
                .orElse(null);

        if (!request.qtChecked() && !request.noteChecked()) {
            if (check != null) {
                devotionCheckRepository.delete(check);
            }
            return;
        }

        if (check == null) {
            check = DevotionCheck.builder()
                    .year(year)
                    .student(student)
                    .checkDate(request.date())
                    .qtChecked(request.qtChecked())
                    .noteChecked(request.noteChecked())
                    .checkedByTeacher(teacher)
                    .build();

            devotionCheckRepository.save(check);
        } else {
            check.updateChecks(request.qtChecked(), request.noteChecked(), teacher);
        }
    }

    public Long extractTeacherId(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Long.valueOf(claims.getSubject());
        } catch (JwtException e) {
            throw new IllegalArgumentException("유효하지 않은 인증 토큰입니다.");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 인증 토큰입니다.");
        }
    }

    private String createAccessToken(Teacher teacher) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(teacher.getId()))
                .claim("teacherName", teacher.getTeacherName())
                .claim("role", teacher.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirationSeconds)))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("인증 토큰이 없습니다.");
        }

        return authorizationHeader.substring(7);
    }
}