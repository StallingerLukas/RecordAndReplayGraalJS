/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.builtins;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;
import com.oracle.truffle.js.builtins.ForInIteratorPrototypeBuiltinsFactory.ForInIteratorPrototypeNextNodeGen;
import com.oracle.truffle.js.builtins.ForInIteratorPrototypeBuiltinsFactory.HasOnlyShapePropertiesNodeGen;
import com.oracle.truffle.js.builtins.helper.ListGetNode;
import com.oracle.truffle.js.builtins.helper.ListSizeNode;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.GetPrototypeNode;
import com.oracle.truffle.js.nodes.access.PropertyGetNode;
import com.oracle.truffle.js.nodes.access.PropertySetNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSClass;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSObjectPrototype;
import com.oracle.truffle.js.runtime.builtins.JSUserObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSProperty;
import com.oracle.truffle.js.runtime.objects.JSShape;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.PropertyDescriptor;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.ForInIterator;

/**
 * Functions of the %ForInIteratorPrototype% object.
 */
public final class ForInIteratorPrototypeBuiltins extends JSBuiltinsContainer.Switch {
    protected ForInIteratorPrototypeBuiltins() {
        super(JSFunction.FOR_IN_ITERATOR_PROTOYPE_NAME);
        defineFunction("next", 0);
    }

    public enum EnumerateIteratorPrototype implements BuiltinEnum<EnumerateIteratorPrototype> {
        next(0);

        private final int length;

        EnumerateIteratorPrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget) {
        switch (builtin.getName()) {
            case "next":
                return ForInIteratorPrototypeNextNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class ForInIteratorPrototypeNextNode extends JSBuiltinNode {
        @Child private PropertySetNode setValueNode;
        @Child private PropertySetNode setDoneNode;
        @Child private PropertyGetNode getIteratorNode;
        @Child private GetPrototypeNode getPrototypeNode;
        @Child private HasOnlyShapePropertiesNode hasOnlyShapePropertiesNode;
        @Child private ListGetNode listGet;
        @Child private ListSizeNode listSize;
        private final BranchProfile errorBranch = BranchProfile.create();
        private final BranchProfile growProfile = BranchProfile.create();
        private final ConditionProfile fastOwnKeysProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile sameShapeProfile = ConditionProfile.createBinaryProfile();

        private static final Object DONE = null;
        private static final int MAX_PROTO_DEPTH = 1000;

        public ForInIteratorPrototypeNextNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
            this.setValueNode = PropertySetNode.create(JSRuntime.VALUE, false, context, false);
            this.setDoneNode = PropertySetNode.create(JSRuntime.DONE, false, context, false);
            this.getIteratorNode = PropertyGetNode.createGetHidden(JSRuntime.FOR_IN_ITERATOR_ID, context);
            this.getPrototypeNode = GetPrototypeNode.create();
            this.hasOnlyShapePropertiesNode = HasOnlyShapePropertiesNode.create();
            this.listGet = ListGetNode.create();
            this.listSize = ListSizeNode.create();
        }

        @Specialization
        public DynamicObject execute(Object target,
                        @Cached("createEqualityProfile()") PrimitiveValueProfile valuesProfile) {
            Object iteratorValue = getIteratorNode.getValue(target);
            if (iteratorValue == Undefined.instance) {
                errorBranch.enter();
                throw Errors.createTypeErrorIncompatibleReceiver(target);
            }
            ForInIterator state = (ForInIterator) iteratorValue;
            Object nextValue = findNext(state);
            boolean done = nextValue == DONE;
            if (done) {
                nextValue = Undefined.instance;
            } else {
                if (valuesProfile.profile(state.iterateValues)) {
                    nextValue = JSObject.get(state.object, nextValue);
                } else {
                    assert nextValue instanceof String;
                }
            }
            return createIterResultObject(nextValue, done);
        }

        private Object findNext(ForInIterator state) {
            for (;;) {
                DynamicObject object = state.object;
                if (!state.objectWasVisited) {
                    JSClass jsclass = JSObject.getJSClass(object);
                    boolean fastOwnKeys;
                    List<?> list;
                    int size;
                    if (fastOwnKeysProfile.profile(JSTruffleOptions.FastOwnKeys && hasOnlyShapePropertiesNode.execute(object, jsclass))) {
                        fastOwnKeys = true;
                        list = JSShape.getEnumerablePropertyNames(object.getShape());
                        size = list.size();
                        // if the object does not have enumerable properties, no need to enumerate
                        if (size != 0) {
                            list = JSShape.getProperties(object.getShape());
                            size = list.size();
                        }
                    } else {
                        fastOwnKeys = false;
                        list = jsclass.ownPropertyKeys(object);
                        size = listSize.execute(list);
                    }
                    state.objectShape = object.getShape();
                    state.remainingKeys = list;
                    state.remainingKeysSize = size;
                    state.remainingKeysIndex = 0;
                    state.fastOwnKeys = fastOwnKeys;
                    state.objectWasVisited = true;
                }

                assert state.remainingKeysSize == state.remainingKeys.size();
                while (state.remainingKeysIndex < state.remainingKeysSize) {
                    final Object next = listGet.execute(state.remainingKeys, state.remainingKeysIndex++);
                    final Object key = getKey(next);
                    if (!(key instanceof String)) {
                        continue;
                    }
                    if (state.isVisitedKey(key)) {
                        continue;
                    }

                    if (fastOwnKeysProfile.profile(state.fastOwnKeys && next instanceof Property)) {
                        if (sameShapeProfile.profile(state.objectShape == object.getShape())) {
                            // same shape => can skip GetOwnProperty
                            if (JSProperty.isEnumerable((Property) next)) {
                                return key;
                            } else {
                                continue;
                            }
                        } else {
                            // shape has changed => must perform GetOwnProperty
                            addPreviouslyVisitedKeys(state);
                            state.fastOwnKeys = false;
                            // fall through
                        }
                    }

                    PropertyDescriptor desc = JSObject.getOwnProperty(object, key);
                    // desc can be null if obj is a Proxy or the property has been deleted
                    if (desc != null) {
                        state.addVisitedKey(key);
                        if (desc.getEnumerable()) {
                            return key;
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }

                DynamicObject proto = getPrototypeNode.executeJSObject(object);
                if (tryFastForwardImmutablePrototype(proto)) {
                    proto = Null.instance;
                }
                state.object = proto;
                state.objectWasVisited = false;
                if (proto == Null.instance) {
                    return DONE;
                } else {
                    if (fastOwnKeysProfile.profile(state.fastOwnKeys)) {
                        state.addVisitedShape(state.objectShape, growProfile);
                    } else {
                        // check for Proxy prototype cycles
                        if (++state.protoDepth > MAX_PROTO_DEPTH) {
                            errorBranch.enter();
                            throw Errors.createRangeErrorStackOverflow();
                        }
                    }
                }
            }
        }

        private static Object getKey(final Object next) {
            return next instanceof Property ? ((Property) next).getKey() : next;
        }

        @TruffleBoundary
        private static void addPreviouslyVisitedKeys(ForInIterator state) {
            for (int i = 0; i < state.remainingKeysIndex - 1; i++) {
                state.addVisitedKey(getKey(state.remainingKeys.get(i)));
            }
        }

        private boolean tryFastForwardImmutablePrototype(DynamicObject proto) {
            if (proto == Null.instance) {
                return false;
            }
            // If none of the remaining prototypes have enumerable properties, we are done.
            // If the object has an immutable prototype (i.e., Object.prototype, Module Namespace),
            // its prototype is always null and we can skip [[GetPrototypeOf]]().
            JSClass jsclass = JSObject.getJSClass(proto);
            if (jsclass == JSObjectPrototype.INSTANCE && hasOnlyShapePropertiesNode.execute(proto, jsclass) && JSShape.getEnumerablePropertyNames(proto.getShape()).isEmpty()) {
                assert JSObject.getPrototype(proto) == Null.instance;
                return true;
            } else {
                return false;
            }
        }

        private DynamicObject createIterResultObject(Object value, boolean done) {
            DynamicObject iterResultObject = JSUserObject.create(getContext());
            setValueNode.setValue(iterResultObject, value);
            setDoneNode.setValueBoolean(iterResultObject, done);
            return iterResultObject;
        }
    }

    @ImportStatic({JSObject.class})
    public abstract static class HasOnlyShapePropertiesNode extends JavaScriptBaseNode {

        protected HasOnlyShapePropertiesNode() {
        }

        public static HasOnlyShapePropertiesNode create() {
            return HasOnlyShapePropertiesNodeGen.create();
        }

        public final boolean execute(DynamicObject object) {
            return execute(object, JSObject.getJSClass(object));
        }

        public abstract boolean execute(DynamicObject object, JSClass jsclass);

        @Specialization(guards = {"jsclass == cachedJSClass", "!isJSObjectPrototype(cachedJSClass)"}, limit = "3")
        static boolean doCached(DynamicObject object, @SuppressWarnings("unused") JSClass jsclass,
                        @Cached(value = "jsclass") JSClass cachedJSClass) {
            return cachedJSClass.hasOnlyShapeProperties(object);
        }

        @Specialization(guards = {"isJSObjectPrototype(jsclass)"})
        static boolean doObjectPrototype(DynamicObject object, JSClass jsclass,
                        @Cached("getJSContext(object)") JSContext context) {
            if (context.getArrayPrototypeNoElementsAssumption().isValid()) {
                assert jsclass.hasOnlyShapeProperties(object);
                return true;
            }
            return JSObjectPrototype.INSTANCE.hasOnlyShapeProperties(object);
        }

        @Specialization(replaces = {"doCached", "doObjectPrototype"})
        static boolean doUncached(DynamicObject object, JSClass jsclass) {
            return jsclass.hasOnlyShapeProperties(object);
        }

        static boolean isJSObjectPrototype(JSClass jsclass) {
            return jsclass == JSObjectPrototype.INSTANCE;
        }
    }
}
