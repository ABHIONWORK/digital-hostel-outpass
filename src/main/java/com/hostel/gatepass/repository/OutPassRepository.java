package com.hostel.gatepass.repository;

import com.hostel.gatepass.model.OutPass;
import com.hostel.gatepass.model.OutPassStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for OutPass entities.
 */
@Repository
public interface OutPassRepository extends JpaRepository<OutPass, Long> {

    List<OutPass> findByStudentId(String studentId);

    List<OutPass> findByStatus(OutPassStatus status);
}
