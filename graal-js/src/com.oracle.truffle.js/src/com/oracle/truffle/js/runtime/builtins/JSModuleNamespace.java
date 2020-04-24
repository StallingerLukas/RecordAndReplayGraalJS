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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.LocationModifier;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSFrameUtil;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.objects.Dead;
import com.oracle.truffle.js.runtime.objects.ExportResolution;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSModuleRecord;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.JSShape;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.PropertyDescriptor;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.DefinePropertyUtil;

/**
 * Module Namespace Exotic Objects.
 */
public final class JSModuleNamespace extends JSBuiltinObject {

    public static final JSModuleNamespace INSTANCE = new JSModuleNamespace();

    public static final String CLASS_NAME = "Module";

    private static final HiddenKey MODULE_ID = new HiddenKey("module");
    private static final HiddenKey EXPORTS_ID = new HiddenKey("exports");

    /**
     * [[Module]]. Module Record.
     *
     * The Module Record whose exports this namespace exposes.
     */
    private static final Property MODULE_PROPERTY;

    /**
     * [[Exports]]. List of String.
     *
     * A List containing the String values of the exported names exposed as own properties of this
     * object. The list is ordered as if an Array of those String values had been sorted using
     * Array.prototype.sort using SortCompare as comparefn.
     */
    private static final Property EXPORTS_PROPERTY;

    static {
        Shape.Allocator allocator = JSShape.makeAllocator(JSObject.LAYOUT);
        MODULE_PROPERTY = JSObjectUtil.makeHiddenProperty(MODULE_ID, allocator.locationForType(Object.class, EnumSet.of(LocationModifier.Final, LocationModifier.NonNull)));
        EXPORTS_PROPERTY = JSObjectUtil.makeHiddenProperty(EXPORTS_ID, allocator.locationForType(Object.class, EnumSet.of(LocationModifier.Final, LocationModifier.NonNull)));
    }

    private JSModuleNamespace() {
    }

    public static JSModuleRecord getModule(DynamicObject obj) {
        assert isJSModuleNamespace(obj);
        return (JSModuleRecord) MODULE_PROPERTY.get(obj, isJSModuleNamespace(obj));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ExportResolution> getExports(DynamicObject obj) {
        assert isJSModuleNamespace(obj);
        return (Map<String, ExportResolution>) EXPORTS_PROPERTY.get(obj, isJSModuleNamespace(obj));
    }

    public static DynamicObject create(JSContext context, Object module, Map<String, ExportResolution> exports) {
        DynamicObject obj = JSObject.createWithBoundPrototype(context, context.getModuleNamespaceFactory(), module, exports);
        assert isJSModuleNamespace(obj);
        return obj;
    }

    public static Shape makeInitialShape(JSContext context) {
        Shape initialShape = JSShape.makeEmptyRoot(JSObject.LAYOUT, INSTANCE, context);
        initialShape = initialShape.addProperty(MODULE_PROPERTY);
        initialShape = initialShape.addProperty(EXPORTS_PROPERTY);

        /*
         * The initial value of the @@toStringTag property is the String value "Module".
         *
         * This property has the attributes { [[Writable]]: false, [[Enumerable]]: false,
         * [[Configurable]]: false }.
         */
        Property toStringTagProperty = JSObjectUtil.makeDataProperty(Symbol.SYMBOL_TO_STRING_TAG, initialShape.allocator().constantLocation(CLASS_NAME),
                        JSAttributes.notConfigurableNotEnumerableNotWritable());
        initialShape = initialShape.addProperty(toStringTagProperty);

        return JSShape.makeNotExtensible(initialShape);
    }

    @Override
    public String getClassName(DynamicObject object) {
        return CLASS_NAME;
    }

    @Override
    @TruffleBoundary
    public String safeToString(DynamicObject obj, int depth) {
        return "[" + CLASS_NAME + "]";
    }

    @Override
    @TruffleBoundary
    public Object getOwnHelper(DynamicObject store, Object thisObj, Object key) {
        if (!(key instanceof String)) {
            return super.getOwnHelper(store, thisObj, key);
        }
        Map<String, ExportResolution> exports = getExports(store);
        ExportResolution binding = exports.get(key);
        if (binding != null) {
            return getBindingValue(binding);
        } else {
            return Undefined.instance;
        }
    }

    private static Object getBindingValue(ExportResolution binding) {
        JSModuleRecord targetModule = binding.getModule();
        MaterializedFrame targetEnv = targetModule.getEnvironment();
        FrameSlot frameSlot = targetEnv.getFrameDescriptor().findFrameSlot(binding.getBindingName());
        assert frameSlot != null;
        if (JSFrameUtil.hasTemporalDeadZone(frameSlot) && targetEnv.isObject(frameSlot) && FrameUtil.getObjectSafe(targetEnv, frameSlot) == Dead.instance()) {
            // If it is an uninitialized binding, throw a ReferenceError
            throw Errors.createReferenceErrorNotDefined(frameSlot.getIdentifier(), null);
        }
        return targetEnv.getValue(frameSlot);
    }

    @Override
    public boolean hasProperty(DynamicObject thisObj, Object key) {
        if (!(key instanceof String)) {
            return super.hasProperty(thisObj, key);
        }
        Map<String, ExportResolution> exports = getExports(thisObj);
        return Boundaries.mapContainsKey(exports, key);
    }

    @Override
    @TruffleBoundary
    public boolean hasOwnProperty(DynamicObject thisObj, Object key) {
        if (!(key instanceof String)) {
            return super.hasOwnProperty(thisObj, key);
        }
        Map<String, ExportResolution> exports = getExports(thisObj);
        ExportResolution binding = exports.get(key);
        if (binding != null) {
            getBindingValue(binding); // checks for uninitialized bindings
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean delete(DynamicObject thisObj, long index, boolean isStrict) {
        return true;
    }

    @Override
    public boolean delete(DynamicObject thisObj, Object key, boolean isStrict) {
        if (!(key instanceof String)) {
            return super.delete(thisObj, key, isStrict);
        }
        if (Boundaries.mapContainsKey(getExports(thisObj), key)) {
            if (isStrict) {
                throw Errors.createTypeErrorNotConfigurableProperty(key);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public boolean setPrototypeOf(DynamicObject thisObj, DynamicObject newPrototype) {
        return newPrototype == Null.instance;
    }

    @Override
    public boolean defineOwnProperty(DynamicObject thisObj, Object key, PropertyDescriptor desc, boolean doThrow) {
        if (!(key instanceof String)) {
            return super.defineOwnProperty(thisObj, key, desc, doThrow);
        }
        PropertyDescriptor current = getOwnProperty(thisObj, key);
        if (current != null && !desc.isAccessorDescriptor() && desc.getIfHasWritable(true) && desc.getIfHasEnumerable(true) && !desc.getIfHasConfigurable(false) &&
                        (!desc.hasValue() || JSRuntime.isSameValue(desc.getValue(), current.getValue()))) {
            return true;
        }
        return DefinePropertyUtil.reject(doThrow, "not allowed to defineProperty on a namespace object");
    }

    @Override
    @TruffleBoundary
    public PropertyDescriptor getOwnProperty(DynamicObject thisObj, Object key) {
        if (!(key instanceof String)) {
            return super.getOwnProperty(thisObj, key);
        }
        Map<String, ExportResolution> exports = getExports(thisObj);
        ExportResolution binding = exports.get(key);
        if (binding != null) {
            Object value = getBindingValue(binding);
            return PropertyDescriptor.createData(value, true, true, false);
        } else {
            return null;
        }
    }

    public static boolean isJSModuleNamespace(Object obj) {
        return JSObject.isDynamicObject(obj) && isJSModuleNamespace((DynamicObject) obj);
    }

    public static boolean isJSModuleNamespace(DynamicObject obj) {
        return isInstance(obj, INSTANCE);
    }

    @TruffleBoundary
    @Override
    public List<Object> getOwnPropertyKeys(DynamicObject thisObj, boolean strings, boolean symbols) {
        List<Object> symbolKeys = symbols ? symbolKeys(thisObj) : Collections.emptyList();
        if (!strings) {
            return symbolKeys;
        }
        Map<String, ExportResolution> exports = getExports(thisObj);
        List<Object> keys = new ArrayList<>(exports.size() + symbolKeys.size());
        keys.addAll(exports.keySet());
        keys.addAll(symbolKeys);
        return keys;
    }

    private static List<Object> symbolKeys(DynamicObject thisObj) {
        // Module Namespace objects only have symbol keys in their shapes.
        return thisObj.getShape().getKeyList();
    }

    @Override
    @TruffleBoundary
    public boolean setIntegrityLevel(DynamicObject obj, boolean freeze) {
        if (freeze) {
            Map<String, ExportResolution> exports = getExports(obj);
            if (!exports.isEmpty()) {
                ExportResolution firstBinding = exports.values().iterator().next();
                // Throw ReferenceError if the first binding is uninitialized,
                // throw TypeError otherwise
                getBindingValue(firstBinding); // checks for an uninitialized binding
                throw Errors.createTypeError("not allowed to freeze a namespace object");
            }
        } else {
            // Check for uninitalized bindings
            for (ExportResolution binding : getExports(obj).values()) {
                getBindingValue(binding); // can throw ReferenceError
            }
        }
        return true;
    }

    @Override
    public boolean usesOrdinaryGetOwnProperty() {
        return false;
    }
}
