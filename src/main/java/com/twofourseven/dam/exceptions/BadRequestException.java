package com.twofourseven.dam.exceptions;

/** Thrown when a request is invalid for business reasons. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
