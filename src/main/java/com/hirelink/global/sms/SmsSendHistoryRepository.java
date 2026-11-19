package com.hirelink.global.sms;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsSendHistoryRepository extends JpaRepository<SmsSendHistory, Long> {
}
