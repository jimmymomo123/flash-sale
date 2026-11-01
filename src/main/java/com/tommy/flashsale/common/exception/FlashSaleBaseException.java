package com.tommy.flashsale.common.exception;

import lombok.Getter;

@Getter
public class FlashSaleBaseException extends RuntimeException {

    private final ErrorCode errorCode;

    public FlashSaleBaseException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public FlashSaleBaseException(Throwable throwable) {
        super(throwable);
        if (throwable instanceof FlashSaleBaseException flashSaleBaseException) {
            this.errorCode = flashSaleBaseException.getErrorCode();
        } else {
            this.errorCode = ErrorCode.UNKNOWN_ERROR;
        }
    }
}
