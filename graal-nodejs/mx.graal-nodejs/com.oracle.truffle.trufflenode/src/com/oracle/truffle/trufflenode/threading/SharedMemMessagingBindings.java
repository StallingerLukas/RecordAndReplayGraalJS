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
package com.oracle.truffle.trufflenode.threading;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.builtins.JSBuiltinLookup;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JavaScriptRootNode;
import com.oracle.truffle.js.runtime.builtins.JSBuiltinObject;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.JSShape;
import com.oracle.truffle.trufflenode.GraalJSAccess;

/**
 * JS Builtins used by Node.s workers to send Java object references via message passing (@see
 * lib/internal/worker.js).
 */
public final class SharedMemMessagingBindings extends JSBuiltinObject {

    private static final SharedMemMessagingBindings INSTANCE = new SharedMemMessagingBindings();

    private static final JSBuiltinsContainer BUILTINS = new SharedMemMessagingBuiltins();

    private static final String CLASS_NAME = "SharedMemMessaging";

    private static final HiddenKey API = new HiddenKey("api");
    private static final Property API_PROPERTY;

    static {
        Shape.Allocator allocator = JSShape.makeAllocator(JSObject.LAYOUT);
        API_PROPERTY = JSObjectUtil.makeHiddenProperty(API, allocator.locationForType(Object.class));
    }

    public static void setApiField(DynamicObject obj, Object api) {
        API_PROPERTY.setSafe(obj, api, null);
    }

    public static Object getApiField(DynamicObject obj) {
        return API_PROPERTY.get(obj, isInstance(obj, INSTANCE));
    }

    private SharedMemMessagingBindings() {
    }

    @TruffleBoundary
    private static DynamicObject create(JSContext context, GraalJSAccess graalJSAccess) {
        DynamicObject obj = context.createEmptyShape().addProperty(API_PROPERTY).newInstance();
        ((JSBuiltinLookup) context.getFunctionLookup()).defineBuiltins(BUILTINS);
        JSObjectUtil.putFunctionsFromContainer(context.getRealm(), obj, BUILTINS.getName());
        setApiField(obj, graalJSAccess);
        return obj;
    }

    @Override
    public String getClassName(DynamicObject object) {
        return CLASS_NAME;
    }

    @TruffleBoundary
    public static Object createInitFunction(GraalJSAccess graalJSAccess, JSContext context) {
        JSRealm realm = context.getRealm();

        // This JS function will be executed at node.js bootstrap time
        JavaScriptRootNode wrapperNode = new JavaScriptRootNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                return create(context, graalJSAccess);
            }
        };
        JSFunctionData functionData = JSFunctionData.createCallOnly(context, Truffle.getRuntime().createCallTarget(wrapperNode), 2, "SharedMemMessagingInit");
        return JSFunction.create(realm, functionData);
    }

}
