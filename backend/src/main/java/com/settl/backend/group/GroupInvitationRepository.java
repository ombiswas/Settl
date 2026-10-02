package com.settl.backend.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, UUID> {

    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group JOIN FETCH gi.invitedBy WHERE gi.tokenHash = :tokenHash")
    Optional<GroupInvitation> findByTokenHashWithGroupAndInviter(@Param("tokenHash") String tokenHash);

    Optional<GroupInvitation> findByTokenHash(String tokenHash);

    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.group.id = :groupId AND gi.email = :email AND gi.status = :status")
    Optional<GroupInvitation> findByGroupIdAndEmailAndStatus(
            @Param("groupId") UUID groupId,
            @Param("email") String email,
            @Param("status") GroupInvitationStatus status
    );

    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.invitedBy WHERE gi.group.id = :groupId AND gi.status = :status ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findByGroupIdAndStatusWithInviter(
            @Param("groupId") UUID groupId,
            @Param("status") GroupInvitationStatus status
    );

    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group WHERE gi.email = :email AND gi.status = :status")
    List<GroupInvitation> findByEmailAndStatusWithGroup(
            @Param("email") String email,
            @Param("status") GroupInvitationStatus status
    );

    @Query("SELECT COUNT(gi) > 0 FROM GroupInvitation gi WHERE gi.group.id = :groupId AND gi.email = :email AND gi.status = 'PENDING'")
    boolean existsPendingByGroupIdAndEmail(@Param("groupId") UUID groupId, @Param("email") String email);
}
