package com.resume.agent.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 系统级错误
    SUCCESS(200, "OK"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务级错误 (1xxxx)
    BUSINESS_ERROR(10000, "业务处理异常"),
    DATA_NOT_FOUND(10001, "数据不存在"),
    DATA_DUPLICATE(10002, "数据重复"),
    OPERATION_FAILED(10003, "操作失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
