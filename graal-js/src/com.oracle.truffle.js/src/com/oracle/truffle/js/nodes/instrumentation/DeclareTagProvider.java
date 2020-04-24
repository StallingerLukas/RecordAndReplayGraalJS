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
package com.oracle.truffle.js.nodes.instrumentation;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.function.BlockScopeNode.FrameBlockScopeNode;
import com.oracle.truffle.js.nodes.function.FunctionBodyNode;
import com.oracle.truffle.js.runtime.JSFrameUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;

public final class DeclareTagProvider {

    public static JavaScriptNode createMaterializedFunctionBodyNode(JavaScriptNode body, SourceSection sourceSection, FrameDescriptor frameDescriptor) {
        return new MaterializedFunctionBodyNode(body, sourceSection, frameDescriptor);
    }

    public static JavaScriptNode createMaterializedBlockNode(JavaScriptNode block, FrameDescriptor frameDescriptor, FrameSlot parentSlot, SourceSection sourceSection) {
        return new MaterializedFrameBlockScopeNode(block, frameDescriptor, parentSlot, sourceSection);
    }

    public static boolean isMaterializedFrameProvider(JavaScriptNode node) {
        return node instanceof MaterializedFrameBlockScopeNode || node instanceof MaterializedFunctionBodyNode;
    }

    private DeclareTagProvider() {
    }

    private static JavaScriptNode[] initDeclarations(FrameDescriptor frameDescriptor, SourceSection sourceSection) {
        assert sourceSection != null;
        if (frameDescriptor != null) {
            List<FrameSlot> slots = new ArrayList<>();
            for (FrameSlot slot : frameDescriptor.getSlots()) {
                if (!JSFrameUtil.isInternal(slot)) {
                    slots.add(slot);
                }
            }
            JavaScriptNode[] declarations = new JavaScriptNode[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                declarations[i] = new DeclareProviderNode(slots.get(i));
                declarations[i].setSourceSection(sourceSection);
            }
            return declarations;
        } else {
            return new JavaScriptNode[0];
        }
    }

    private static class MaterializedFrameBlockScopeNode extends FrameBlockScopeNode {

        @Children private JavaScriptNode[] declarations;

        protected MaterializedFrameBlockScopeNode(JavaScriptNode block, FrameDescriptor frameDescriptor, FrameSlot parentSlot, SourceSection sourceSection) {
            super(block, frameDescriptor, parentSlot);
            this.declarations = initDeclarations(frameDescriptor, sourceSection);
        }

        @ExplodeLoop
        private void executeDeclarations(VirtualFrame frame) {
            for (JavaScriptNode declaration : declarations) {
                declaration.execute(frame);
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            executeDeclarations(frame);
            return super.execute(frame);
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            executeDeclarations(frame);
            super.executeVoid(frame);
        }

        @Override
        protected JavaScriptNode copyUninitialized() {
            return new MaterializedFrameBlockScopeNode(cloneUninitialized(block), frameDescriptor, parentSlot, getSourceSection());
        }
    }

    private static class MaterializedFunctionBodyNode extends FunctionBodyNode {

        @Children private JavaScriptNode[] declarations;

        private final FrameDescriptor frameDescriptor;

        protected MaterializedFunctionBodyNode(JavaScriptNode body, SourceSection sourceSection, FrameDescriptor frameDescriptor) {
            super(body);
            this.frameDescriptor = frameDescriptor;
            this.declarations = initDeclarations(frameDescriptor, sourceSection);
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            for (JavaScriptNode declaration : declarations) {
                declaration.execute(frame);
            }
            return super.execute(frame);
        }

        @Override
        protected JavaScriptNode copyUninitialized() {
            return new MaterializedFunctionBodyNode(cloneUninitialized(getBody()), getSourceSection(), frameDescriptor);
        }
    }

    private static class DeclareProviderNode extends JavaScriptNode {

        private final FrameSlot slot;

        DeclareProviderNode(FrameSlot slot) {
            this.slot = slot;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // Ignored
            return Undefined.instance;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            if (tag == JSTags.DeclareTag.class) {
                return true;
            } else {
                return super.hasTag(tag);
            }
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public Object getNodeObject() {
            NodeObjectDescriptor descriptor = JSTags.createNodeObjectDescriptor();
            descriptor.addProperty("name", slot.getIdentifier());
            if (JSFrameUtil.isConst(slot)) {
                descriptor.addProperty("type", "const");
            } else if (JSFrameUtil.isLet(slot)) {
                descriptor.addProperty("type", "let");
            } else {
                descriptor.addProperty("type", "var");
            }
            return descriptor;
        }

        @Override
        protected JavaScriptNode copyUninitialized() {
            return new DeclareProviderNode(slot);
        }
    }
}
