package com.journey.feature.department.repository;

import com.journey.feature.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, String> {

    List<Department> findAllByOrderByNameEnAsc();

    boolean existsByNameEnIgnoreCase(String nameEn);

    boolean existsByNameAr(String nameAr);

    boolean existsByNameEnIgnoreCaseAndIdNot(String nameEn, String id);

    boolean existsByNameArAndIdNot(String nameAr, String id);
}
