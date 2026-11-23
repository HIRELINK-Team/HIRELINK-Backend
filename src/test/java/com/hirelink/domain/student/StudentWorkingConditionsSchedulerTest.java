package com.hirelink.domain.student;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import com.hirelink.domain.student.repository.StudentProfileRepository;
import com.hirelink.domain.student.service.StudentWorkingConditionsScheduler;
import com.hirelink.domain.student.service.StudentWorkingConditionsService;
import static org.mockito.Mockito.*;

class StudentWorkingConditionsSchedulerTest {
    @Test void failedProfileDoesNotBlockLaterPagesAndIsRetriedOnNextRun() {
        var profiles = mock(StudentProfileRepository.class);
        var conditions = mock(StudentWorkingConditionsService.class);
        var scheduler = new StudentWorkingConditionsScheduler(profiles, conditions);
        var page = PageRequest.of(0, 100);
        when(profiles.findWorkingConditionsRefreshCandidates(0L, page)).thenReturn(List.of(1L, 4L));
        when(profiles.findWorkingConditionsRefreshCandidates(4L, page)).thenReturn(List.of(9L));
        when(profiles.findWorkingConditionsRefreshCandidates(9L, page)).thenReturn(List.of());
        doThrow(new IllegalStateException("temporary failure")).doNothing().when(conditions).refreshExisting(1L);

        scheduler.refreshAll();
        scheduler.refreshAll();

        var ordered = inOrder(conditions);
        for (int run = 0; run < 2; run++) {
            ordered.verify(conditions).refreshExisting(1L);
            ordered.verify(conditions).refreshExisting(4L);
            ordered.verify(conditions).refreshExisting(9L);
        }
        verifyNoMoreInteractions(conditions);
    }
}
