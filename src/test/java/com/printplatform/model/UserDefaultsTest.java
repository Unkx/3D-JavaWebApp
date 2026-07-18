package com.printplatform.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDefaultsTest {

    @Test
    void newUser_hasExpectedPrivacyAndAvatarDefaults() {
        User user = new User();

        assertThat(user.isShowRealName()).isTrue();
        assertThat(user.isShowCity()).isFalse();
        assertThat(user.isAvatarImportSkipped()).isFalse();
        assertThat(user.getAvatarData()).isNull();
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getGoogleAvatarUrl()).isNull();
        assertThat(user.getNickname()).isNull();
        assertThat(user.getLastLoginAt()).isNull();
    }
}
