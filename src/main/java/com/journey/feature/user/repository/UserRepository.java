package com.journey.feature.user.repository;

import com.journey.feature.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(Integer role);
    List<User> findByRoleAndSeniorId(Integer role, String seniorId);

    List<User> findByDepartmentId(String departmentId);
    List<User> findByRoleAndDepartmentId(Integer role, String departmentId);
    long countByDepartmentId(String departmentId);
}
