package com.settl.backend.group;

import com.settl.backend.group.dto.GroupResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.password=${TEST_DB_PASSWORD:2603}"
})
class GroupQueryCountIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private JavaMailSender javaMailSender;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("alice@example.com", "hash", "Alice"));
        bob = userRepository.save(new User("bob@example.com", "hash", "Bob"));
        charlie = userRepository.save(new User("charlie@example.com", "hash", "Charlie"));
    }

    @Test
    @DisplayName("Query count should remain constant (2 queries total) and not scale with the number of groups (No 1+N)")
    void testUserInMultipleGroupsQueryCountDoesNotScaleWithGroups() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        // Create 2 groups with Alice
        createGroupWithMembers("Group 1", alice, List.of(alice, bob));
        createGroupWithMembers("Group 2", alice, List.of(alice, charlie));

        // Measure query count for 2 groups
        statistics.clear();
        List<GroupResponse> twoGroups = groupService.getUserGroups(alice.getId());
        long queriesFor2Groups = statistics.getPrepareStatementCount();

        assertThat(twoGroups).hasSize(2);
        assertThat(twoGroups.get(0).members()).isNotEmpty();
        assertThat(twoGroups.get(1).members()).isNotEmpty();

        // Create 3 additional groups (5 groups total)
        createGroupWithMembers("Group 3", alice, List.of(alice, bob, charlie));
        createGroupWithMembers("Group 4", bob, List.of(bob, alice));
        createGroupWithMembers("Group 5", charlie, List.of(charlie, alice));

        // Measure query count for 5 groups
        statistics.clear();
        List<GroupResponse> fiveGroups = groupService.getUserGroups(alice.getId());
        long queriesFor5Groups = statistics.getPrepareStatementCount();

        assertThat(fiveGroups).hasSize(5);
        for (GroupResponse g : fiveGroups) {
            assertThat(g.members()).isNotEmpty();
            assertThat(g.memberCount()).isEqualTo(g.members().size());
            assertThat(g.members().get(0).displayName()).isNotBlank();
        }

        // Assert query count is constant and does NOT scale with group count:
        // Expected: 1 query for user's groups, 1 query for all members in those groups with JOIN FETCH user
        assertThat(queriesFor2Groups)
                .as("2 groups should execute at most 2 queries (1 groups query + 1 batched members query)")
                .isLessThanOrEqualTo(2);

        assertThat(queriesFor5Groups)
                .as("5 groups query count should equal 2 groups query count (constant, does not scale with groups count)")
                .isEqualTo(queriesFor2Groups);
    }

    @Test
    @DisplayName("User with zero groups should execute exactly 1 query and guard against querying members")
    void testUserWithZeroGroupsExecutesSingleQueryWithoutQueryingMembers() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        User standaloneUser = userRepository.save(new User("standalone@example.com", "hash", "Solo"));

        statistics.clear();
        List<GroupResponse> result = groupService.getUserGroups(standaloneUser.getId());
        long queries = statistics.getPrepareStatementCount();

        assertThat(result).isEmpty();
        assertThat(queries).as("Only 1 query to find groups; empty list returned immediately without querying members")
                .isEqualTo(1);
    }

    private Group createGroupWithMembers(String name, User creator, List<User> members) {
        Group group = groupRepository.save(new Group(name, "USD", creator));
        for (int i = 0; i < members.size(); i++) {
            User m = members.get(i);
            boolean isAdmin = m.getId().equals(creator.getId());
            groupMemberRepository.save(new GroupMember(group, m, isAdmin));
        }
        return group;
    }
}
