package com.myplans.auth.exception;

public class RutAlreadyExistsException extends RuntimeException {
    public RutAlreadyExistsException(String message) {
        super(message);
    }
}
