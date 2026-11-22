package com.hirelink.domain.student.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.student.entity.StudyProgram;

public interface StudyProgramRepository extends JpaRepository<StudyProgram, Long> {
	Optional<StudyProgram> findByProgramName(String programName);
}
