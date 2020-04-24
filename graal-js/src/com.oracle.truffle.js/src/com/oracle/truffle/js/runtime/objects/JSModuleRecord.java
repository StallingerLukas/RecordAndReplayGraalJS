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
package com.oracle.truffle.js.runtime.objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSUserObject;

/**
 * Source Text Module Record.
 */
public final class JSModuleRecord extends ScriptOrModule {
    public enum Status {
        Uninstantiated,
        Instantiating,
        Instantiated,
        Evaluating,
        Evaluated,
    }

    /** Module parse node. */
    private final Object module;
    private final JSModuleLoader moduleLoader;

    /** Module's instantiation/evaluation status. */
    private Status status;

    /** Exception that occurred during evaluation. */
    private Throwable evaluationError;
    /** Implementation-specific: The result of ModuleExecution if no exception occurred. */
    private Object executionResult;

    private JSFunctionData functionData;
    private FrameDescriptor frameDescriptor;

    /** Lazily initialized Module Namespace object ({@code [[Namespace]]}). */
    private DynamicObject namespace;
    /** Lazily initialized frame ({@code [[Environment]]}). */
    private MaterializedFrame environment;
    /** Lazily initialized import.meta object ({@code [[ImportMeta]]}). */
    private DynamicObject importMeta;

    private Runnable finishTranslation;

    /**
     * Auxiliary field used during Instantiate and Evaluate only. If [[Status]] is "instantiating"
     * or "evaluating", this nonnegative number records the point at which the module was first
     * visited during the ongoing depth-first traversal of the dependency graph.
     */
    private int dfsIndex;
    /**
     * Auxiliary field used during Instantiate and Evaluate only. If [[Status]] is "instantiating"
     * or "evaluating", this is either the module's own [[DFSIndex]] or that of an "earlier" module
     * in the same strongly connected component.
     */
    private int dfsAncestorIndex;

    public JSModuleRecord(Object module, JSContext context, JSModuleLoader moduleLoader, Source source, Runnable finishTranslation) {
        super(context, source);
        this.module = module;
        this.moduleLoader = moduleLoader;
        this.finishTranslation = finishTranslation;
        setUninstantiated();
    }

    public Object getModule() {
        return module;
    }

    public JSModuleLoader getModuleLoader() {
        return moduleLoader;
    }

    public JSFunctionData getFunctionData() {
        assert functionData != null;
        return functionData;
    }

    public void setFunctionData(JSFunctionData functionData) {
        assert this.functionData == null;
        this.functionData = functionData;
    }

    public FrameDescriptor getFrameDescriptor() {
        assert frameDescriptor != null;
        return frameDescriptor;
    }

    public void setFrameDescriptor(FrameDescriptor frameDescriptor) {
        assert this.frameDescriptor == null;
        this.frameDescriptor = frameDescriptor;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isEvaluated() {
        return getStatus() == Status.Evaluated;
    }

    public Throwable getEvaluationError() {
        assert isEvaluated();
        return evaluationError;
    }

    public void setEvaluationError(Throwable evaluationError) {
        assert isEvaluated();
        this.evaluationError = evaluationError;
    }

    public DynamicObject getNamespace() {
        return namespace;
    }

    public void setNamespace(DynamicObject namespace) {
        assert this.namespace == null;
        this.namespace = namespace;
    }

    public MaterializedFrame getEnvironment() {
        return environment;
    }

    public void setEnvironment(MaterializedFrame environment) {
        assert this.environment == null;
        assert this.frameDescriptor == environment.getFrameDescriptor();
        this.environment = environment;
    }

    public void finishTranslation() {
        assert getStatus() == Status.Instantiating;
        finishTranslation.run();
        finishTranslation = null;
    }

    public int getDFSIndex() {
        assert dfsIndex >= 0;
        return dfsIndex;
    }

    public void setDFSIndex(int dfsIndex) {
        this.dfsIndex = dfsIndex;
    }

    public int getDFSAncestorIndex() {
        assert dfsAncestorIndex >= 0;
        return dfsAncestorIndex;
    }

    public void setDFSAncestorIndex(int dfsAncestorIndex) {
        this.dfsAncestorIndex = dfsAncestorIndex;
    }

    public Object getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(Object executionResult) {
        this.executionResult = executionResult;
    }

    public DynamicObject getImportMeta() {
        if (importMeta == null) {
            importMeta = createMetaObject();
        }
        return importMeta;
    }

    private DynamicObject createMetaObject() {
        DynamicObject metaObj = JSUserObject.createWithNullPrototype(context);
        if (context.hasImportMetaInitializerBeenSet()) {
            context.notifyImportMetaInitializer(metaObj, this);
        } else {
            initializeMetaObject(metaObj);
        }
        return metaObj;
    }

    @TruffleBoundary
    private void initializeMetaObject(DynamicObject metaObj) {
        JSObject.set(metaObj, "url", getSource().getURI().toString());
    }

    public void setUninstantiated() {
        setStatus(Status.Uninstantiated);
        this.environment = null;
        this.dfsIndex = -1;
        this.dfsAncestorIndex = -1;
    }
}
