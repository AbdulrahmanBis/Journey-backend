package com.journey.feature.notification.api;

/**
 * The seam between the rest of the application and notifications.
 *
 * <p>This interface plus {@link NotificationRequest} is the <em>entire</em> surface other features
 * may use. Nothing outside {@code feature.notification} imports anything else from the module, and
 * the module imports nothing from them — the dependency points one way, into this package.
 *
 * <p>That is deliberate: the BRD calls for notifications as a separate Spring service. Splitting it
 * out later means writing a second implementation of this one method that posts the request over
 * HTTP, and moving the module into its own build. No caller changes. Until the notification store
 * gets its own database there is nothing to gain from paying for that network hop, so the shipped
 * implementation is in-process.
 *
 * <p>Implementations must not throw. A notification that cannot be produced is a logged failure,
 * never a failed assignment — see {@code LocalNotificationDispatcher}.
 */
public interface NotificationDispatcher {

    /**
     * Renders the named template for whoever its config says should receive it, and delivers it on
     * the channels the config lists.
     */
    void dispatch(NotificationRequest request);
}
