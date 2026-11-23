package com.hirelink.domain.student.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.hirelink.domain.student.entity.StudentDocumentCleanup;

public interface StudentDocumentCleanupRepository extends JpaRepository<StudentDocumentCleanup, String> {
	List<StudentDocumentCleanup> findAllByObjectKeyStartingWith(String prefix);
	List<StudentDocumentCleanup> findTop20ByAvailableAtBeforeOrderByAvailableAt(Instant now);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from StudentDocumentCleanup c where c.objectKey = :key and c.availableAt < :now")
	Optional<StudentDocumentCleanup> findDueForUpdate(@Param("key") String key, @Param("now") Instant now);
}
