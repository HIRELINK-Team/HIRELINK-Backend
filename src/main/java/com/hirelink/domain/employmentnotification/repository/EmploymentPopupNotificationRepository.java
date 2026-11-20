package com.hirelink.domain.employmentnotification.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.employmentnotification.entity.EmploymentPopupNotification;

public interface EmploymentPopupNotificationRepository extends JpaRepository<EmploymentPopupNotification, Long> {

	List<EmploymentPopupNotification> findAllByRecipientIdAndConfirmedAtIsNullOrderByCreatedAtAscIdAsc(
			Long recipientId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select notification from EmploymentPopupNotification notification where notification.id = :id")
	Optional<EmploymentPopupNotification> findByIdForUpdate(@Param("id") Long id);
}
