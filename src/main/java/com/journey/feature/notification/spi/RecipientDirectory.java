package com.journey.feature.notification.spi;

import java.util.Optional;

/**
 * How the notification module turns a user id into someone it can actually contact.
 *
 * <p>This is the module's <em>outbound</em> port, the mirror of
 * {@code api.NotificationDispatcher}: {@code api} is what other features call, {@code spi} is what
 * another feature implements for us. Both keep the dependency pointing at this module rather than
 * away from it — the module declares what it needs and never imports {@code feature.user} to get it.
 *
 * <p>That matters for the eventual split. A separate notification service has no {@code users}
 * table; it would satisfy this same interface by calling the identity provider or the user API, and
 * nothing else in the module changes.
 */
public interface RecipientDirectory {

    /** Empty when the id is unknown — a deleted user should not fail a whole dispatch. */
    Optional<Recipient> find(String userId);

    /**
     * @param language BCP-47-ish tag ({@code "en"}, {@code "ar"}), or null when the recipient has
     *                 expressed no preference, in which case the configured default is used
     */
    record Recipient(String userId, String name, String email, String language) {}
}
