package com.hirelink.domain.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hirelink.domain.member.entity.UserTermAgreement;
import com.hirelink.domain.member.entity.UserTermAgreementId;

public interface UserTermAgreementRepository extends JpaRepository<UserTermAgreement, UserTermAgreementId> {
}
