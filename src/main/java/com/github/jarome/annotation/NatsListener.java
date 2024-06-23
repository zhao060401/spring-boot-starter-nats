package com.github.jarome.annotation;

/**
 * Your consumer class must implement this interface
 */
public interface NatsListener<T> {
    void onMessage(T message);
}
