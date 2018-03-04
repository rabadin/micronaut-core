/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.retry;

import org.particleframework.context.event.ApplicationEventPublisher;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.retry.event.CircuitClosedEvent;
import org.particleframework.retry.event.CircuitOpenEvent;
import org.particleframework.retry.exception.CircuitOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A context object for storing the state of the Circuit
 *
 * @author graemerocher
 * @since 1.0
 */
class CircuitBreakerRetry implements MutableRetryState {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetryInterceptor.class);
    private final RetryStateBuilder retryStateBuilder;
    private final long openTimeout;
    private final ExecutableMethod<?,?> method;
    private final ApplicationEventPublisher eventPublisher;
    private AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private volatile Throwable lastError;
    private volatile long time = System.currentTimeMillis();
    private volatile MutableRetryState childState;

    CircuitBreakerRetry(
            long openTimeout,
            RetryStateBuilder childStateBuilder,
            ExecutableMethod<?,?> method,
            ApplicationEventPublisher eventPublisher) {
        this.retryStateBuilder = childStateBuilder;
        this.openTimeout = openTimeout;
        this.childState = (MutableRetryState) childStateBuilder.build();
        this.eventPublisher = eventPublisher;
        this.method = method;
    }

    @Override
    public void close(@Nullable Throwable exception) {
        if(exception == null && currentState() == CircuitState.HALF_OPEN) {
            closeCircuit();
        }
        else if(exception != null) {
            if(currentState() != CircuitState.OPEN) {
                openCircuit(exception);
            }
        }
        else {
            // reset state for successful operation
            time = System.currentTimeMillis();
            lastError = null;
            this.childState = (MutableRetryState) retryStateBuilder.build();
        }
    }

    @Override
    public void open() {
        if(currentState() == CircuitState.OPEN && lastError != null) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Rethrowing existing exception for Open Circuit [{}]: {}", method, lastError.getMessage());
            }
            if(lastError instanceof RuntimeException) {
                throw (RuntimeException) lastError;
            }
            else {
                throw new CircuitOpenException("Circuit Open: " + lastError.getMessage(), lastError);
            }
        }
    }

    @Override
    public long nextDelay() {
        return childState.nextDelay();
    }

    @Override
    public boolean canRetry(Throwable exception) {
        if(exception == null) {
            throw new IllegalArgumentException("Exception cause cannot be null");
        }
        try {
            return currentState() != CircuitState.OPEN && childState.canRetry(exception);
        } finally {
            if(currentState() == CircuitState.HALF_OPEN) {
                openCircuit(exception);
            }
        }
    }

    @Override
    public int getMaxAttempts() {
        return childState.getMaxAttempts();
    }

    @Override
    public int currentAttempt() {
        return childState.currentAttempt();
    }

    @Override
    public OptionalDouble getMultiplier() {
        return childState.getMultiplier();
    }

    @Override
    public Duration getDelay() {
        return childState.getDelay();
    }

    @Override
    public Duration getOverallDelay() {
        return childState.getOverallDelay();
    }

    @Override
    public Optional<Duration> getMaxDelay() {
        return childState.getMaxDelay();
    }

    /**
     * @return The current state
     */
    CircuitState currentState() {
        if(state.get() == CircuitState.OPEN) {
            long now = System.currentTimeMillis();
            long timeout = time + openTimeout;
            if(now > timeout) {
                return halfOpenCircuit();
            }
            return state.get();
        }
        else {
            return state.get();
        }
    }

    /**
     * Opens the circuit
     *
     * @return The current state
     */
    private CircuitState openCircuit(Throwable cause) {
        if(cause == null) {
            throw new IllegalArgumentException("Exception cause cannot be null");
        }
        if(LOG.isDebugEnabled()) {
            LOG.debug("Opening circuit [{}] due to error: {}", method, cause.getMessage());
        }
        this.childState = (MutableRetryState) retryStateBuilder.build();
        this.lastError = cause;
        this.time = System.currentTimeMillis();
        try {
            return state.getAndSet(CircuitState.OPEN);
        } finally {
            if(eventPublisher != null) {
                try {
                    eventPublisher.publishEvent(new CircuitOpenEvent(method, childState, cause));
                } catch (Exception e) {
                    if(LOG.isErrorEnabled()) {
                        LOG.error("Error publishing CircuitOpen event: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Resets the circuit state to {@link CircuitState#CLOSED}
     *
     * @return The current state
     */
    private CircuitState closeCircuit() {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Closing Circuit [{}]", method);
        }

        time = System.currentTimeMillis();
        lastError = null;
        this.childState = (MutableRetryState) retryStateBuilder.build();
        try {
            return state.getAndSet(CircuitState.CLOSED);
        } finally {
            if(eventPublisher != null) {
                try {
                    eventPublisher.publishEvent(new CircuitClosedEvent(method));
                } catch (Exception e) {
                    if(LOG.isErrorEnabled()) {
                        LOG.error("Error publishing CircuitClosedEvent: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Resets the circuit state to {@link CircuitState#CLOSED}
     *
     * @return The current state
     */
    private CircuitState halfOpenCircuit() {
        lastError = null;
        this.childState = (MutableRetryState) retryStateBuilder.build();
        return state.getAndSet(CircuitState.HALF_OPEN);
    }
}
