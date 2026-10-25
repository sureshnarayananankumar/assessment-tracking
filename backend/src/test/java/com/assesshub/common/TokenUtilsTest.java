package com.assesshub.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUtilsTest {

    @Test
    void tokensAreUniqueUrlSafeAndHashedDeterministically() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String token = TokenUtils.newToken();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(TokenUtils.looksValid(token)).isTrue();
            assertThat(seen.add(token)).isTrue();
        }
        String token = TokenUtils.newToken();
        assertThat(TokenUtils.sha256Hex(token)).hasSize(64).isEqualTo(TokenUtils.sha256Hex(token));
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(TokenUtils.looksValid(null)).isFalse();
        assertThat(TokenUtils.looksValid("short")).isFalse();
        assertThat(TokenUtils.looksValid("a".repeat(40) + "' OR 1=1 --")).isFalse();
    }

    @Test
    void parsesAndJoinsIdLists() {
        assertThat(TextUtils.parseIdList(" 3, 1,2 ")).containsExactly(3L, 1L, 2L);
        assertThat(TextUtils.joinIds(java.util.List.of(3L, 1L, 2L, 1L))).isEqualTo("1,2,3");
        assertThat(TextUtils.joinIds(java.util.List.of())).isNull();
    }
}
