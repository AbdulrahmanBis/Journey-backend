package com.journey.feature.department.service;

import com.journey.common.error.ApiException;
import com.journey.common.error.ErrorCode;
import com.journey.common.enums.UserRole;
import com.journey.common.security.AccessPolicy;
import com.journey.common.service.IdGeneratorService;
import com.journey.feature.department.dto.DepartmentDto;
import com.journey.feature.department.dto.SaveDepartmentRequest;
import com.journey.feature.department.entity.Department;
import com.journey.feature.department.repository.DepartmentRepository;
import com.journey.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final IdGeneratorService idGenerator;
    private final AccessPolicy access;

    /** Everyone may read the list — it labels people and fills pickers. */
    public List<DepartmentDto> list() {
        return departmentRepository.findAllByOrderByNameEnAsc().stream()
                .map(d -> toDto(d, userRepository.countByDepartmentId(d.getId())))
                .toList();
    }

    /** Admin and HR own the organisation's structure. */
    @Transactional
    public DepartmentDto create(SaveDepartmentRequest req) {
        access.requireRole(UserRole.ADMIN, UserRole.HR);
        String english = req.english().trim();
        String arabic = req.arabic().trim();
        if (departmentRepository.existsByNameEnIgnoreCase(english) || departmentRepository.existsByNameAr(arabic)) {
            throw new ApiException(ErrorCode.DEPARTMENT_NAME_TAKEN);
        }
        Department saved = departmentRepository.save(Department.builder()
                .id(idGenerator.next(IdGeneratorService.DEPARTMENT, "dep-"))
                .nameEn(english)
                .nameAr(arabic)
                .build());
        return toDto(saved, 0L);
    }

    @Transactional
    public DepartmentDto rename(String id, SaveDepartmentRequest req) {
        access.requireRole(UserRole.ADMIN, UserRole.HR);
        Department department = findOrThrow(id);
        String english = req.english().trim();
        String arabic = req.arabic().trim();
        if (departmentRepository.existsByNameEnIgnoreCaseAndIdNot(english, id)
                || departmentRepository.existsByNameArAndIdNot(arabic, id)) {
            throw new ApiException(ErrorCode.DEPARTMENT_NAME_TAKEN);
        }
        department.setNameEn(english);
        department.setNameAr(arabic);
        return toDto(departmentRepository.save(department), userRepository.countByDepartmentId(id));
    }

    /**
     * Only an empty department can be deleted. Silently moving its people somewhere would change
     * who their manager is without anyone deciding that, so the caller moves them first.
     */
    @Transactional
    public void delete(String id) {
        access.requireRole(UserRole.ADMIN, UserRole.HR);
        Department department = findOrThrow(id);
        long members = userRepository.countByDepartmentId(id);
        if (members > 0) {
            throw new ApiException(ErrorCode.DEPARTMENT_HAS_MEMBERS, members);
        }
        departmentRepository.delete(department);
    }

    public Department findOrThrow(String id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    /** 400 rather than a foreign-key 500 when a client sends an id that does not exist. */
    public void requireExists(String id) {
        if (id == null || id.isBlank() || !departmentRepository.existsById(id)) {
            throw new ApiException(ErrorCode.UNKNOWN_DEPARTMENT);
        }
    }

    public DepartmentDto toDto(Department d, Long memberCount) {
        return new DepartmentDto(d.getId(), d.getNameEn(), d.getNameAr(), memberCount);
    }
}
