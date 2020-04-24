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
package com.oracle.truffle.js.nodes.intl;

import java.util.MissingResourceException;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.JSCollator;
import com.oracle.truffle.js.runtime.util.IntlUtil;

/*
 * https://tc39.github.io/ecma402/#sec-initializecollator
 */
public abstract class InitializeCollatorNode extends JavaScriptBaseNode {

    private final JSContext context;

    @Child JSToCanonicalizedLocaleListNode toCanonicalizedLocaleListNode;
    @Child CreateOptionsObjectNode createOptionsNode;

    @Child GetStringOptionNode getUsageOption;
    @Child GetStringOptionNode getLocaleMatcherOption;
    @Child GetBooleanOptionNode getNumericOption;
    @Child GetStringOptionNode getCaseFirstOption;
    @Child GetStringOptionNode getSensitivityOption;
    @Child GetBooleanOptionNode getIgnorePunctuationOption;

    protected InitializeCollatorNode(JSContext context) {
        this.context = context;
        this.toCanonicalizedLocaleListNode = JSToCanonicalizedLocaleListNode.create(context);
        this.createOptionsNode = CreateOptionsObjectNodeGen.create(context);
        this.getUsageOption = GetStringOptionNode.create(context, IntlUtil.USAGE, new String[]{IntlUtil.SORT, IntlUtil.SEARCH}, IntlUtil.SORT);
        this.getLocaleMatcherOption = GetStringOptionNode.create(context, IntlUtil.LOCALE_MATCHER, new String[]{IntlUtil.LOOKUP, IntlUtil.BEST_FIT}, IntlUtil.BEST_FIT);
        this.getNumericOption = GetBooleanOptionNode.create(context, IntlUtil.NUMERIC, null);
        this.getCaseFirstOption = GetStringOptionNode.create(context, IntlUtil.CASE_FIRST, new String[]{IntlUtil.UPPER, IntlUtil.LOWER, IntlUtil.FALSE}, null);
        this.getSensitivityOption = GetStringOptionNode.create(context, IntlUtil.SENSITIVITY, new String[]{IntlUtil.BASE, IntlUtil.ACCENT, IntlUtil.CASE, IntlUtil.VARIANT}, null);
        this.getIgnorePunctuationOption = GetBooleanOptionNode.create(context, IntlUtil.IGNORE_PUNCTUATION, false);
    }

    public abstract DynamicObject executeInit(DynamicObject collator, Object locales, Object options);

    public static InitializeCollatorNode createInitalizeCollatorNode(JSContext context) {
        return InitializeCollatorNodeGen.create(context);
    }

    @Specialization
    public DynamicObject initializeCollator(DynamicObject collatorObj, Object localesArg, Object optionsArg) {

        // must be invoked before any code that tries to access ICU library data
        try {
            JSCollator.InternalState state = JSCollator.getInternalState(collatorObj);
            String[] locales = toCanonicalizedLocaleListNode.executeLanguageTags(localesArg);
            DynamicObject options = createOptionsNode.execute(optionsArg);
            String usage = getUsageOption.executeValue(options);
            String optLocaleMatcher = getLocaleMatcherOption.executeValue(options);
            Boolean optkn = getNumericOption.executeValue(options);
            String optkf = getCaseFirstOption.executeValue(options);
            String sensitivity = getSensitivityOption.executeValue(options);
            Boolean ignorePunctuation = getIgnorePunctuationOption.executeValue(options);

            JSCollator.initializeCollator(context, state, locales, usage, optLocaleMatcher, optkn, optkf, sensitivity, ignorePunctuation);

        } catch (MissingResourceException e) {
            throw Errors.createICU4JDataError(e);
        }

        return collatorObj;
    }
}
