package com.github.jarome.common.exception;

public class NatsException extends RuntimeException {
    private Integer code;
    private String msg;

    public NatsException(Throwable cause, Integer code, String msg) {
        super(cause);
        this.code = code;
        this.msg = msg;
    }

    public NatsException(String msg) {
        this.code = -1;
        this.msg = msg;
    }

    public NatsException(Throwable cause) {
        super(cause);
    }

    public NatsException(String msg, Throwable cause) {
        super(msg, cause);
        this.msg = msg;
    }

    public NatsException(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
