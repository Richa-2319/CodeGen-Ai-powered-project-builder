package com.project.distributed_codegen.intelligence_service.repository;


import com.project.distributed_codegen.intelligence_service.entity.ChatEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ChatEventRepository extends JpaRepository<ChatEvent, Long> {
    Optional<ChatEvent> findBySagaId(String s);

    @Modifying
    @Transactional
    @Query("update ChatEvent e set e.status = com.project.distributed_codegen.common_lib.enums.ChatEventStatus.FAILED "
            + "where e.sagaId = :sagaId and e.status = com.project.distributed_codegen.common_lib.enums.ChatEventStatus.PENDING")
    int markPendingSagaFailed(@Param("sagaId") String sagaId);
}
