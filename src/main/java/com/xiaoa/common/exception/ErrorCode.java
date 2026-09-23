package com.xiaoa.common.exception;

public enum ErrorCode {

    SUCCESS(0, "success"),
    SYSTEM_ERROR(1000, "系统繁忙，请稍后重试"),
    INVALID_PARAMETER(1001, "请求参数错误"),
    UNAUTHORIZED(2001, "未登录或登录已过期"),
    FORBIDDEN(2003, "没有操作权限"),
    NEED_JOIN(2004, "账号未入店，请使用邀请码入店"),
    USER_REMOVED(2005, "你已被移出门店，请联系店长"),
    USER_DISABLED(2006, "账号已被禁用"),
    TENANT_EXPIRED(2007, "租户已到期，请续费"),
    INVITE_INVALID(2008, "邀请码无效"),
    INVITE_USED(2009, "邀请码已被使用"),
    INVITE_EXPIRED(2010, "邀请码已过期，请联系店长重新生成"),
    ALREADY_JOINED(2011, "当前微信已加入门店"),
    PHONE_ACCOUNT_EXISTS(2012, "手机号已绑定其他微信，请使用接管流程"),
    NOT_FOUND(1002, "数据不存在"),
    DUPLICATE(1003, "数据已存在"),
    QUOTA_NOT_ENOUGH(3001, "额度不足"),
    COMPLIANCE_REJECTED(4001, "内容未通过合规检查");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
