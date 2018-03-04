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
package org.particleframework.http;

import java.util.Map;
import java.util.function.Consumer;

/**
 * An interface for an {@link HttpMessage} that is mutable allowing headers and the message body to be set.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableHttpMessage<B> extends HttpMessage<B> {
    /**
     * @return The {@link MutableHttpHeaders} object
     */
    @Override
    MutableHttpHeaders getHeaders();


    /**
     * Sets the body
     *
     * @param body The body
     * @return This message
     */
    MutableHttpMessage<B> body(B body);

    /**
     * Mutate the headers with the given consumer
     *
     * @param headers The headers
     * @return This response
     */
    default MutableHttpMessage<B> headers(Consumer<MutableHttpHeaders> headers) {
        headers.accept(getHeaders());
        return this;
    }

    /**
     * Set a response header
     *
     * @param name  The name of the header
     * @param value The value of the header
     */
    default MutableHttpMessage<B> header(CharSequence name, CharSequence value) {
        getHeaders().add(name, value);
        return this;
    }


    /**
     * Set multiple headers
     *
     * @param namesAndValues The names and values
     */
    default MutableHttpMessage<B> headers(Map<CharSequence, CharSequence> namesAndValues) {
        MutableHttpHeaders headers = getHeaders();
        namesAndValues.forEach(headers::add);
        return this;
    }

    /**
     * Sets the content length
     *
     * @param length The length
     * @return This HttpResponse
     */
    default MutableHttpMessage<B> contentLength(long length) {
        getHeaders().add(HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        return this;
    }

    /**
     * Set the response content type
     *
     * @param contentType The content type
     */
    default MutableHttpMessage<B> contentType(CharSequence contentType) {
        getHeaders().add(HttpHeaders.CONTENT_TYPE, contentType);
        return this;
    }

    /**
     * Set the response content type
     *
     * @param mediaType The media type
     */
    default MutableHttpMessage<B> contentType(MediaType mediaType) {
        getHeaders().add(HttpHeaders.CONTENT_TYPE, mediaType);
        return this;
    }
}
