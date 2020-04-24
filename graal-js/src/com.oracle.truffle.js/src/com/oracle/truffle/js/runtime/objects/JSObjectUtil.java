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
package com.oracle.truffle.js.runtime.objects;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.LocationFactory;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.builtins.Builtin;
import com.oracle.truffle.js.runtime.builtins.JSClass;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;

/**
 * @see DynamicObject
 */
public final class JSObjectUtil {
    private static final HiddenKey PROTOTYPE_DATA = new HiddenKey("PROTOTYPE_DATA");

    private JSObjectUtil() {
        // this utility class should not be instantiated
    }

    public static Location createConstantLocation(Object value) {
        return JSObject.LAYOUT.createAllocator().constantLocation(value);
    }

    private static LocationFactory declaredLocationFactory() {
        return (shape, val) -> shape.allocator().declaredLocation(val);
    }

    public static Shape shapeDefineDataProperty(JSContext context, Shape shape, Object key, Object value, int flags) {
        CompilerAsserts.neverPartOfCompilation();
        return shape.defineProperty(checkForNoSuchPropertyOrMethod(context, key), value, flags);
    }

    public static Shape shapeDefineDeclaredDataProperty(JSContext context, Shape shape, Object key, Object value, int flags) {
        CompilerAsserts.neverPartOfCompilation();
        return shape.defineProperty(checkForNoSuchPropertyOrMethod(context, key), value, flags, declaredLocationFactory());
    }

    @TruffleBoundary
    public static void putDataProperty(JSContext context, DynamicObject thisObj, Object key, Object value, int flags) {
        assert checkForExistingProperty(thisObj, key);

        thisObj.define(checkForNoSuchPropertyOrMethod(context, key), value, flags);
    }

    public static void putDataProperty(DynamicObject thisObj, Object name, Object value, int flags) {
        JSContext context = JSObject.getJSContext(thisObj);
        putDataProperty(context, thisObj, name, value, flags);
    }

    public static void defineDataProperty(JSContext context, DynamicObject thisObj, Object key, Object value, int flags) {
        thisObj.define(checkForNoSuchPropertyOrMethod(context, key), value, flags);
    }

    public static void defineDataProperty(DynamicObject thisObj, Object key, Object value, int flags) {
        JSContext context = JSObject.getJSContext(thisObj);
        defineDataProperty(context, thisObj, key, value, flags);
    }

    public static void putOrSetDataProperty(JSContext context, DynamicObject thisObj, Object key, Object value, int flags) {
        if (!JSObject.hasOwnProperty(thisObj, key)) {
            JSObjectUtil.putDataProperty(context, thisObj, key, value, flags);
        } else {
            JSObject.set(thisObj, key, value);
        }
    }

    public static Property makeDataProperty(Object key, Location location, int flags) {
        assert JSRuntime.isPropertyKey(key);
        return Property.create(key, location, flags);
    }

    public static Property makeAccessorProperty(String name, Location location, int flags) {
        return Property.create(name, location, flags | JSProperty.ACCESSOR);
    }

    public static Property makeProxyProperty(String name, PropertyProxy proxy, int flags) {
        return makeProxyProperty(name, createConstantLocation(proxy), flags);
    }

    public static Property makeProxyProperty(String name, Location location, int flags) {
        return Property.create(name, location, flags | JSProperty.PROXY);
    }

    public static Property makeHiddenProperty(HiddenKey id, Location location) {
        return makeHiddenProperty(id, location, false);
    }

    public static Property makeHiddenProperty(HiddenKey id, Location location, boolean relocatable) {
        return Property.create(id, location, 0).copyWithRelocatable(relocatable);
    }

    public static void defineAccessorProperty(DynamicObject thisObj, Object key, Accessor accessor, int flags) {
        int finalFlags = flags | JSProperty.ACCESSOR;

        JSContext context = JSObject.getJSContext(thisObj);
        thisObj.define(checkForNoSuchPropertyOrMethod(context, key), accessor, finalFlags);
    }

    public static void defineProxyProperty(DynamicObject thisObj, Object key, PropertyProxy proxy, int flags) {
        int finalFlags = flags | JSProperty.PROXY;

        JSContext context = JSObject.getJSContext(thisObj);
        thisObj.define(checkForNoSuchPropertyOrMethod(context, key), proxy, finalFlags);
    }

    public static void changeFlags(DynamicObject thisObj, Object key, int flags) {
        // only javascript flags allowed here
        assert flags == (flags & JSAttributes.ATTRIBUTES_MASK);

        changeFlags(thisObj, key, (attr) -> (attr & ~JSAttributes.ATTRIBUTES_MASK) | flags);
    }

    @TruffleBoundary
    private static boolean changeFlags(DynamicObject obj, Object key, IntUnaryOperator flagsOp) {
        Shape oldShape = obj.getShape();
        Property existing = oldShape.getProperty(key);
        if (existing != null) {
            int newFlags = flagsOp.applyAsInt(existing.getFlags());
            if (existing.getFlags() != newFlags) {
                Property newProperty = existing.copyWithFlags(newFlags);
                Shape newShape = oldShape.replaceProperty(existing, newProperty);
                obj.setShapeAndGrow(oldShape, newShape);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Put preallocated data property that must not be moved.
     */
    public static void putDataProperty(JSContext context, DynamicObject thisObj, Property dataProperty, Object value) {
        assert JSProperty.isData(dataProperty);
        assert checkForExistingProperty(thisObj, dataProperty.getKey());

        checkForNoSuchPropertyOrMethod(context, dataProperty.getKey());
        defineProperty(thisObj, dataProperty, value);
    }

    private static void defineProperty(DynamicObject thisObj, Property property, Object value) {
        thisObj.define(property.getKey(), value, property.getFlags(), (shape, val) -> property.getLocation());
    }

    public static void putDataProperty(JSContext context, DynamicObject thisObj, String name, Object value) {
        assert checkForExistingProperty(thisObj, name);
        putDataProperty(context, thisObj, name, value, JSAttributes.notConfigurableNotEnumerableNotWritable());
    }

    @TruffleBoundary
    public static void putDeclaredDataProperty(JSContext context, DynamicObject thisObj, Object key, Object value, int flags) {
        assert checkForExistingProperty(thisObj, key);

        thisObj.define(checkForNoSuchPropertyOrMethod(context, key), value, flags, declaredLocationFactory());
    }

    public static void putConstructorProperty(JSContext context, DynamicObject prototype, DynamicObject constructor) {
        putDataProperty(context, prototype, JSObject.CONSTRUCTOR, constructor, JSAttributes.configurableNotEnumerableWritable());
    }

    public static void putConstructorPrototypeProperty(JSContext ctx, DynamicObject constructor, DynamicObject prototype) {
        putDataProperty(ctx, constructor, JSObject.PROTOTYPE, prototype, JSAttributes.notConfigurableNotEnumerableNotWritable());
    }

    public static void putAccessorProperty(JSContext context, DynamicObject thisObj, Object key, DynamicObject getter, DynamicObject setter, int flags) {
        Accessor accessor = new Accessor(getter, setter);
        putAccessorProperty(context, thisObj, key, accessor, flags);
    }

    public static void putAccessorProperty(JSContext context, DynamicObject thisObj, Object key, Accessor accessor, int flags) {
        assert JSRuntime.isPropertyKey(key);
        assert checkForExistingProperty(thisObj, key);

        thisObj.define(checkForNoSuchPropertyOrMethod(context, key), accessor, flags | JSProperty.ACCESSOR);
    }

    public static void putConstantAccessorProperty(JSContext context, DynamicObject thisObj, Object key, DynamicObject getter, DynamicObject setter) {
        putConstantAccessorProperty(context, thisObj, key, getter, setter, JSAttributes.configurableNotEnumerable());
    }

    public static void putConstantAccessorProperty(JSContext context, DynamicObject thisObj, Object key, DynamicObject getter, DynamicObject setter, int flags) {
        putAccessorProperty(context, thisObj, key, getter, setter, flags);
    }

    public static void putProxyProperty(DynamicObject thisObj, Property proxyProperty) {
        assert JSProperty.isProxy(proxyProperty);
        assert checkForExistingProperty(thisObj, proxyProperty.getKey());

        defineProperty(thisObj, proxyProperty, JSProperty.getConstantProxy(proxyProperty));
    }

    private static boolean checkForExistingProperty(DynamicObject thisObj, Object key) {
        assert !thisObj.getShape().hasProperty(key) : "Don't put a property that already exists. Use the setters.";
        return true;
    }

    public static void putHiddenProperty(DynamicObject thisObj, Property property, Object value) {
        assert property.isHidden();
        defineProperty(thisObj, property, value);
    }

    /**
     * Get or create a prototype child shape inheriting from this object, migrating the object to a
     * unique shape in the process. Creating unique shapes should be avoided in the fast path.
     */
    public static Shape getProtoChildShape(DynamicObject obj, JSClass jsclass, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        if (obj == null) {
            return context.makeEmptyShapeWithPrototypeInObject(jsclass, JSObject.PROTO_PROPERTY);
        }
        assert JSRuntime.isObject(obj);
        Shape protoChild = getProtoChildShapeMaybe(obj, jsclass);
        if (protoChild != null) {
            return protoChild;
        }

        return getProtoChildShapeSlowPath(obj, jsclass, context);
    }

    public static Shape getProtoChildShape(DynamicObject obj, JSClass jsclass, JSContext context, BranchProfile branchProfile) {
        Shape protoChild = getProtoChildShapeMaybe(obj, jsclass);
        if (protoChild != null) {
            return protoChild;
        }

        branchProfile.enter();
        return getProtoChildShapeSlowPath(obj, jsclass, context);
    }

    private static Shape getProtoChildShapeMaybe(DynamicObject obj, JSClass jsclass) {
        Shape protoChild = JSShape.getProtoChildTree(obj, jsclass);
        assert protoChild == null || JSShape.getJSClassNoCast(protoChild) == jsclass;
        return protoChild;
    }

    @TruffleBoundary
    private static Shape getProtoChildShapeSlowPath(DynamicObject obj, JSClass jsclass, JSContext context) {
        JSPrototypeData prototypeData = getPrototypeData(obj);
        if (prototypeData == null) {
            prototypeData = putPrototypeData(obj);
        }
        return prototypeData.getOrAddProtoChildTree(jsclass, createChildRootShape(obj, jsclass, context));
    }

    private static Shape createChildRootShape(DynamicObject obj, JSClass jsclass, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        return JSShape.makeRootShape(JSObject.LAYOUT, new JSSharedData(context, JSShape.makePrototypeProperty(obj)), jsclass);
    }

    public static JSPrototypeData putPrototypeData(DynamicObject obj) {
        CompilerAsserts.neverPartOfCompilation();
        assert getPrototypeData(obj) == null;
        JSPrototypeData prototypeData = new JSPrototypeData();
        putPrototypeData(obj, prototypeData);
        return prototypeData;
    }

    private static void putPrototypeData(DynamicObject obj, JSPrototypeData prototypeData) {
        boolean wasNotExtensible = !JSShape.isExtensible(obj.getShape());
        obj.define(PROTOTYPE_DATA, prototypeData);
        if (wasNotExtensible && JSObject.isExtensible(obj)) {
            // not-extensible marker property is expected to be the last property; ensure it is.
            obj.delete(JSShape.NOT_EXTENSIBLE_KEY);
            JSObject.preventExtensions(obj);
            assert !JSObject.isExtensible(obj);
        }
    }

    static JSPrototypeData getPrototypeData(DynamicObject obj) {
        return (JSPrototypeData) obj.get(PROTOTYPE_DATA);
    }

    public static Map<Object, Object> archive(DynamicObject obj) {
        HashMap<Object, Object> ret = new HashMap<>();
        Shape shape = obj.getShape();
        for (Property prop : shape.getPropertyListInternal(false)) {
            if (!(prop.getLocation().isValue()) && !ret.containsKey(prop.getKey())) {
                ret.put(prop.getKey(), prop.get(obj, false));
            }
        }
        return ret;
    }

    @TruffleBoundary
    public static void setPrototype(DynamicObject object, DynamicObject newPrototype) {
        CompilerAsserts.neverPartOfCompilation("do not set object prototype from compiled code");

        final JSContext context = JSObject.getJSContext(object);
        final Shape oldShape = object.getShape();
        JSShape.invalidatePrototypeAssumption(oldShape);
        final Shape newRootShape;
        if (newPrototype == Null.instance) {
            newRootShape = context.makeEmptyShapeWithNullPrototype(JSShape.getJSClass(oldShape));
        } else {
            assert JSRuntime.isObject(newPrototype) : newPrototype;
            newRootShape = JSObjectUtil.getProtoChildShape(newPrototype, JSShape.getJSClass(oldShape), context);
        }
        Map<Object, Object> archive = archive(object);
        object.setShapeAndResize(oldShape, newRootShape);

        Shape newShape = newRootShape;
        boolean sameLocations = true;
        for (Property p : oldShape.getPropertyListInternal(true)) {
            Object key = p.getKey();
            if (!newRootShape.hasProperty(key)) {
                if (p.getLocation().isValue()) {
                    newShape = newShape.addProperty(p);
                    object.setShapeAndGrow(newShape.getParent(), newShape);
                } else if (p.isHidden() && sameLocations) {
                    newShape = newShape.addProperty(p);
                    newShape.getLastProperty().setSafe(object, archive.get(key), newShape.getParent(), newShape);
                } else {
                    sameLocations = false;

                    // we're allocating new property locations, so previous assumptions are invalid
                    JSShape.invalidatePropertyAssumption(oldShape, key);

                    Object value = archive.get(key);
                    newShape = newShape.defineProperty(key, value, p.getFlags());
                    newShape.getLastProperty().setSafe(object, value, newShape.getParent(), newShape);
                }
            }
        }
    }

    public static <T> T checkForNoSuchPropertyOrMethod(JSContext context, T key) {
        if (context != null && key != null && context.isOptionNashornCompatibilityMode()) {
            if (context.getNoSuchPropertyUnusedAssumption().isValid() && key.equals(JSObject.NO_SUCH_PROPERTY_NAME)) {
                context.getNoSuchPropertyUnusedAssumption().invalidate("NoSuchProperty is used");
            }
            if (context.getNoSuchMethodUnusedAssumption().isValid() && key.equals(JSObject.NO_SUCH_METHOD_NAME)) {
                context.getNoSuchMethodUnusedAssumption().invalidate("NoSuchMethod is used");
            }
        }
        return key;
    }

    public static void putFunctionsFromContainer(JSRealm realm, DynamicObject thisObj, String containerName) {
        JSContext context = realm.getContext();
        context.getFunctionLookup().iterateBuiltinFunctions(containerName, new Consumer<Builtin>() {
            @Override
            public void accept(Builtin builtin) {
                if (builtin.getECMAScriptVersion() > context.getEcmaScriptVersion()) {
                    return;
                } else if (builtin.isAnnexB() && !context.isOptionAnnexB()) {
                    return;
                }
                JSFunctionData functionData = builtin.createFunctionData(context);
                putDataProperty(context, thisObj, builtin.getKey(), JSFunction.create(realm, functionData), builtin.getAttributeFlags());
            }
        });
    }
}
