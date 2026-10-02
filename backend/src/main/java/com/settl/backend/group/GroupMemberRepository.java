package com.settl.backend.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    @Query("SELECT gm FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.id.userId = :userId")
    Optional<GroupMember> findByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.id.userId = :userId")
    boolean existsByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.user WHERE gm.id.groupId = :groupId ORDER BY gm.joinedAt ASC")
    List<GroupMember> findByGroupIdWithUser(@Param("groupId") UUID groupId);

    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.admin = true")
    long countAdminsInGroup(@Param("groupId") UUID groupId);

    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.id.groupId = :groupId")
    long countMembersInGroup(@Param("groupId") UUID groupId);

    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.group WHERE gm.id.userId = :userId")
    List<GroupMember> findAllByUserIdWithGroup(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.id.userId = :userId")
    void deleteByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GroupMember gm WHERE gm.id.groupId = :groupId")
    void deleteAllByGroupId(@Param("groupId") UUID groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GroupMember gm WHERE gm.id.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
