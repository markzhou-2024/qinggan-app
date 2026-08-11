package com.qinggan.travel.family.persistence;

import com.qinggan.travel.family.domain.FamilyRole;
import com.qinggan.travel.family.domain.FamilyRoleBinding;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyRoleBindingJpaRepository extends JpaRepository<FamilyRoleBinding, Long> {

    List<FamilyRoleBinding> findByTripIdOrderByRoleAsc(Long tripId);

    Optional<FamilyRoleBinding> findByTripIdAndActiveDeviceId(Long tripId, String activeDeviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from FamilyRoleBinding b where b.tripId = :tripId and b.role = :role")
    Optional<FamilyRoleBinding> findForUpdate(@Param("tripId") Long tripId, @Param("role") FamilyRole role);
}
