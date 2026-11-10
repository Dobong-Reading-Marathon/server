package com.dobong.dobong.book;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookRegisterRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean coverUrlInvalid(String coverUrl) {
        BookRegisterRequest request = new BookRegisterRequest(1L, "어린왕자", "생텍쥐페리", 100,
                null, null, null, null, null, null, null, null, coverUrl);
        return validator.validateProperty(request, "coverUrl").size() > 0;
    }

    @Test
    @DisplayName("https:// 는 대소문자 구분 없이 허용한다")
    void allowsHttpsCaseInsensitive() {
        assertThat(coverUrlInvalid("https://image.yes24.com/a.jpg")).isFalse();
        assertThat(coverUrlInvalid("HTTPS://image.yes24.com/a.jpg")).isFalse();
    }

    @Test
    @DisplayName("null과 빈 문자열은 허용한다")
    void allowsNullAndEmpty() {
        assertThat(coverUrlInvalid(null)).isFalse();
        assertThat(coverUrlInvalid("")).isFalse();
    }

    @Test
    @DisplayName("http:// 와 공백만 있는 값은 거부한다")
    void rejectsHttpAndBlank() {
        assertThat(coverUrlInvalid("http://image.yes24.com/a.jpg")).isTrue();
        assertThat(coverUrlInvalid("   ")).isTrue();
    }

    @Test
    @DisplayName("최대 길이를 넘으면 거부한다")
    void rejectsTooLong() {
        String ok = "https://" + "a".repeat(Book.COVER_URL_MAX_LENGTH - 8);
        assertThat(coverUrlInvalid(ok)).isFalse();
        assertThat(coverUrlInvalid(ok + "a")).isTrue();
    }
}
