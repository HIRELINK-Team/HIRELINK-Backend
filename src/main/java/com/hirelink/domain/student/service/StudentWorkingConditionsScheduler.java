package com.hirelink.domain.student.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.hirelink.domain.student.repository.StudentProfileRepository;

@Component
@ConditionalOnProperty(name = "student-working-conditions.refresh-enabled", havingValue = "true", matchIfMissing = true)
public class StudentWorkingConditionsScheduler {
    private static final Logger log = LoggerFactory.getLogger(StudentWorkingConditionsScheduler.class);
    private final StudentProfileRepository profiles;
    private final StudentWorkingConditionsService conditions;

    public StudentWorkingConditionsScheduler(StudentProfileRepository profiles, StudentWorkingConditionsService conditions) {
        this.profiles = profiles; this.conditions = conditions;
    }

    @Scheduled(fixedDelayString = "${student-working-conditions.refresh-delay:60000}",
            initialDelayString = "${student-working-conditions.refresh-initial-delay:60000}")
    public void refreshAll() {
        long afterId = 0;
        int failed = 0;
        while (true) {
            var ids = profiles.findWorkingConditionsRefreshCandidates(afterId, PageRequest.of(0, 100));
            if (ids.isEmpty()) { break; }
            for (Long id : ids) {
                try { conditions.refreshExisting(id); }
                catch (RuntimeException exception) { failed++; }
            }
            afterId = ids.get(ids.size() - 1);
        }
        if (failed > 0) { log.warn("Working condition refresh failed for {} profiles; retry scheduled", failed); }
    }
}
