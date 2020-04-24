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
package com.oracle.truffle.js.runtime.builtins;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Null;

public final class JSUserObject extends JSBuiltinObject implements PrototypeSupplier {

    public static final String TYPE_NAME = "object";
    public static final String CLASS_NAME = "Object";
    public static final String PROTOTYPE_NAME = "Object.prototype";

    public static final JSUserObject INSTANCE = new JSUserObject();

    private JSUserObject() {
    }

    public static DynamicObject create(JSContext context) {
        return create(context, context.getRealm());
    }

    public static DynamicObject create(JSContext context, JSRealm realm) {
        if (context.isMultiContext()) {
            return createWithPrototypeInObject(realm.getObjectPrototype(), context);
        }
        return JSObject.createWithRealm(context, context.getOrdinaryObjectFactory(), realm, JSArguments.EMPTY_ARGUMENTS_ARRAY);
    }

    @TruffleBoundary
    public static DynamicObject createWithPrototype(DynamicObject prototype, JSContext context) {
        assert prototype == Null.instance || JSRuntime.isObject(prototype);
        return JSObject.create(context, prototype, INSTANCE);
    }

    public static DynamicObject createWithNullPrototype(JSContext context) {
        return JSObject.create(context, context.getEmptyShapeNullPrototype());
    }

    public static DynamicObject createWithPrototypeInObject(DynamicObject prototype, JSContext context) {
        assert prototype == Null.instance || JSRuntime.isObject(prototype);
        Shape shape = context.getEmptyShapePrototypeInObject();
        DynamicObject obj = JSObject.create(context, shape);
        JSObject.PROTO_PROPERTY.setSafe(obj, prototype, shape);
        return obj;
    }

    public static DynamicObject createInit(JSRealm realm) {
        CompilerAsserts.neverPartOfCompilation();
        return JSObject.createInit(realm, realm.getObjectPrototype(), INSTANCE);
    }

    public static DynamicObject createInit(JSRealm realm, DynamicObject prototype) {
        CompilerAsserts.neverPartOfCompilation();
        return JSObject.createInit(realm, prototype, INSTANCE);
    }

    public static boolean isJSUserObject(Object obj) {
        return JSObject.isDynamicObject(obj) && isJSUserObject((DynamicObject) obj);
    }

    public static boolean isJSUserObject(DynamicObject obj) {
        return isInstance(obj, INSTANCE);
    }

    @Override
    @TruffleBoundary
    public String getClassName(DynamicObject object) {
        JSContext context = JSObject.getJSContext(object);
        if (context.getEcmaScriptVersion() <= 5) {
            Object toStringTag = get(object, Symbol.SYMBOL_TO_STRING_TAG);
            if (JSRuntime.isString(toStringTag)) {
                return JSRuntime.toStringIsString(toStringTag);
            }
        }
        return CLASS_NAME;
    }

    @TruffleBoundary
    @Override
    public String safeToString(DynamicObject obj, int depth) {
        if (JSTruffleOptions.NashornCompatibilityMode) {
            return defaultToString(obj);
        } else {
            return JSRuntime.objectToConsoleString(obj, null, depth);
        }
    }

    @TruffleBoundary
    @Override
    public Object get(DynamicObject thisObj, long index) {
        // convert index only once
        return get(thisObj, String.valueOf(index));
    }

    @Override
    public boolean hasOnlyShapeProperties(DynamicObject obj) {
        return true;
    }

    @Override
    public Shape makeInitialShape(JSContext context, DynamicObject prototype) {
        return JSObjectUtil.getProtoChildShape(prototype, INSTANCE, context);
    }

    @Override
    public DynamicObject getIntrinsicDefaultProto(JSRealm realm) {
        return realm.getObjectPrototype();
    }
}
