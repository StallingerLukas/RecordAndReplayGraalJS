/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.intl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.cast.JSToNumberNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSException;
import com.oracle.truffle.js.runtime.JSRuntime;

public abstract class DefaultNumberOptionNode extends JavaScriptBaseNode {

    @Child JSToNumberNode toNumberNode;
    int maximum;
    Number fallback;

    protected DefaultNumberOptionNode(int maximum, Number fallback) {
        this.maximum = maximum;
        this.fallback = fallback;
    }

    public abstract Number executeValue(Object value, int minimum);

    public static DefaultNumberOptionNode create(int maximum, Number fallback) {
        return DefaultNumberOptionNodeGen.create(maximum, fallback);
    }

    @Specialization(guards = "!isUndefined(value)")
    public Number getOption(Object value, int minimum) {
        Number numValue = toNumber(value);
        int intValue = numValue.intValue();
        if (minimum > intValue || maximum < intValue) {
            createRangeError(value, minimum);
        }
        return intValue;
    }

    @TruffleBoundary
    private void createRangeError(Object value, int minimum) throws JSException {
        throw Errors.createRangeError(String.format("invalid value %s found where only values between %d and %d are allowed", JSRuntime.safeToString(value), minimum, maximum));
    }

    @Specialization(guards = "isUndefined(value)")
    @SuppressWarnings("unused")
    public Number getOptionFromUndefined(Object value, int minimum) {
        return fallback;
    }

    protected Number toNumber(Object value) {
        if (toNumberNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toNumberNode = insert(JSToNumberNode.create());
        }
        return toNumberNode.executeNumber(value);
    }
}
