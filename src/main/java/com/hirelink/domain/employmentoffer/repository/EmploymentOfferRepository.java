package com.hirelink.domain.employmentoffer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;

public interface EmploymentOfferRepository extends JpaRepository<EmploymentOffer, Long> {
}
