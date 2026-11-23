package com.hirelink.domain.member.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentWithdrawalRetentionRepository {
    private final JdbcTemplate jdbc;

    public StudentWithdrawalRetentionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void clearAuthenticationDrafts(Long userId) {
        jdbc.update("delete from phone_verification where user_id = ?", userId);
        jdbc.update("""
                update student_registration_verification set renewal_id=null, renewal_expires_at=null,
                    renewal_payload=null, renewal_card_expires_on=null, renewal_card_verified_at=null,
                    renewal_document_expires_on=null, renewal_document_verified_at=null
                where user_id = ?
                """, userId);
    }

    public void deleteRetainedRecords(Long userId) {
        // 논리 삭제로 일반 조회에서 제외된 서류도 보관기한이 끝나면 실제 삭제한다.
        jdbc.update("delete from student_enrollment_document where user_id = ?", userId);
        jdbc.update("delete from student_registration_verification where user_id = ?", userId);
        jdbc.update("delete from student_career where user_id = ?", userId);
        jdbc.update("delete from student_preferred_job where user_id = ?", userId);
        jdbc.update("delete from student_preferred_work_time where user_id = ?", userId);
        jdbc.update("delete from student_preferred_location where user_id = ?", userId);
        jdbc.update("delete from student_priority where user_id = ?", userId);
        jdbc.update("delete from student_job_preference where user_id = ?", userId);
        jdbc.update("delete from user_term_agreement where user_id = ?", userId);
        jdbc.update("delete from phone_verification where user_id = ?", userId);
        jdbc.update("delete from phone_verification_send_attempt where user_id = ?", userId);
    }
}
