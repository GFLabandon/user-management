package io.github.gflabandon.counselor.service;

public class EditConflictException extends BusinessException {
    public EditConflictException() { super("资料已被其他操作更新，请重新打开页面核对后再提交。"); }
}
