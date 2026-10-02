package io.github.gflabandon.counselor.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.servlet.ModelAndView;

/** Shared, allowlisted presentation for MVC and servlet error dispatches. */
public final class ErrorPages {
    private ErrorPages() {}
    public static ModelAndView page(HttpServletRequest request, int status, boolean csrf) {
        String title, message, next;
        switch (status) {
            case 400 -> { title = "请求内容有误"; message = "参数缺失或格式不正确，操作未完成。"; next = "请从列表重新打开页面，检查输入后再提交。"; }
            case 403 -> {
                title = csrf ? "表单验证已失效" : "没有操作权限";
                message = csrf ? "当前表单的安全校验未通过，本次请求未执行。" : "当前账号没有访问此页面或执行此操作的权限。";
                next = csrf ? "请先复制需要保留的文字，再重新打开表单；登录已过期时请重新登录。" : "请返回档案列表；如需管理权限，请联系管理员。";
            }
            case 404 -> { title = "页面或记录不存在"; message = "该页面或记录可能已移除，或访问地址有误。"; next = "请从列表重新查找记录。"; }
            case 405 -> { title = "请求方式不支持"; message = "此地址不接受当前请求方式。"; next = "请从页面提供的按钮或链接重新操作。"; }
            case 409 -> { title = "资料已更新"; message = "资料已被其他操作更新，本次修改未保存。"; next = "请复制需要保留的文字，再重新打开最新记录核对。"; }
            case 413 -> { title = "上传内容过大"; message = "上传超出限制：单张头像最大 5 MB，整个请求最大 20 MB。"; next = "请返回表单并选择较小的 JPG/PNG 图片；本次请求未进入保存流程，未解析的输入无法在此页恢复。"; }
            case 415 -> { title = "请求格式不支持"; message = "服务器无法处理当前请求格式。"; next = "请从应用页面重新提交。"; }
            case 503 -> { title = "服务暂时不可用"; message = "当前无法完成请求。"; next = "请稍后返回列表确认操作结果，再决定是否重试；持续失败时请向管理员提供请求编号。"; }
            default -> { if (status < 400 || status > 599) status = 500; title = "请求未能完成"; message = "当前请求无法完成。"; next = "请返回列表确认操作结果，避免重复提交；需要帮助时请向管理员提供请求编号。"; }
        }
        var view = new ModelAndView("error/request");
        view.setStatus(HttpStatusCode.valueOf(status));
        view.addObject("errorStatus", status).addObject("errorTitle", title)
                .addObject("errorMessage", message).addObject("errorNextStep", next)
                .addObject("requestId", requestId(request));
        return view;
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value instanceof String id && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") ? id : null;
    }
}
