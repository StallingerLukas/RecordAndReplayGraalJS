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
package com.oracle.truffle.js.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.JSRuntime;

/**
 * Implementation of ToLength (ES6 7.1.15).
 *
 */
public abstract class JSToLengthNode extends JavaScriptBaseNode {

    protected final BranchProfile needNegativeBranch = BranchProfile.create();

    public static JSToLengthNode create() {
        return JSToLengthNodeGen.create();
    }

    public abstract long executeLong(Object value);

    @Specialization
    protected long doInt(int value) {
        if (value < 0) {
            needNegativeBranch.enter();
            return 0;
        }
        return value;
    }

    @Specialization
    protected long doDouble(double value,
                    @Cached("create()") BranchProfile needPositiveInfinityBranch,
                    @Cached("create()") BranchProfile needNaNBranch,
                    @Cached("create()") BranchProfile needSafeBranch) {
        if (Double.isNaN(value)) {
            needNaNBranch.enter();
            return 0;
        }
        if (JSRuntime.isPositiveInfinity(value)) {
            needPositiveInfinityBranch.enter();
            return JSRuntime.MAX_SAFE_INTEGER_LONG;
        }
        return doLong((long) value, needSafeBranch);
    }

    @Specialization(guards = "isUndefined(value)")
    protected long doUndefined(@SuppressWarnings("unused") DynamicObject value) {
        return 0;
    }

    @Specialization
    protected long doObject(Object value,
                    @Cached("create()") JSToNumberNode toNumberNode,
                    @Cached("create()") BranchProfile needSafeBranch) {
        Number result = (Number) toNumberNode.execute(value);
        return doLong(JSRuntime.toInteger(result), needSafeBranch);
    }

    private long doLong(final long value, final BranchProfile needSafeBranch) {
        if (value < 0) {
            needNegativeBranch.enter();
            return 0;
        }
        if (value > JSRuntime.MAX_SAFE_INTEGER_LONG) {
            needSafeBranch.enter();
            return JSRuntime.MAX_SAFE_INTEGER_LONG;
        }
        return value;
    }
}
