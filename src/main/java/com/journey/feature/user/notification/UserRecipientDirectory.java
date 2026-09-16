package com.journey.feature.user.notification;

import com.journey.feature.notification.spi.RecipientDirectory;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Satisfies the notification module's recipient lookup from the {@code users} table.
 *
 * <p>Lives in the user feature, not the notification one: the adapter belongs with the data it
 * adapts, and this way {@code feature.notification} still imports nothing from
 * {@code feature.user}. Replacing the users table with Entra ID later means rewriting this class
 * and nothing else.
 */
@Component
@RequiredArgsConstructor
public class UserRecipientDirectory implements RecipientDirectory {

    private final UserRepository userRepository;

    @Override
    public Optional<Recipient> find(String userId) {
        return userRepository.findById(userId)
                .map(user -> new Recipient(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getPreferredLanguage()));
    }
}
