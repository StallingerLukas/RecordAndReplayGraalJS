/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.interop;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.truffleinterop.InteropFunction;

/**
 * This node prepares the import of a value from Interop. It transforms values allowed in Truffle,
 * but not supported in Graal.js (e.g. {@link Long}).
 *
 * @see JSRuntime#importValue(Object)
 */
@GenerateUncached
public abstract class JSForeignToJSTypeNode extends JavaScriptBaseNode {
    public abstract Object executeWithTarget(Object target);

    public static JSForeignToJSTypeNode create() {
        return JSForeignToJSTypeNodeGen.create();
    }

    @Specialization
    public int fromInt(int value) {
        return value;
    }

    @Specialization
    public String fromString(String value) {
        return value;
    }

    @Specialization
    public boolean fromBoolean(boolean value) {
        return value;
    }

    @Specialization
    public BigInt fromBigInt(BigInt value) {
        return value;
    }

    @Specialization(guards = "isLongRepresentableAsInt32(value)")
    public int fromLongToInt(long value) {
        return (int) value;
    }

    @Specialization(guards = "!isLongRepresentableAsInt32(value)")
    public long fromLong(long value) {
        return value;
    }

    @Specialization
    public double fromDouble(double value) {
        return value;
    }

    @Specialization
    public int fromNumber(byte value) {
        return value;
    }

    @Specialization
    public int fromNumber(short value) {
        return value;
    }

    @Specialization
    public double fromNumber(float value) {
        return value;
    }

    @Specialization
    public String fromChar(char value) {
        return String.valueOf(value);
    }

    @Specialization(guards = "isJavaNull(value)")
    public Object isNull(@SuppressWarnings("unused") Object value) {
        return Null.instance;
    }

    @Specialization
    public Object fromTruffleJavaObject(TruffleObject value,
                    @CachedContext(JavaScriptLanguage.class) ContextReference<JSRealm> contextRef) {
        if (value instanceof InteropFunction) {
            return ((InteropFunction) value).getFunction();
        } else {
            TruffleLanguage.Env env = contextRef.get().getEnv();
            if (env.isHostObject(value)) {
                Object object = env.asHostObject(value);
                if (object == null) {
                    return Null.instance;
                }
            }
        }
        return value;
    }

    @Fallback
    public Object fallbackCase(Object value) {
        throw Errors.createTypeErrorUnsupportedInteropType(value);
    }
}
