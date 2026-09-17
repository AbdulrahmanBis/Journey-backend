package com.journey.feature.journey.repository;

import com.journey.feature.journey.entity.UnitQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UnitQuizQuestionRepository extends JpaRepository<UnitQuizQuestion, String> {

    List<UnitQuizQuestion> findByUnitIdOrderByQuestionOrder(String unitId);

    List<UnitQuizQuestion> findByUnitIdIn(Collection<String> unitIds);
}
