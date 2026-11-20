package com.hirelink.domain.employmentnotification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hirelink.domain.application.entity.Application;
import com.hirelink.domain.employmentnotification.dto.EmploymentPopupNotificationConfirmResponse;
import com.hirelink.domain.employmentnotification.dto.EmploymentPopupNotificationItemResponse;
import com.hirelink.domain.employmentnotification.dto.EmploymentPopupNotificationListResponse;
import com.hirelink.domain.employmentnotification.entity.EmploymentPopupNotification;
import com.hirelink.domain.employmentnotification.entity.EmploymentPopupNotificationType;
import com.hirelink.domain.employmentnotification.repository.EmploymentPopupNotificationRepository;
import com.hirelink.domain.employmentoffer.entity.EmploymentOffer;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.global.exception.EmploymentPopupNotificationNotFoundException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class EmploymentPopupNotificationService {

	private final EmploymentPopupNotificationRepository notifications;
	private final UserAccountRepository users;
	private final Clock clock;

	public EmploymentPopupNotificationService(EmploymentPopupNotificationRepository notifications,
			UserAccountRepository users, Clock clock) {
		this.notifications = notifications;
		this.users = users;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void applicationCreated(Application application) {
		notifications.saveAndFlush(EmploymentPopupNotification.forApplication(
				application.getJobPosting().getWorkplace().getEmployerUser(),
				EmploymentPopupNotificationType.APPLICATION_CREATED, application, now()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void applicationRejected(Application application) {
		notifications.saveAndFlush(EmploymentPopupNotification.forApplication(
				application.getStudentProfile().getUserAccount(),
				EmploymentPopupNotificationType.APPLICATION_REJECTED, application, now()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void offerCreated(EmploymentOffer offer) {
		notifications.saveAndFlush(EmploymentPopupNotification.forOffer(
				offer.getApplication().getStudentProfile().getUserAccount(),
				EmploymentPopupNotificationType.OFFER_CREATED, offer, now()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void offerAccepted(EmploymentOffer offer) {
		notifications.saveAndFlush(EmploymentPopupNotification.forOffer(
				offer.getApplication().getJobPosting().getWorkplace().getEmployerUser(),
				EmploymentPopupNotificationType.OFFER_ACCEPTED, offer, now()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void offerRejected(EmploymentOffer offer) {
		notifications.saveAndFlush(EmploymentPopupNotification.forOffer(
				offer.getApplication().getJobPosting().getWorkplace().getEmployerUser(),
				EmploymentPopupNotificationType.OFFER_REJECTED, offer, now()));
	}

	@Transactional(readOnly = true)
	public EmploymentPopupNotificationListResponse getUnconfirmed(Long userId) {
		getActiveUser(userId);
		return new EmploymentPopupNotificationListResponse(notifications
				.findAllByRecipientIdAndConfirmedAtIsNullOrderByCreatedAtAscIdAsc(userId)
				.stream().map(this::toItem).toList());
	}

	@Transactional
	public EmploymentPopupNotificationConfirmResponse confirm(Long userId, Long notificationId) {
		UserAccount user = getActiveUser(userId);
		EmploymentPopupNotification notification = notifications.findByIdForUpdate(notificationId)
				.orElseThrow(EmploymentPopupNotificationNotFoundException::new);
		if (!notification.getRecipient().getId().equals(user.getId())) {
			throw new AccessDeniedException("본인의 팝업 알림만 확인할 수 있습니다.");
		}
		notification.confirm(now());
		return new EmploymentPopupNotificationConfirmResponse(notification.getId(), notification.getConfirmedAt());
	}

	private UserAccount getActiveUser(Long userId) {
		UserAccount user = users.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		return user;
	}

	private EmploymentPopupNotificationItemResponse toItem(EmploymentPopupNotification notification) {
		return new EmploymentPopupNotificationItemResponse(notification.getId(), notification.getType(),
				notification.getType().getMessage(), notification.getApplicationId(),
				notification.getEmploymentOfferId(), notification.getCreatedAt());
	}

	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
