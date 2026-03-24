package com.church.qt.common;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PasswordPolicyValidator {
    private static final int MIN_LENGTH = 6;
    private static final String[] FORBIDDEN_SEQUENCES = {
            "0123456789",
            "1234567890",
            "abcdefghijklmnopqrstuvwxyz",
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
    };

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력하세요.");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 최소 6자리 이상이어야 합니다.");
        }
        if (isSameCharacterRepeated(rawPassword)) {
            throw new IllegalArgumentException("같은 문자만 반복된 비밀번호는 사용할 수 없습니다.");
        }
        if (containsKeyboardSequence(rawPassword)) {
            throw new IllegalArgumentException("키보드 배열이나 연속된 문자가 포함된 쉬운 비밀번호는 사용할 수 없습니다.");
        }
    }

    private boolean isSameCharacterRepeated(String password) {
        char first = password.charAt(0);
        for (int i = 1; i < password.length(); i += 1) {
            if (password.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private boolean containsKeyboardSequence(String password) {
        String normalized = password.toLowerCase(Locale.ROOT);
        for (String sequence : FORBIDDEN_SEQUENCES) {
            if (containsSequence(normalized, sequence)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSequence(String password, String source) {
        if (password.length() < MIN_LENGTH) {
            return false;
        }
        for (int i = 0; i <= source.length() - MIN_LENGTH; i += 1) {
            String chunk = source.substring(i, i + MIN_LENGTH);
            String reversed = new StringBuilder(chunk).reverse().toString();
            if (password.contains(chunk) || password.contains(reversed)) {
                return true;
            }
        }
        return false;
    }
}
