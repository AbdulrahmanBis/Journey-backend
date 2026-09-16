package com.journey.feature.journeyPackage.service;

import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.journey.entity.Journey;
import com.journey.feature.journey.service.JourneyService;
import com.journey.feature.journeyPackage.dto.PackageDto;
import com.journey.feature.journeyPackage.dto.PackageJourneyDto;
import com.journey.feature.journeyPackage.dto.SavePackageRequest;
import com.journey.feature.journeyPackage.entity.JourneyPackage;
import com.journey.feature.journeyPackage.entity.PackageJourney;
import com.journey.feature.journeyPackage.repository.JourneyPackageRepository;
import com.journey.feature.journeyPackage.repository.PackageAssignmentRepository;
import com.journey.feature.journeyPackage.repository.PackageJourneyRepository;
import com.journey.feature.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * Package definitions. Like journeys they are company-wide: anyone signed in can read them, and
 * the same staff roles that author journeys (Senior, Manager, HR, Admin) author packages.
 */
@Service
@RequiredArgsConstructor
public class PackageService {

    private final JourneyPackageRepository packageRepository;
    private final PackageJourneyRepository packageJourneyRepository;
    private final PackageAssignmentRepository assignmentRepository;
    private final JourneyService journeyService;
    private final IdGeneratorService idGenerator;
    private final AccessPolicy access;

    public List<PackageDto> list() {
        access.actor();
        return packageRepository.findAllByOrderByTitleAsc().stream().map(this::toDto).toList();
    }

    public PackageDto get(String id) {
        access.actor();
        return toDto(requirePackage(id));
    }

    @Transactional
    public PackageDto create(SavePackageRequest req) {
        User author = access.requireRole(AccessPolicy.STAFF);
        List<String> journeyIds = validJourneyIds(req.journeyIds());
        JourneyPackage saved = packageRepository.save(JourneyPackage.builder()
                .id(idGenerator.next(IdGeneratorService.PACKAGE, "pkg-"))
                .title(req.title().trim())
                .description(blankToNull(req.description()))
                .createdById(author.getId())
                .createdByName(author.getName())
                .build());
        saveJourneys(saved.getId(), journeyIds);
        return toDto(saved);
    }

    /** Changes apply to future assignments only; learners who already have the package keep theirs. */
    @Transactional
    public PackageDto update(String id, SavePackageRequest req) {
        access.requireRole(AccessPolicy.STAFF);
        JourneyPackage pkg = requirePackage(id);
        List<String> journeyIds = validJourneyIds(req.journeyIds());
        pkg.setTitle(req.title().trim());
        pkg.setDescription(blankToNull(req.description()));
        pkg.setUpdatedAt(LocalDateTime.now());
        packageRepository.save(pkg);
        packageJourneyRepository.deleteByPackageId(id);
        packageJourneyRepository.flush();
        saveJourneys(id, journeyIds);
        return toDto(pkg);
    }

    /** Refused while any assignment exists, cancelled ones included: they are the learner's history. */
    @Transactional
    public void delete(String id) {
        access.requireRole(AccessPolicy.STAFF);
        JourneyPackage pkg = requirePackage(id);
        if (assignmentRepository.countByPackageId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This package has been assigned, so it can't be deleted.");
        }
        packageJourneyRepository.deleteByPackageId(id);
        packageRepository.delete(pkg);
    }

    // ─── Used by assignments ──────────────────────────────────────────────────

    public JourneyPackage requirePackage(String id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found: " + id));
    }

    public List<PackageJourney> journeysOf(String packageId) {
        return packageJourneyRepository.findByPackageIdOrderByPosition(packageId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Every id must be a real journey, and each may appear once. */
    private List<String> validJourneyIds(List<String> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A journey can only appear once in a package.");
        }
        ids.forEach(journeyService::getEntityById); // 404 for an unknown journey
        return ids;
    }

    private void saveJourneys(String packageId, List<String> journeyIds) {
        for (int i = 0; i < journeyIds.size(); i++) {
            packageJourneyRepository.save(PackageJourney.builder()
                    .packageId(packageId)
                    .journeyId(journeyIds.get(i))
                    .position(i + 1)
                    .build());
        }
    }

    private PackageDto toDto(JourneyPackage p) {
        List<PackageJourneyDto> journeys = journeysOf(p.getId()).stream()
                .map(pj -> {
                    Journey j = journeyService.getEntityById(pj.getJourneyId());
                    return new PackageJourneyDto(j.getId(), j.getTitle(), j.getTechTag(), pj.getPosition());
                })
                .toList();
        return new PackageDto(p.getId(), p.getTitle(), p.getDescription(), p.getCreatedById(),
                p.getCreatedByName(), p.getCreatedAt(), p.getUpdatedAt(), journeys,
                assignmentRepository.countByPackageIdAndCancelledAtIsNull(p.getId()),
                assignmentRepository.countByPackageId(p.getId()) == 0);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
