package com.hirelink.domain.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.application.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
}
