package com.settl.backend.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query("SELECT gm.group FROM GroupMember gm WHERE gm.user.id = :userId ORDER BY gm.group.createdAt DESC")
    List<Group> findGroupsByUserId(@Param("userId") UUID userId);
}
