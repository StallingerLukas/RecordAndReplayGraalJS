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
package com.oracle.truffle.js.nodes.access;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.objects.IteratorRecord;

/**
 * Absorb iterator to new array.
 */
public abstract class IteratorToArrayNode extends JavaScriptNode {
    private final JSContext context;
    @Child @Executed JavaScriptNode iteratorNode;
    @Child private IteratorGetNextValueNode iteratorStepNode;

    protected IteratorToArrayNode(JSContext context, JavaScriptNode iteratorNode, IteratorGetNextValueNode iteratorStepNode) {
        this.context = context;
        this.iteratorNode = iteratorNode;
        this.iteratorStepNode = iteratorStepNode;
    }

    public static IteratorToArrayNode create(JSContext context, JavaScriptNode iterator) {
        IteratorGetNextValueNode iteratorStep = IteratorGetNextValueNode.create(context, null, JSConstantNode.create(null), true);
        return IteratorToArrayNodeGen.create(context, iterator, iteratorStep);
    }

    @Specialization
    protected Object doIterator(VirtualFrame frame, IteratorRecord iteratorRecord) {
        List<Object> elements = new ArrayList<>();
        Object value;
        while ((value = iteratorStepNode.execute(frame, iteratorRecord)) != null) {
            Boundaries.listAdd(elements, value);
        }
        return JSArray.createZeroBasedObjectArray(context, Boundaries.listToArray(elements));
    }

    public abstract Object execute(VirtualFrame frame, IteratorRecord iteratorRecord);

    @Override
    protected JavaScriptNode copyUninitialized() {
        return IteratorToArrayNodeGen.create(context, cloneUninitialized(iteratorNode), cloneUninitialized(iteratorStepNode));
    }
}
