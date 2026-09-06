package io.github.gflabandon.counselor.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.mapper.AccountMapper;
import io.github.gflabandon.counselor.mapper.CounselorMapper;
import io.github.gflabandon.counselor.web.AccountForm;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class AccountService {
    private final AccountMapper mapper;
    private final CounselorMapper counselors;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public AccountService(AccountMapper mapper, CounselorMapper counselors, PasswordEncoder encoder, AuditService audit) {
        this.mapper = mapper; this.counselors = counselors; this.encoder = encoder; this.audit = audit;
    }
    public record AccountSummary(int id, String username, AccountRole role, boolean enabled, Integer counselorId) {}
    public List<AccountSummary> all() { return mapper.all().stream().map(a -> new AccountSummary(
            a.getId(), a.getUsername(), a.getRole(), a.getEnabled(), a.getCounselorId())).toList(); }
    public SystemAccount get(int id) {
        SystemAccount account = mapper.findById(id);
        if (account == null) throw new BusinessException("账号不存在。");
        return account;
    }

    @Transactional
    public int save(Integer id, @Valid AccountForm form, String actor) {
        mapper.lockAdministration();
        SystemAccount account = id == null ? new SystemAccount() : get(id);
        if (id != null && account.getVersion() != form.getVersion()) throw new EditConflictException();
        if (id != null && !account.getUsername().equals(form.getUsername().toLowerCase(Locale.ROOT))) {
            throw new BusinessException("登录名建立后不能修改。");
        }
        if (id != null && (!form.getEnabled() || form.getRole() != AccountRole.ADMIN)
                && account.getRole() == AccountRole.ADMIN && account.getEnabled()) {
            if (account.getUsername().equals(actor)) throw new BusinessException("不能停用或降级当前登录的管理员账号。");
            if (mapper.activeAdmins() <= 1) throw new BusinessException("至少保留一个启用的管理员账号。");
        }
        if (form.getCounselorId() != null && counselors.findById(form.getCounselorId()) == null) {
            throw new BusinessException("关联的辅导员档案不存在。");
        }
        String password = form.getPassword();
        if (id == null || (password != null && !password.isEmpty())) {
            validatePassword(password);
            account.setPasswordHash(encoder.encode(password));
        }
        if (id == null) account.setUsername(form.getUsername().toLowerCase(Locale.ROOT));
        account.setRole(form.getRole()); account.setEnabled(form.getEnabled()); account.setCounselorId(form.getCounselorId());
        if (id == null) mapper.insert(account);
        else if (mapper.update(account) != 1) throw new EditConflictException();
        audit.success(actor, id == null ? "ACCOUNT_CREATE" : "ACCOUNT_UPDATE", "ACCOUNT", account.getId());
        return account.getId();
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("密码须为 12–64 个字符，UTF-8 编码不超过 72 字节。");
        }
    }
}
