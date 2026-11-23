package com.hirelink.domain.student.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hirelink.domain.student.entity.StudentEnrollmentDocument;
import com.hirelink.domain.student.entity.StudentDocumentId;

public interface StudentEnrollmentDocumentRepository extends JpaRepository<StudentEnrollmentDocument, StudentDocumentId> {
    default Optional<StudentEnrollmentDocument> findById(Long userId) { return findById(new StudentDocumentId(userId,"ENROLLMENT")); }
    List<StudentEnrollmentDocument> findAllByUserId(Long userId);
}
