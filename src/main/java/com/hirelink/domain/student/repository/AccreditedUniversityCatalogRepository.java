package com.hirelink.domain.student.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hirelink.domain.student.entity.AccreditedUniversityCatalog;

public interface AccreditedUniversityCatalogRepository extends JpaRepository<AccreditedUniversityCatalog, LocalDate> {
    Optional<AccreditedUniversityCatalog> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate today);
}
