package com.kenyarealestate.property.repository;

import com.kenyarealestate.property.entity.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, UUID> {
    List<FailedEvent> findByResolvedFalseOrderByReceivedAtAsc();
}
