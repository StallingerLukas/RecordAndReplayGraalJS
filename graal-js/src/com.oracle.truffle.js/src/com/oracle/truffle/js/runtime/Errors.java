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
package com.oracle.truffle.js.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.builtins.JSBuiltinObject;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Null;

/**
 * Utility class to to create all kinds of ECMAScript-defined Error Objects.
 */
public final class Errors {

    private Errors() {
        // don't instantiate this
    }

    @TruffleBoundary
    public static JSException createError(String message) {
        return JSException.create(JSErrorType.Error, message);
    }

    @TruffleBoundary
    public static JSException createEvalError(String message) {
        return JSException.create(JSErrorType.EvalError, message);
    }

    @TruffleBoundary
    public static JSException createRangeError(String message) {
        return JSException.create(JSErrorType.RangeError, message);
    }

    @TruffleBoundary
    public static JSException createRangeError(String message, Node originatingNode) {
        return JSException.create(JSErrorType.RangeError, message, originatingNode);
    }

    @TruffleBoundary
    public static JSException createRangeErrorFormat(String message, Node originatingNode, Object... args) {
        return JSException.create(JSErrorType.RangeError, String.format(message, args), originatingNode);
    }

    @TruffleBoundary
    public static JSException createURIError(String message) {
        return JSException.create(JSErrorType.URIError, message);
    }

    @TruffleBoundary
    public static JSException createTypeError(String message) {
        return JSException.create(JSErrorType.TypeError, message);
    }

    @TruffleBoundary
    public static JSException createTypeErrorFormat(String message, Object... args) {
        return JSException.create(JSErrorType.TypeError, String.format(message, args));
    }

    @TruffleBoundary
    public static JSException createTypeError(String message, Node originatingNode) {
        return JSException.create(JSErrorType.TypeError, message, originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotMixBigIntWithOtherTypes(Node originatingNode) {
        return createTypeError("Cannot mix BigInt and other types, use explicit conversions.", originatingNode);
    }

    @TruffleBoundary
    public static JSException createErrorCanNotConvertToBigInt(JSErrorType type, Object x) {
        return JSException.create(type, String.format("Cannot convert %s to a BigInt.", JSRuntime.safeToString(x)));
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotConvertBigIntToNumber(Node originatingNode) {
        return createTypeError("Cannot convert a BigInt value to a number.", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotAFunction(Object functionObj) {
        return createTypeErrorNotAFunction(functionObj, null);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotAFunction(Object functionObj, Node originatingNode) {
        assert !JSFunction.isJSFunction(functionObj); // don't lie to me
        return JSException.create(JSErrorType.TypeError, String.format("%s is not a function", JSRuntime.safeToString(functionObj)), originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotAConstructor(Object object) {
        return createTypeErrorNotAConstructor(object, null);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotAConstructor(Object object, Node originatingNode) {
        return JSException.create(JSErrorType.TypeError, String.format("%s is not a constructor", JSRuntime.safeToString(object)), originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorTypeXExpected(String type) {
        return createTypeErrorFormat("%s object expected.", type);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCalledOnNonObject() {
        return createTypeError("called on non-object");
    }

    @TruffleBoundary
    public static JSException createTypeErrorMethodCalledOnNonObjectOrWrongType(String method) {
        return createTypeErrorFormat("Method %s called on a non-object or on a wrong type of object.", method);
    }

    @TruffleBoundary
    public static JSException createTypeErrorSegmenterExpected() {
        return createTypeError("Segmenter object expected.");
    }

    @TruffleBoundary
    public static JSException createSyntaxError(String message, Throwable cause, Node originatingNode) {
        return JSException.create(JSErrorType.SyntaxError, message, cause, originatingNode);
    }

    @TruffleBoundary
    public static JSException createSyntaxError(String message) {
        return JSException.create(JSErrorType.SyntaxError, message);
    }

    @TruffleBoundary
    public static JSException createSyntaxError(String message, Node originatingNode) {
        return JSException.create(JSErrorType.SyntaxError, message, originatingNode);
    }

    @TruffleBoundary
    public static JSException createSyntaxError(String message, SourceSection sourceLocation, boolean isIncompleteSource) {
        return JSException.create(JSErrorType.SyntaxError, message, sourceLocation, isIncompleteSource);
    }

    @TruffleBoundary
    public static JSException createSyntaxErrorVariableAlreadyDeclared(String varName, Node originatingNode) {
        return Errors.createSyntaxError("Variable \"" + varName + "\" has already been declared", originatingNode);
    }

    @TruffleBoundary
    public static JSException createReferenceError(String message, Node originatingNode) {
        return JSException.create(JSErrorType.ReferenceError, message, originatingNode);
    }

    @TruffleBoundary
    public static JSException createReferenceError(String message, Throwable cause, Node originatingNode) {
        return JSException.create(JSErrorType.ReferenceError, message, cause, originatingNode);
    }

    @TruffleBoundary
    public static JSException createReferenceError(String message) {
        return JSException.create(JSErrorType.ReferenceError, message);
    }

    @TruffleBoundary
    public static JSException createReferenceError(String message, SourceSection sourceLocation) {
        return JSException.create(JSErrorType.ReferenceError, message, sourceLocation, false);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotObjectCoercible(Object value) {
        return createTypeErrorNotObjectCoercible(value, null);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotObjectCoercible(Object value, Node originatingNode) {
        if (JSTruffleOptions.NashornCompatibilityMode) {
            return Errors.createTypeErrorNotAnObject(value, originatingNode);
        }
        return Errors.createTypeError("Cannot convert undefined or null to object: " + JSRuntime.safeToString(value), originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotAnObject(Object value) {
        return Errors.createTypeErrorNotAnObject(value, null);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotAnObject(Object value, Node originatingNode) {
        return Errors.createTypeError(JSRuntime.safeToString(value) + " is not an Object", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorIterResultNotAnObject(Object value, Node originatingNode) {
        return Errors.createTypeErrorNotAnObject(value, originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotIterable(Object value, Node originatingNode) {
        return Errors.createTypeError(JSRuntime.safeToString(value) + "  is not iterable", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorInvalidPrototype(Object value) {
        return Errors.createTypeError("Object prototype may only be an Object or null: " + JSRuntime.safeToString(value));
    }

    @TruffleBoundary
    public static JSException createTypeErrorInvalidInstanceofTarget(Object target, Node originatingNode) {
        if (!JSRuntime.isObject(target)) {
            return Errors.createTypeError("Right-hand-side of instanceof is not an object", originatingNode);
        } else {
            assert !JSRuntime.isCallable(target);
            return Errors.createTypeError("Right-hand-side of instanceof is not callable", originatingNode);
        }
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotConvertToPrimitiveValue() {
        return Errors.createTypeError("Cannot convert object to primitive value");
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotConvertToPrimitiveValue(Node originatingNode) {
        return Errors.createTypeError("Cannot convert object to primitive value", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotConvertToString(String what) {
        return Errors.createTypeErrorCannotConvertToString(what, null);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotConvertToString(String what, Node originatingNode) {
        return Errors.createTypeError("Cannot convert " + what + " to a string", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotConvertToNumber(String what) {
        return Errors.createTypeErrorCannotConvertToNumber(what, null);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotConvertToNumber(String what, Node originatingNode) {
        return Errors.createTypeError("Cannot convert " + what + " to a number", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorIncompatibleReceiver(String methodName, Object receiver) {
        return Errors.createTypeError("Method " + methodName + " called on incompatible receiver " + JSRuntime.safeToString(receiver));
    }

    @TruffleBoundary
    public static JSException createTypeErrorIncompatibleReceiver(Object what) {
        return Errors.createTypeError("incompatible receiver: " + JSRuntime.safeToString(what));
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotSetProto(DynamicObject thisObj, DynamicObject proto) {
        if (!JSBuiltinObject.checkProtoCycle(thisObj, proto)) {
            if (JSObject.getJSContext(thisObj).isOptionNashornCompatibilityMode()) {
                return Errors.createTypeError("Cannot create__proto__ cycle for " + JSObject.defaultToString(thisObj));
            }
            return Errors.createTypeError("Cyclic __proto__ value");
        }
        throw Errors.createTypeError("Cannot set __proto__ of non-extensible " + JSObject.defaultToString(thisObj));
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotWritableProperty(Object key, Object thisObj, Node originatingNode) {
        return Errors.createTypeError(keyToString(key) + " is not a writable property of " + JSRuntime.safeToString(thisObj), originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotWritableProperty(Object key, Object thisObj) {
        return createTypeErrorNotWritableProperty(key, thisObj, null);
    }

    @TruffleBoundary
    public static JSException createTypeErrorLengthNotWritable() {
        return Errors.createTypeError("Cannot assign to read only property 'length'");
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotConfigurableProperty(Object key) {
        return JSException.create(JSErrorType.TypeError, keyToString(key) + " is not a configurable property");
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotExtensible(DynamicObject thisObj, Object key) {
        return Errors.createTypeError("Cannot add new property " + keyToString(key) + " to non-extensible " + JSObject.defaultToString(thisObj));
    }

    @TruffleBoundary
    public static JSException createTypeErrorSetNonObjectReceiver(Object receiver, Object key) {
        return Errors.createTypeError("Cannot add property " + keyToString(key) + " to non-object " + JSRuntime.safeToString(receiver));
    }

    @TruffleBoundary
    public static JSException createTypeErrorConstReassignment(Object key, Object thisObj, Node originatingNode) {
        if (JSObject.isJSObject(thisObj) && JSObject.getJSContext((DynamicObject) thisObj).isOptionV8CompatibilityMode()) {
            throw Errors.createTypeError("Assignment to constant variable.", originatingNode);
        }
        throw Errors.createTypeError("Assignment to constant \"" + key + "\"", originatingNode);
    }

    private static String keyToString(Object key) {
        assert JSRuntime.isPropertyKey(key);
        return key instanceof String ? "\"" + key + "\"" : key.toString();
    }

    @TruffleBoundary
    public static JSException createReferenceErrorNotDefined(Object key, Node originatingNode) {
        return Errors.createReferenceError(quoteKey(key) + " is not defined", originatingNode);
    }

    private static String quoteKey(Object key) {
        return JSTruffleOptions.NashornCompatibilityMode ? "\"" + key + "\"" : key.toString();
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotConstructible(DynamicObject functionObj) {
        assert JSFunction.isJSFunction(functionObj);
        String message = String.format("%s is not a constructor function", JSRuntime.toString(functionObj));
        return JSException.create(JSErrorType.TypeError, message);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotRedefineProperty(Object key) {
        assert JSRuntime.isPropertyKey(key);
        return Errors.createTypeErrorFormat("Cannot redefine property %s", key);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotSetProperty(Object key, Object object, Node originatingNode) {
        assert JSRuntime.isPropertyKey(key);
        String errorMessage;
        if (JSTruffleOptions.NashornCompatibilityMode) {
            errorMessage = "Cannot set property \"" + key + "\" of " + JSRuntime.safeToString(object);
        } else {
            errorMessage = "Cannot set property '" + key + "' of " + JSRuntime.safeToString(object);
        }
        return createTypeError(errorMessage, originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotSetAccessorProperty(Object key, DynamicObject store) {
        assert JSRuntime.isPropertyKey(key);
        String message = JSTruffleOptions.NashornCompatibilityMode ? "Cannot set property \"%s\" of %s that has only a getter" : "Cannot set property %s of %s which has only a getter";
        return Errors.createTypeErrorFormat(message, key, JSObject.defaultToString(store));
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotGetProperty(Object key, Object object, boolean isGetMethod, Node originatingNode) {
        assert JSRuntime.isPropertyKey(key);
        String errorMessage;
        if (JSTruffleOptions.NashornCompatibilityMode) {
            if (isGetMethod) {
                errorMessage = JSRuntime.safeToString(object) + " has no such function \"" + key + "\"";
            } else {
                if (object == Null.instance) {
                    errorMessage = "Cannot get property \"" + key + "\" of " + Null.NAME;
                } else {
                    errorMessage = "Cannot read property \"" + key + "\" from " + JSRuntime.safeToString(object);
                }
            }
        } else {
            errorMessage = "Cannot read property '" + key + "' of " + JSRuntime.safeToString(object);
        }
        return createTypeError(errorMessage, originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotDeclareGlobalFunction(String varName, Node originatingNode) {
        return Errors.createTypeError("Cannot declare global function '" + varName + "'", originatingNode);
    }

    @TruffleBoundary
    public static JSException createRangeErrorCurrencyNotWellFormed(String currencyCode) {
        return createRangeError(String.format("Currency, %s, is not well formed.", currencyCode));
    }

    @TruffleBoundary
    public static JSException createRangeErrorInvalidUnitArgument(String functionName, String unit) {
        return createRangeError(String.format("Invalid unit argument for %s() '%s'", functionName, unit));
    }

    @TruffleBoundary
    public static JSException createTypeErrorMapExpected() {
        return Errors.createTypeError("Map expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorSetExpected() {
        return Errors.createTypeError("Set expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorSymbolExpected() {
        return Errors.createTypeError("Symbol expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorDetachedBuffer() {
        return Errors.createTypeError("Detached buffer");
    }

    @TruffleBoundary
    public static JSException createTypeErrorArrayBufferExpected() {
        return Errors.createTypeError("ArrayBuffer expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorArrayBufferViewExpected() {
        return Errors.createTypeError("TypedArray expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorCallableExpected() {
        return Errors.createTypeError("Callable expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorGeneratorObjectExpected() {
        return Errors.createTypeError("Not a generator object");
    }

    @TruffleBoundary
    public static JSException createTypeErrorAsyncGeneratorObjectExpected() {
        return Errors.createTypeError("Not an async generator object");
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotADataView() {
        return Errors.createTypeError("Not a DataView");
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotADate() {
        return Errors.createTypeError("not a Date object");
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotANumber(Object value) {
        return Errors.createTypeError(JSRuntime.safeToString(value) + " is not a Number");
    }

    @TruffleBoundary
    public static JSException createTypeErrorGlobalObjectNotExtensible(Node originatingNode) {
        return Errors.createTypeError("Global object is not extensible", originatingNode);
    }

    @TruffleBoundary
    public static JSException createRangeErrorTooManyArguments() {
        return Errors.createRangeError("Maximum call stack size exceeded");
    }

    @TruffleBoundary
    public static JSException createRangeErrorBigIntMaxSizeExceeded() {
        return Errors.createRangeError("Maximum BigInt size exceeded");
    }

    @TruffleBoundary
    public static JSException createRangeErrorStackOverflow() {
        return Errors.createRangeError("Maximum call stack size exceeded");
    }

    @TruffleBoundary
    public static JSException createRangeErrorStackOverflow(Node originatingNode) {
        return Errors.createRangeError("Maximum call stack size exceeded", originatingNode);
    }

    @TruffleBoundary
    public static JSException createRangeErrorInvalidStringLength() {
        return Errors.createRangeError("Invalid string length");
    }

    @TruffleBoundary
    public static JSException createRangeErrorInvalidStringLength(Node originatingNode) {
        return Errors.createRangeError("Invalid string length", originatingNode);
    }

    @TruffleBoundary
    public static JSException createRangeErrorInvalidArrayLength() {
        return Errors.createRangeError("Invalid array length");
    }

    public static RuntimeException unsupported(String message) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException(message);
    }

    public static RuntimeException notImplemented(String message) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException("not implemented: " + message);
    }

    public static RuntimeException shouldNotReachHere() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(String message) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach here: " + message);
    }

    public static RuntimeException shouldNotReachHere(Throwable exception) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach here", exception);
    }

    @TruffleBoundary
    public static JSException createTypeErrorConfigurableExpected() {
        return createTypeError("configurable expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorSameResultExpected() {
        return createTypeError("same result expected");
    }

    @TruffleBoundary
    public static JSException createTypeErrorYieldStarThrowMethodMissing(Node originatingNode) {
        return createTypeError("yield* protocol violation: iterator does not have a throw method", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotDeletePropertyOf(Object propertyKey, Object object) {
        assert JSRuntime.isPropertyKey(propertyKey);
        return createTypeError("Cannot delete property " + JSRuntime.quote(propertyKey.toString()) + " of " + JSRuntime.safeToString(object));
    }

    @TruffleBoundary
    public static JSException createTypeErrorCannotDeletePropertyOfSealedArray(long index) {
        return createTypeErrorFormat("Cannot delete property \"%d\" of sealed array", index);
    }

    public static JSException createTypeErrorJSObjectExpected() {
        return createTypeError("only JavaScript objects are supported by this operation");
    }

    @TruffleBoundary
    public static JSException createTypeErrorTrapReturnedFalsish(String trap, Object propertyKey) {
        return createTypeError("'" + trap + "' on proxy: trap returned falsish for property '" + propertyKey + "'");
    }

    public static JSException createTypeErrorProxyRevoked() {
        return createTypeError("proxy has been revoked");
    }

    @TruffleBoundary
    public static JSException createTypeErrorProxyGetInvariantViolated(Object propertyKey, Object expectedValue, Object actualValue) {
        String propertyName = propertyKey.toString();
        String expected = JSRuntime.safeToString(expectedValue);
        String actual = JSRuntime.safeToString(actualValue);
        return createTypeError("'get' on proxy: property '" + propertyName +
                        "' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual value (expected '" + expected + "' but got '" + actual + "')");
    }

    public static JSException createTypeErrorInteropException(Object receiver, InteropException cause, String message, Node originatingNode) {
        return createTypeErrorInteropException(receiver, cause, message, null, originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorInteropException(Object receiver, InteropException cause, String message, Object messageDetails, Node originatingNode) {
        String reason = cause.getMessage();
        if (reason == null) {
            reason = cause.getClass().getSimpleName();
        }
        String receiverStr = "foreign object";
        TruffleLanguage.Env env = JavaScriptLanguage.getCurrentEnv();
        if (env.isHostObject(receiver)) {
            try {
                receiverStr = receiver.toString();
            } catch (Exception e) {
                // ignore
            }
        }
        String messageTxt = (messageDetails == null) ? message : String.format("%s (%s)", message, messageDetails);
        return JSException.create(JSErrorType.TypeError, messageTxt + " on " + receiverStr + " failed due to: " + reason, cause, originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorUnboxException(Object receiver, InteropException cause, Node originatingNode) {
        return createTypeErrorInteropException(receiver, cause, "UNBOX", originatingNode);
    }

    @TruffleBoundary
    public static JSException createTypeErrorUnsupportedInteropType(Object value) {
        return Errors.createTypeError("type " + value.getClass().getSimpleName() + " not supported in JavaScript");
    }

    @TruffleBoundary
    public static JSException createTypeErrorNotATruffleObject(String message) {
        return Errors.createTypeError("cannot call " + message + " on a non-interop object");
    }

    @TruffleBoundary
    public static JSException createTypeErrorInvalidIdentifier(Object identifier) {
        return Errors.createTypeError("Invalid identifier: " + JSRuntime.safeToString(identifier));
    }

    @TruffleBoundary
    public static JSException createTypeErrorClassNotFound(String className) {
        return Errors.createTypeErrorFormat("Access to host class %s is not allowed or does not exist.", className);
    }

    @TruffleBoundary
    public static JSException createNotAFileError(String path) {
        return Errors.createTypeError("Not a file: " + path);
    }

    @TruffleBoundary
    public static JSException createErrorFromException(Exception e) {
        return JSException.create(JSErrorType.Error, e.getMessage(), e, null);
    }

    @TruffleBoundary
    public static JSException createError(String message, Throwable e) {
        return JSException.create(JSErrorType.Error, message, e, null);
    }

    @TruffleBoundary
    public static JSException createICU4JDataError(Exception e) {
        return Errors.createError("ICU data not found. ICU4J library not properly configured. " +
                        "Set the system property " +
                        "com.ibm.icu.impl.ICUBinary.dataPath" +
                        " to your icudt path." +
                        (e.getMessage() != null && !e.getMessage().isEmpty() ? " (" + e.getMessage() + ")" : ""), e);
    }

    @TruffleBoundary
    public static JSException createTypeErrorStringExpected() {
        return Errors.createTypeError("string expected");
    }

    @TruffleBoundary
    public static JSException createEvalDisabled() {
        return Errors.createEvalError("dynamic evaluation of code is disabled.");
    }

    @TruffleBoundary
    public static JSException createTypeErrorIteratorResultNotObject(Object value, Node originatingNode) {
        return Errors.createTypeError("Iterator result " + JSRuntime.safeToString(value) + " is not an object", originatingNode);
    }

    @TruffleBoundary
    public static JSException createSIMDExpected() {
        return Errors.createTypeError("SIMD type expected");
    }

}
