package com.example.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.usermanagement.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserServiceIntegrationTests {

    @Autowired
    private UserService userService;

    @Test
    void searchIsCaseInsensitiveAndIncludesNotes() {
        assertThat(userService.findUsers("alex")).extracting(User::getUsername)
                .containsExactly("Alex Chen");
        assertThat(userService.findUsers("DESIGNER")).extracting(User::getUsername)
                .containsExactly("Jamie Lin");
    }

    @Test
    void updatingUserReplacesRoleAssignmentsTransactionally() {
        User user = userService.getUserById(1);
        user.setRoleIds(List.of(3));

        userService.saveUser(user);

        User updated = userService.getUserById(1);
        assertThat(updated.getRoles()).extracting("name").containsExactly("Viewer");
    }
}
