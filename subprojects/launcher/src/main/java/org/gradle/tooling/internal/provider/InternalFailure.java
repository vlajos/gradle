/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.tooling.internal.provider;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

public class InternalFailure implements Serializable {

    private final String message;
    private final String description;
    private final InternalFailure cause;

    public InternalFailure(String message, String description, InternalFailure cause) {
        this.message = message;
        this.description = description;
        this.cause = cause;
    }

    public String getMessage() {
        return message;
    }

    public String getDescription() {
        return description;
    }

    public InternalFailure getCause() {
        return cause;
    }

    public static InternalFailure fromThrowable(Throwable t) {
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        t.printStackTrace(wrt);
        Throwable cause = t.getCause();
        InternalFailure causeFailure = cause != null && cause != t ? fromThrowable(cause) : null;
        return new InternalFailure(t.getMessage(), out.toString(), causeFailure);
    }

}
