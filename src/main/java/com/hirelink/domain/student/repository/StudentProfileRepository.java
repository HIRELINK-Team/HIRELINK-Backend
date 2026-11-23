package com.hirelink.domain.student.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hirelink.domain.student.entity.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    @Query("""
            select p.id from StudentProfile p
            where p.id > :afterId and p.hirelinkBadgeIssuedAt is not null
              and p.userAccount.withdrawalFinalizedAt is null
            order by p.id
            """)
    List<Long> findWorkingConditionsRefreshCandidates(@Param("afterId") Long afterId, Pageable page);
}
