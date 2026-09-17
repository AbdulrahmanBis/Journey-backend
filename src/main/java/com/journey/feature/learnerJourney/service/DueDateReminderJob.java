package com.journey.feature.learnerJourney.service;

import com.journey.common.enums.ItemStatus;
import com.journey.feature.journey.entity.Journey;
import com.journey.feature.journey.repository.JourneyRepository;
import com.journey.feature.learnerJourney.entity.LearnerJourney;
import com.journey.feature.learnerJourney.event.JourneyDueSoonEvent;
import com.journey.feature.learnerJourney.event.JourneyOverdueEvent;
import com.journey.feature.learnerJourney.repository.LearnerJourneyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sends due-date reminders: "due soon" to the learner {@value #DUE_SOON_DAYS} days ahead, and "overdue"
 * to the learner and their assigner the day after the deadline.
 *
 * <p>Runs hourly and once at startup, so a stopped server catches up when it comes back. Each reminder
 * is recorded on the learner journey and goes out once per due date; moving the due date clears the
 * record (LearnerJourneyService.updateDueDate). A journey found already overdue gets only the overdue
 * reminder, not a stale "due soon".
 *
 * <p>Events are published inside the transaction, so the notifier (AFTER_COMMIT) only announces
 * reminders whose record was saved. Turn off with {@code app.reminders.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class DueDateReminderJob {

    static final int DUE_SOON_DAYS = 2;

    private final LearnerJourneyRepository learnerJourneyRepository;
    private final JourneyRepository journeyRepository;
    private final ApplicationEventPublisher events;
    /** Explicit, because the startup call reaches run() from inside this class, where @Transactional would not apply. */
    private final TransactionTemplate transactions;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${app.reminders.cron:0 0 * * * *}")
    public void run() {
        transactions.executeWithoutResult(status -> sendDue());
    }

    private void sendDue() {
        LocalDate today = LocalDate.now();
        List<LearnerJourney> candidates = learnerJourneyRepository.findByDueDateLessThanEqualAndStatusNotIn(
                today.plusDays(DUE_SOON_DAYS), List.of(ItemStatus.COMPLETED.getCode(), ItemStatus.CANCELLED.getCode()));

        int sent = 0;
        for (LearnerJourney lj : candidates) {
            String title = journeyRepository.findById(lj.getJourneyId()).map(Journey::getTitle).orElse("");
            if (lj.getDueDate().isBefore(today)) {
                if (lj.getOverdueNotifiedAt() != null) continue;
                lj.setOverdueNotifiedAt(LocalDateTime.now());
                lj.setDueSoonNotifiedAt(lj.getDueSoonNotifiedAt() != null ? lj.getDueSoonNotifiedAt() : LocalDateTime.now());
                events.publishEvent(new JourneyOverdueEvent(lj.getId(), title, lj.getLearnerId(), lj.getAssignedById(), lj.getDueDate()));
            } else {
                if (lj.getDueSoonNotifiedAt() != null) continue;
                lj.setDueSoonNotifiedAt(LocalDateTime.now());
                events.publishEvent(new JourneyDueSoonEvent(lj.getId(), title, lj.getLearnerId(), lj.getDueDate(),
                        ChronoUnit.DAYS.between(today, lj.getDueDate())));
            }
            learnerJourneyRepository.save(lj);
            sent++;
        }
        if (sent > 0) log.info("Due-date reminders sent for {} journey(s)", sent);
    }
}
