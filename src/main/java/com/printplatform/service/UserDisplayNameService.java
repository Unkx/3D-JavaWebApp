package com.printplatform.service;

import com.printplatform.model.User;
import com.printplatform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class UserDisplayNameService {

    private static final String[] ADJECTIVES = {
            "Swift", "Silent", "Crazy", "Bold", "Calm", "Bright", "Lucky", "Quiet",
            "Sharp", "Wild", "Steady", "Clever"
    };

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public UserDisplayNameService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Real "First L." or a stable, lazily-generated nickname, per the user's own privacy toggle. */
    public String resolve(User user) {
        if (user.isShowRealName()) {
            return realNameDisplay(user);
        }
        if (user.getNickname() == null) {
            user.setNickname(generateNickname());
            userRepository.save(user);
        }
        return user.getNickname();
    }

    private String realNameDisplay(User user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        if (first == null || first.isBlank()) {
            return "Użytkownik";
        }
        if (last == null || last.isBlank()) {
            return first;
        }
        return first + " " + last.charAt(0) + ".";
    }

    private String generateNickname() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        int number = 10 + random.nextInt(90);
        return adjective + number;
    }
}
