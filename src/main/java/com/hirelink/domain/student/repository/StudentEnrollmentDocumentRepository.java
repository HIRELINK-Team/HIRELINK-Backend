package com.hirelink.domain.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.hirelink.domain.student.entity.StudentEnrollmentDocument;

public interface StudentEnrollmentDocumentRepository extends JpaRepository<StudentEnrollmentDocument, Long> { }
