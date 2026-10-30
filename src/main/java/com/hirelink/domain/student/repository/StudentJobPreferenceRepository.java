package com.hirelink.domain.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.StudentJobPreference;

public interface StudentJobPreferenceRepository extends JpaRepository<StudentJobPreference, Long> {
}
