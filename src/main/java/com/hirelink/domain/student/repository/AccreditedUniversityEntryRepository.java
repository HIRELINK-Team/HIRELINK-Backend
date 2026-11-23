package com.hirelink.domain.student.repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hirelink.domain.student.entity.AccreditedUniversityEntry;

public interface AccreditedUniversityEntryRepository extends JpaRepository<AccreditedUniversityEntry, Long> {
    long countByEffectiveFrom(LocalDate effectiveFrom);
    boolean existsByEffectiveFromAndNormalizedName(LocalDate effectiveFrom, String normalizedName);
}
