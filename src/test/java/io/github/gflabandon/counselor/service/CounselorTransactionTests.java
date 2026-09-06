package io.github.gflabandon.counselor.service;

import static org.assertj.core.api.Assertions.*;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.web.CounselorForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CounselorTransactionTests {
    @Autowired CounselorService service;
    @Autowired DepartmentService departments;

    @Test void historyInsertFailureRollsBackNewProfile() {
        CounselorForm form = new CounselorForm(); form.setEmployeeNo("ROLLBACK-1"); form.setName("事务测试");
        form.setDepartmentId(departments.all().get(0).getId());
        assertThatThrownBy(() -> service.create(form, null, null)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.search("ROLLBACK-1", null, null, 1, 10).total()).isZero();
    }

    @Test void historyFailureRollsBackStatusAndVersionChange() {
        Counselor before = service.search("DEMO-001", null, null, 1, 10).items().get(0);
        int count = service.history(before.getId()).size();
        assertThatThrownBy(() -> service.deactivate(before.getId(), before.getVersion(), null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        Counselor after = service.get(before.getId());
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getEmploymentStatus()).isEqualTo(before.getEmploymentStatus());
        assertThat(service.history(before.getId())).hasSize(count);
    }
}
