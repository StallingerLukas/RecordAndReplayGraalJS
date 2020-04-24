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
package com.oracle.truffle.js.nodes.cast;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.nodes.JSGuards;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.cast.JSToObjectNodeGen.JSToObjectWrapperNodeGen;
import com.oracle.truffle.js.nodes.unary.JSUnaryNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSException;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.builtins.JSBigInt;
import com.oracle.truffle.js.runtime.builtins.JSBoolean;
import com.oracle.truffle.js.runtime.builtins.JSClass;
import com.oracle.truffle.js.runtime.builtins.JSNumber;
import com.oracle.truffle.js.runtime.builtins.JSString;
import com.oracle.truffle.js.runtime.builtins.JSSymbol;
import com.oracle.truffle.js.runtime.objects.JSLazyString;
import com.oracle.truffle.js.runtime.objects.Null;

/**
 * Implementation of ECMA 9.9 "ToObject" as Truffle node.
 *
 * thing a generic value to be converted to a DynamicObject or TruffleObject
 */
public abstract class JSToObjectNode extends JavaScriptBaseNode {

    protected static final int MAX_SHAPE_COUNT = 1;
    protected static final int MAX_CLASSES = 3;

    private final JSContext context;
    private final boolean checkForNullOrUndefined;
    private final boolean fromWith;
    private final boolean allowForeign;

    protected JSToObjectNode(JSContext context, boolean checkForNullOrUndefined, boolean fromWith, boolean allowForeign) {
        this.context = context;
        this.checkForNullOrUndefined = checkForNullOrUndefined;
        this.fromWith = fromWith;
        this.allowForeign = allowForeign;
    }

    public abstract TruffleObject executeTruffleObject(Object value);

    public static JSToObjectNode createToObject(JSContext context) {
        return createToObject(context, true, false, true);
    }

    public static JSToObjectNode createToObjectNoCheck(JSContext context) {
        return createToObject(context, false, false, true);
    }

    public static JSToObjectNode createToObjectNoCheckNoForeign(JSContext context) {
        return createToObject(context, false, false, false);
    }

    private static JSToObjectNode createToObject(JSContext context, boolean checkForNullOrUndefined, boolean fromWith, boolean allowForeign) {
        return JSToObjectNodeGen.create(context, checkForNullOrUndefined, fromWith, allowForeign);
    }

    protected final JSContext getContext() {
        return context;
    }

    protected final boolean isCheckForNullOrUndefined() {
        return checkForNullOrUndefined;
    }

    protected final boolean isFromWith() {
        return fromWith;
    }

    protected final boolean isAllowForeign() {
        return allowForeign;
    }

    @TruffleBoundary
    private JSException createTypeError(DynamicObject value) {
        if (isFromWith()) {
            return Errors.createTypeError("Cannot apply \"with\" to " + JSRuntime.safeToString(value), this);
        }
        return Errors.createTypeErrorNotObjectCoercible(value, this);
    }

    @Specialization
    protected DynamicObject doBoolean(boolean value) {
        return JSBoolean.create(context, value);
    }

    @Specialization
    protected DynamicObject doJSLazyString(JSLazyString value) {
        return JSString.create(getContext(), value);
    }

    @Specialization
    protected DynamicObject doString(String value) {
        return JSString.create(getContext(), value);
    }

    @Specialization
    protected DynamicObject doInt(int value) {
        return JSNumber.create(getContext(), value);
    }

    @Specialization
    protected DynamicObject doDouble(double value) {
        return JSNumber.create(getContext(), value);
    }

    @Specialization
    protected DynamicObject doBigInt(BigInt value) {
        return JSBigInt.create(getContext(), value);
    }

    @Specialization(guards = "isJavaNumber(value)")
    protected DynamicObject doNumber(Object value) {
        return JSNumber.create(getContext(), (Number) value);
    }

    @Specialization
    protected DynamicObject doSymbol(Symbol value) {
        return JSSymbol.create(getContext(), value);
    }

    @Specialization(guards = {"shape.check(object)", "isJSObjectShape(shape)", "!isCheckForNullOrUndefined()"}, limit = "MAX_SHAPE_COUNT")
    protected DynamicObject doJSObjectNoCheckShape(DynamicObject object,
                    @Cached("object.getShape()") @SuppressWarnings("unused") Shape shape) {
        return object;
    }

    @Specialization(guards = {"isJSType(object)", "!isCheckForNullOrUndefined()"}, replaces = "doJSObjectNoCheckShape")
    protected DynamicObject doJSObjectNoCheck(DynamicObject object) {
        return object;
    }

    @Specialization(guards = {"shape.check(object)", "isJSObjectShape(shape)", "isNotNullOrUndefined(shape)", "isCheckForNullOrUndefined()"}, limit = "MAX_SHAPE_COUNT")
    protected DynamicObject doJSObjectCheckShape(DynamicObject object,
                    @Cached("object.getShape()") @SuppressWarnings("unused") Shape shape) {
        return object;
    }

    protected static boolean isNotNullOrUndefined(Shape shape) {
        return shape.getObjectType() != Null.NULL_CLASS;
    }

    @Specialization(guards = {"isCheckForNullOrUndefined()", "cachedClass != null", "cachedClass.isInstance(object)"}, replaces = "doJSObjectCheckShape", limit = "1")
    protected DynamicObject doJSObjectCheckJSClass(DynamicObject object,
                    @Cached("getJSClassIfObject(object)") @SuppressWarnings("unused") JSClass cachedClass) {
        assert JSGuards.isJSObject(object);
        return object;
    }

    @Specialization(guards = {"isJSObject(object)", "isCheckForNullOrUndefined()"}, replaces = "doJSObjectCheckJSClass")
    protected DynamicObject doJSObjectCheck(DynamicObject object) {
        return object;
    }

    @Specialization(guards = {"isNullOrUndefined(object)", "isCheckForNullOrUndefined()"})
    protected DynamicObject doNullOrUndefined(DynamicObject object) {
        throw createTypeError(object);
    }

    @Specialization(guards = {"isForeignObject(obj)"})
    protected TruffleObject doForeignTruffleObject(TruffleObject obj) {
        if (isAllowForeign()) {
            if (isFromWith() && context.isOptionNashornCompatibilityMode() && context.getRealm().getEnv().isHostObject(obj)) {
                throwWithError();
            }
            return obj;
        } else {
            throw Errors.createTypeError("Foreign TruffleObject not supported", this);
        }
    }

    @Specialization(guards = {"!isBoolean(object)", "!isNumber(object)", "!isString(object)", "!isSymbol(object)", "!isJSObject(object)", "!isForeignObject(object)"})
    protected TruffleObject doJavaGeneric(Object object) {
        // assume these to be Java objects
        assert !JSRuntime.isJSNative(object);
        if (isFromWith()) {
            // ... but make that an error within "with"
            throwWithError();
        }
        return (TruffleObject) context.getRealm().getEnv().asBoxedGuestValue(object);
    }

    @TruffleBoundary
    private void throwWithError() {
        String message = "Cannot apply \"with\" to non script object";
        if (JSTruffleOptions.NashornCompatibilityMode) {
            message += ". Consider using \"with(Object.bindProperties({}, nonScriptObject))\".";
        }
        throw Errors.createTypeError(message, this);
    }

    public abstract static class JSToObjectWrapperNode extends JSUnaryNode {
        @Child private JSToObjectNode toObjectNode;

        protected JSToObjectWrapperNode(JavaScriptNode operand, JSToObjectNode toObjectNode) {
            super(operand);
            this.toObjectNode = toObjectNode;
        }

        public abstract DynamicObject executeDynamicObject(Object thisObj);

        /**
         * This factory method forces the creation of an JSObjectCastNode; in contrast to
         * {@code create} it does not check the child and try to omit unnecessary cast nodes.
         */
        public static JSToObjectWrapperNode createToObject(JSContext context, JavaScriptNode child) {
            return JSToObjectWrapperNodeGen.create(child, JSToObjectNode.createToObject(context));
        }

        public static JSToObjectWrapperNode createToObjectFromWith(JSContext context, JavaScriptNode child, boolean checkForNullOrUndefined) {
            return JSToObjectWrapperNodeGen.create(child, JSToObjectNodeGen.create(context, checkForNullOrUndefined, true, true));
        }

        @Override
        public boolean isResultAlwaysOfType(Class<?> clazz) {
            return clazz == TruffleObject.class;
        }

        @Specialization
        protected TruffleObject doDefault(Object value) {
            return toObjectNode.executeTruffleObject(value);
        }

        @Override
        protected JavaScriptNode copyUninitialized() {
            JSToObjectNode clonedToObject = JSToObjectNodeGen.create(toObjectNode.getContext(), toObjectNode.isCheckForNullOrUndefined(), toObjectNode.isFromWith(), toObjectNode.isAllowForeign());
            return JSToObjectWrapperNodeGen.create(cloneUninitialized(getOperand()), clonedToObject);
        }
    }
}
