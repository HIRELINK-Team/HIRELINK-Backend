package com.hirelink.domain.auth.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class LoginSessionFixtures {
    private LoginSessionFixtures() { }

    public static String create(JdbcTemplate jdbc, long userId) {
        UUID sessionId = UUID.randomUUID();
        String hash = sessionId.toString().replace("-", "").repeat(2);
        jdbc.update("""
                insert into refresh_token(user_id, session_id, token_hash, expires_at, created_at)
                select id, ?, ?, now() + interval '14 days', now() from user_account where id = ?
                """, sessionId, hash, userId);
        return sessionId.toString();
    }
}
