package com.printplatform.service;

import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDisplayNameServiceTest {

    @Mock private UserRepository userRepository;

    private UserDisplayNameService service;

    @BeforeEach
    void setUp() {
        service = new UserDisplayNameService(userRepository);
    }

    private User buildUser() {
        User u = new User();
        u.setEmail("test@example.com");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void resolve_showRealNameTrue_withLastName_returnsFirstNamePlusLastInitial() {
        User user = buildUser();
        user.setShowRealName(true);
        user.setFirstName("Jan");
        user.setLastName("Kowalski");

        assertThat(service.resolve(user)).isEqualTo("Jan K.");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolve_showRealNameTrue_noLastName_returnsFirstNameOnly() {
        User user = buildUser();
        user.setShowRealName(true);
        user.setFirstName("Jan");

        assertThat(service.resolve(user)).isEqualTo("Jan");
    }

    @Test
    void resolve_showRealNameTrue_noNameAtAll_returnsFallback() {
        User user = buildUser();
        user.setShowRealName(true);

        assertThat(service.resolve(user)).isEqualTo("Użytkownik");
    }

    @Test
    void resolve_showRealNameFalse_noNicknameYet_generatesAndPersists() {
        User user = buildUser();
        user.setShowRealName(false);

        String result = service.resolve(user);

        assertThat(result).isNotBlank();
        assertThat(user.getNickname()).isEqualTo(result);
        verify(userRepository).save(user);
    }

    @Test
    void resolve_showRealNameFalse_nicknameAlreadySet_reusesItWithoutSaving() {
        User user = buildUser();
        user.setShowRealName(false);
        user.setNickname("Silent42");

        assertThat(service.resolve(user)).isEqualTo("Silent42");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolve_calledTwiceForSameNicknamedUser_returnsSameNicknameBothTimes() {
        User user = buildUser();
        user.setShowRealName(false);

        String first = service.resolve(user);
        String second = service.resolve(user);

        assertThat(second).isEqualTo(first);
    }
}
