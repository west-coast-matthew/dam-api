package com.twofourseven.dam.exceptions;

/** Thrown when a request conflicts with the current state of a resource. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
