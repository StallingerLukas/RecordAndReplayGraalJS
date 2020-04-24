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

import static com.oracle.truffle.js.runtime.builtins.JSAbstractArray.arrayGetRegexResult;

import java.util.EnumSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.LocationModifier;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSContext.BuiltinFunctionKey;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.joni.JoniRegexEngine;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.JSShape;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.PropertyProxy;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.truffleinterop.JSInteropUtil;
import com.oracle.truffle.js.runtime.util.TRegexUtil;
import com.oracle.truffle.js.runtime.util.TRegexUtil.InteropReadStringMemberNode;
import com.oracle.truffle.js.runtime.util.TRegexUtil.TRegexMaterializeResultNode;

public final class JSRegExp extends JSBuiltinObject implements JSConstructorFactory.Default, PrototypeSupplier {

    public static final JSRegExp INSTANCE = new JSRegExp();

    public static final String CLASS_NAME = "RegExp";
    public static final String PROTOTYPE_NAME = CLASS_NAME + ".prototype";
    public static final String MULTILINE = "multiline";
    public static final String GLOBAL = "global";
    public static final String IGNORE_CASE = "ignoreCase";
    public static final String STICKY = "sticky";
    public static final String UNICODE = "unicode";
    public static final String DOT_ALL = "dotAll";
    public static final String SOURCE = "source";
    public static final String FLAGS = "flags";
    public static final String LAST_INDEX = "lastIndex";
    public static final String INPUT = "input";
    public static final String GROUPS = "groups";
    public static final String INDEX = "index";

    public static final String PROTOTYPE_GETTER_NAME = PROTOTYPE_NAME + " getter";

    private static final HiddenKey COMPILED_REGEX_ID = new HiddenKey("compiledRegex");
    private static final Property COMPILED_REGEX_PROPERTY;
    private static final HiddenKey GROUPS_FACTORY_ID = new HiddenKey("groupsFactory");
    private static final Property GROUPS_FACTORY_PROPERTY;

    private static final Property LAZY_INDEX_PROXY = JSObjectUtil.makeProxyProperty(INDEX, new LazyRegexResultIndexProxyProperty(), JSAttributes.getDefault());

    // A pointer from the `groups` object of a regex result back to the regex result.
    // Needed to calculate the contents of the `groups` object lazily.
    public static final HiddenKey GROUPS_RESULT_ID = new HiddenKey("regexResult");
    public static final HiddenKey GROUPS_ORIGINAL_INPUT_ID = new HiddenKey("regexResultOriginalIndex");
    private static final Property GROUPS_RESULT_PROPERTY;
    private static final Property GROUPS_ORIGINAL_INPUT_PROPERTY;

    /**
     * Since we cannot use nodes here, access to this property is special-cased in
     * {@code com.oracle.truffle.js.nodes.access.PropertyGetNode.LazyRegexResultIndexPropertyGetNode}
     * .
     */
    public static class LazyRegexResultIndexProxyProperty implements PropertyProxy {

        @Override
        public Object get(DynamicObject object) {
            return TRegexUtil.InvokeGetGroupBoundariesMethodNode.getUncached().execute(arrayGetRegexResult(object), TRegexUtil.Props.RegexResult.GET_START, 0);
        }

        @TruffleBoundary
        @Override
        public boolean set(DynamicObject object, Object value) {
            JSObjectUtil.defineDataProperty(object, JSRegExp.INDEX, value, JSAttributes.getDefault());
            return true;
        }
    }

    public static class LazyNamedCaptureGroupProperty implements PropertyProxy {

        private final String groupName;
        private final int groupIndex;

        public LazyNamedCaptureGroupProperty(String groupName, int groupIndex) {
            this.groupName = groupName;
            this.groupIndex = groupIndex;
        }

        public int getGroupIndex() {
            return groupIndex;
        }

        private final TRegexMaterializeResultNode materializeNode = TRegexMaterializeResultNode.getUncached();

        @Override
        public Object get(DynamicObject object) {
            Object regexResult = GROUPS_RESULT_PROPERTY.get(object, false);
            String input = (String) GROUPS_ORIGINAL_INPUT_PROPERTY.get(object, false);
            return materializeNode.materializeGroup(regexResult, groupIndex, input);
        }

        @Override
        public boolean set(DynamicObject object, Object value) {
            JSObjectUtil.defineDataProperty(object, groupName, value, JSAttributes.getDefault());
            return true;
        }
    }

    static {
        Shape.Allocator regExpAllocator = JSShape.makeAllocator(JSObject.LAYOUT);
        regExpAllocator.addLocation(JSObject.PROTO_PROPERTY.getLocation());
        COMPILED_REGEX_PROPERTY = JSObjectUtil.makeHiddenProperty(COMPILED_REGEX_ID, regExpAllocator.locationForType(Object.class, EnumSet.of(LocationModifier.NonNull)));
        GROUPS_FACTORY_PROPERTY = JSObjectUtil.makeHiddenProperty(GROUPS_FACTORY_ID, regExpAllocator.locationForType(JSObjectFactory.class));

        Shape.Allocator resultAllocator = JSShape.makeAllocator(JSObject.LAYOUT);
        GROUPS_RESULT_PROPERTY = JSObjectUtil.makeHiddenProperty(GROUPS_RESULT_ID, resultAllocator.locationForType(Object.class, EnumSet.of(LocationModifier.Final, LocationModifier.NonNull)));
        GROUPS_ORIGINAL_INPUT_PROPERTY = JSObjectUtil.makeHiddenProperty(GROUPS_ORIGINAL_INPUT_ID,
                        resultAllocator.locationForType(String.class, EnumSet.of(LocationModifier.Final, LocationModifier.NonNull)));
    }

    private JSRegExp() {
    }

    public static Object getCompiledRegex(DynamicObject thisObj) {
        assert isJSRegExp(thisObj);
        return COMPILED_REGEX_PROPERTY.get(thisObj, isJSRegExp(thisObj));
    }

    public static Object getCompiledRegexUnchecked(DynamicObject thisObj, boolean guard) {
        assert isJSRegExp(thisObj);
        return COMPILED_REGEX_PROPERTY.get(thisObj, guard);
    }

    public static JSObjectFactory getGroupsFactory(DynamicObject thisObj) {
        assert isJSRegExp(thisObj);
        return (JSObjectFactory) GROUPS_FACTORY_PROPERTY.get(thisObj, isJSRegExp(thisObj));
    }

    public static JSObjectFactory getGroupsFactoryUnchecked(DynamicObject thisObj, boolean guard) {
        assert isJSRegExp(thisObj);
        return (JSObjectFactory) GROUPS_FACTORY_PROPERTY.get(thisObj, guard);
    }

    /**
     * Creates a new JavaScript RegExp object (with a {@code lastIndex} of 0).
     * <p>
     * This overload incurs hitting a {@link TruffleBoundary} when having to examine the
     * {@code compiledRegex} for information about named capture groups. In order to avoid a
     * {@link TruffleBoundary} in cases when your regular expression has no named capture groups,
     * consider using the {@code com.oracle.truffle.js.nodes.intl.CreateRegExpNode}.
     */
    public static DynamicObject create(JSContext ctx, Object compiledRegex) {
        DynamicObject obj = create(ctx, compiledRegex, computeGroupsFactory(ctx, compiledRegex));
        JSObjectUtil.putDataProperty(ctx, obj, LAST_INDEX, 0, JSAttributes.notConfigurableNotEnumerableWritable());
        return obj;
    }

    /**
     * Creates a new JavaScript RegExp object <em>without</em> a {@code lastIndex} property.
     */
    public static DynamicObject create(JSContext ctx, Object compiledRegex, JSObjectFactory groupsFactory) {
        // (compiledRegex, groupsFactory)
        DynamicObject regExp = JSObject.create(ctx, ctx.getRegExpFactory(), compiledRegex, groupsFactory);
        assert isJSRegExp(regExp);
        return regExp;
    }

    private static void initialize(JSContext ctx, DynamicObject regExp, Object regex) {
        COMPILED_REGEX_PROPERTY.setSafe(regExp, regex, null);
        GROUPS_FACTORY_PROPERTY.setSafe(regExp, computeGroupsFactory(ctx, regex), null);
    }

    public static void updateCompilation(JSContext ctx, DynamicObject thisObj, Object regex) {
        assert isJSRegExp(thisObj) && regex != null;
        initialize(ctx, thisObj, regex);
    }

    @TruffleBoundary
    private static JSObjectFactory computeGroupsFactory(JSContext ctx, Object compiledRegex) {
        Object namedCaptureGroups = TRegexUtil.InteropReadMemberNode.getUncached().execute(compiledRegex, TRegexUtil.Props.CompiledRegex.GROUPS);
        if (TRegexUtil.InteropIsNullNode.getUncached().execute(namedCaptureGroups)) {
            return null;
        } else {
            return buildGroupsFactory(ctx, namedCaptureGroups);
        }
    }

    @TruffleBoundary
    public static JSObjectFactory buildGroupsFactory(JSContext ctx, Object namedCaptureGroups) {
        Shape groupsShape = ctx.getEmptyShapeNullPrototype();
        groupsShape = groupsShape.addProperty(GROUPS_RESULT_PROPERTY);
        groupsShape = groupsShape.addProperty(GROUPS_ORIGINAL_INPUT_PROPERTY);
        for (Object key : JSInteropUtil.keys(namedCaptureGroups)) {
            String groupName = (String) key;
            int groupIndex = TRegexUtil.InteropReadIntMemberNode.getUncached().execute(namedCaptureGroups, groupName);
            Property groupProperty = JSObjectUtil.makeProxyProperty(groupName, new LazyNamedCaptureGroupProperty(groupName, groupIndex), JSAttributes.getDefault());
            groupsShape = groupsShape.addProperty(groupProperty);
        }
        return JSObjectFactory.createBound(ctx, Null.instance, groupsShape.createFactory());
    }

    /**
     * Format: '/' pattern '/' flags, flags may contain 'g' (global), 'i' (ignore case) and 'm'
     * (multiline).<br>
     * Example: <code>/ab*c/gi</code>
     */
    @TruffleBoundary
    public static String prototypeToString(DynamicObject thisObj) {
        Object regex = getCompiledRegex(thisObj);
        InteropReadStringMemberNode readString = TRegexUtil.InteropReadStringMemberNode.getUncached();
        String pattern = readString.execute(regex, TRegexUtil.Props.CompiledRegex.PATTERN);
        if (pattern.length() == 0) {
            pattern = "(?:)";
        }
        String flags = readString.execute(TRegexUtil.InteropReadMemberNode.getUncached().execute(regex, TRegexUtil.Props.CompiledRegex.FLAGS), TRegexUtil.Props.Flags.SOURCE);
        return "/" + pattern + '/' + flags;
    }

    // non-standard according to ES2015, 7.2.8 IsRegExp (@@match check missing)
    public static boolean isJSRegExp(Object obj) {
        return JSObject.isDynamicObject(obj) && isJSRegExp((DynamicObject) obj);
    }

    // non-standard according to ES2015, 7.2.8 IsRegExp (@@match check missing)
    public static boolean isJSRegExp(DynamicObject obj) {
        return isInstance(obj, INSTANCE);
    }

    @Override
    public DynamicObject createPrototype(JSRealm realm, DynamicObject ctor) {
        JSContext ctx = realm.getContext();
        DynamicObject prototype = JSObject.createInit(realm, realm.getObjectPrototype(), ctx.getEcmaScriptVersion() < 6 ? JSRegExp.INSTANCE : JSUserObject.INSTANCE);
        if (ctx.getEcmaScriptVersion() < 6) {
            JSObjectUtil.putHiddenProperty(prototype, COMPILED_REGEX_PROPERTY, compileEarly("", ""));
            JSObjectUtil.putDataProperty(ctx, prototype, LAST_INDEX, 0, JSAttributes.notConfigurableNotEnumerableWritable());
        }
        putRegExpPropertyAccessor(realm, prototype, SOURCE);
        putRegExpPropertyAccessor(realm, prototype, FLAGS);
        putRegExpPropertyAccessor(realm, prototype, MULTILINE);
        putRegExpPropertyAccessor(realm, prototype, GLOBAL);
        putRegExpPropertyAccessor(realm, prototype, IGNORE_CASE);
        if (ctx.getEcmaScriptVersion() >= 6) {
            putRegExpPropertyAccessor(realm, prototype, STICKY);
            putRegExpPropertyAccessor(realm, prototype, UNICODE);
        }
        if (ctx.getEcmaScriptVersion() >= 9) {
            putRegExpPropertyAccessor(realm, prototype, DOT_ALL);
        }
        // ctor and functions
        JSObjectUtil.putConstructorProperty(ctx, prototype, ctor);
        JSObjectUtil.putFunctionsFromContainer(realm, prototype, PROTOTYPE_NAME);
        return prototype;
    }

    private static void putRegExpPropertyAccessor(JSRealm realm, DynamicObject prototype, String name) {
        DynamicObject getter = realm.lookupFunction(PROTOTYPE_GETTER_NAME, name);
        JSObjectUtil.putConstantAccessorProperty(realm.getContext(), prototype, name, getter, Undefined.instance);
    }

    private static Object compileEarly(String pattern, String flags) {
        // avoid getRealm() in context.getRegexEngine()
        Object tempEngine = new JoniRegexEngine(null);
        return TRegexUtil.CompileRegexNode.getUncached().execute(tempEngine, pattern, flags);
    }

    @Override
    public Shape makeInitialShape(JSContext ctx, DynamicObject thisObj) {
        // @formatter:off
        return JSObjectUtil.getProtoChildShape(thisObj, INSTANCE, ctx).
                        addProperty(COMPILED_REGEX_PROPERTY).
                        addProperty(GROUPS_FACTORY_PROPERTY);
        // @formatter:on
    }

    public static Shape makeLazyRegexArrayShape(JSContext ctx, DynamicObject prototype) {
        Shape initialShape = JSObjectUtil.getProtoChildShape(prototype, JSArray.INSTANCE, ctx);
        initialShape = JSArray.addArrayProperties(initialShape);
        initialShape = initialShape.addProperty(JSAbstractArray.LAZY_REGEX_RESULT_PROPERTY);
        initialShape = initialShape.addProperty(JSAbstractArray.LAZY_REGEX_ORIGINAL_INPUT_PROPERTY);
        final Property inputProperty = JSObjectUtil.makeDataProperty(JSRegExp.INPUT, initialShape.allocator().locationForType(String.class, EnumSet.of(LocationModifier.NonNull)),
                        JSAttributes.getDefault());
        initialShape = initialShape.addProperty(inputProperty);
        initialShape = initialShape.addProperty(JSArray.ARRAY_LENGTH_PROXY_PROPERTY);
        initialShape = initialShape.addProperty(LAZY_INDEX_PROXY);
        initialShape = initialShape.addProperty(JSObjectUtil.makeDataProperty(GROUPS, initialShape.allocator().locationForType(DynamicObject.class,
                        EnumSet.of(LocationModifier.Final, LocationModifier.NonNull)), JSAttributes.getDefault()));
        return initialShape;
    }

    @Override
    public void fillConstructor(JSRealm realm, DynamicObject constructor) {
        final JSContext context = realm.getContext();
        putConstructorSpeciesGetter(realm, constructor);
        if (context.isOptionRegexpStaticResult()) {
            if (JSTruffleOptions.NashornCompatibilityMode) {
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpInput, "input");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpMultiLine, "multiline");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpLastMatch, "lastMatch");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpLastParen, "lastParen");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpLeftContext, "leftContext");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpRightContext, "rightContext");
            } else {
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpInput, "input");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpLastMatch, "lastMatch");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpLastParen, "lastParen");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpLeftContext, "leftContext");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExpRightContext, "rightContext");

                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$_, "input", "$_");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$And, "lastMatch", "$&");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$Plus, "lastParen", "$+");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$Apostrophe, "leftContext", "$`");
                putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$Quote, "rightContext", "$'");
            }
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$1, "$1");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$2, "$2");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$3, "$3");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$4, "$4");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$5, "$5");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$6, "$6");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$7, "$7");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$8, "$8");
            putRegExpStaticPropertyAccessor(realm, constructor, BuiltinFunctionKey.RegExp$9, "$9");
        }
    }

    private static void putRegExpStaticPropertyAccessor(JSRealm realm, DynamicObject constructor, BuiltinFunctionKey builtinKey, String getterName) {
        putRegExpStaticPropertyAccessor(realm, constructor, builtinKey, getterName, getterName);
    }

    private static void putRegExpStaticPropertyAccessor(JSRealm realm, DynamicObject constructor, BuiltinFunctionKey builtinKey, String getterName, String propertyName) {
        JSContext ctx = realm.getContext();
        DynamicObject getter = realm.lookupFunction(CLASS_NAME, getterName);

        // set empty setter for V8 compatibility, see testv8/mjsunit/regress/regress-5566.js
        String setterName = "set " + propertyName;
        JSFunctionData setterData = ctx.getOrCreateBuiltinFunctionData(builtinKey,
                        (c) -> JSFunctionData.createCallOnly(c, ctx.getEmptyFunctionCallTarget(), 0, setterName));
        DynamicObject setter = JSFunction.create(realm, setterData);
        JSObjectUtil.putConstantAccessorProperty(ctx, constructor, propertyName, getter, setter, getRegExpStaticResultPropertyAccessorJSAttributes());
    }

    // https://github.com/tc39/proposal-regexp-legacy-features#additional-properties-of-the-regexp-constructor
    private static int getRegExpStaticResultPropertyAccessorJSAttributes() {
        return JSTruffleOptions.NashornCompatibilityMode ? JSAttributes.notConfigurableEnumerableWritable() : JSAttributes.configurableNotEnumerableWritable();
    }

    public static JSConstructor createConstructor(JSRealm realm) {
        return INSTANCE.createConstructorAndPrototype(realm);
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getClassName(DynamicObject object) {
        return getClassName();
    }

    @Override
    public String getBuiltinToStringTag(DynamicObject object) {
        return getClassName(object);
    }

    @Override
    @TruffleBoundary
    public String safeToString(DynamicObject obj, int depth) {
        if (JSTruffleOptions.NashornCompatibilityMode) {
            return "[RegExp " + prototypeToString(obj) + "]";
        } else {
            return prototypeToString(obj);
        }
    }

    @Override
    public DynamicObject getIntrinsicDefaultProto(JSRealm realm) {
        return realm.getRegExpPrototype();
    }

    @TruffleBoundary
    public static CharSequence escapeRegExpPattern(CharSequence pattern) {
        if (pattern.length() == 0) {
            return "(?:)";
        }
        int extraChars = escapeRegExpExtraCharCount(pattern);
        if (extraChars == 0) {
            return pattern;
        } else {
            return escapeRegExpPattern(pattern, extraChars);
        }
    }

    /**
     * Returns the number of extra characters that need to be inserted into {@code pattern} in order
     * for it to be correctly escaped for use in a RegExp literal (according to the requirements of
     * EscapeRegExpPattern).
     *
     * This method satisfies the following property: if its return value is 0, the pattern does not
     * need to be modified by EscapeRegExpPattern. In order to satisfy this property, this method
     * can sometimes return a result that is 1 higher than the advertised value. This is the case
     * when the pattern needs escaping but none of the escapes actually prolong the pattern, as in
     * {@code "\\\n"}, which is escaped as {@code "\\n"} and where both the original and the escaped
     * pattern are of length 2.
     */
    private static int escapeRegExpExtraCharCount(CharSequence pattern) {
        // The body of this method mirrors that of escapeRegExpPattern. However, instead of actually
        // allocating and filling a new StringBuilder, it only scans the input pattern and takes
        // note of any characters that will need to be escaped.
        int extraChars = 0;
        boolean insideCharClass = false;
        int i = 0;
        while (i < pattern.length()) {
            switch (pattern.charAt(i)) {
                case '\\':
                    assert i + 1 < pattern.length();
                    i++;
                    switch (pattern.charAt(i)) {
                        case '\n':
                        case '\r':
                            // We are replacing "\\\n" with "\\n" or "\\\r" with "\\r". We are not
                            // adding any extra characters but we are still modifying the pattern.
                            // Therefore, we make sure that resulting value extraChars is at least
                            // 1.
                            extraChars = Math.max(extraChars, 1);
                            break;
                        case '\u2028':
                        case '\u2029':
                            extraChars += 4;
                            break;
                    }
                    break;
                case '\n':
                case '\r':
                    extraChars += 1;
                    break;
                case '\u2028':
                case '\u2029':
                    extraChars += 5;
                    break;
                case '/':
                    if (!insideCharClass) {
                        extraChars += 1;
                    }
                    break;
                case '[':
                    insideCharClass = true;
                    break;
                case ']':
                    insideCharClass = false;
                    break;
            }
            i++;
        }
        return extraChars;
    }

    /**
     * Implements the EscapeRegExpPattern abstract operation from the ECMAScript spec.
     *
     * @param pattern the input pattern, which is assumed to be non-empty
     * @param extraChars an estimate on the difference of sizes between the original pattern and the
     *            escaped pattern
     * @return the escaped pattern
     */
    @TruffleBoundary
    private static String escapeRegExpPattern(CharSequence pattern, int extraChars) {
        StringBuilder sb = new StringBuilder(pattern.length() + extraChars);
        boolean insideCharClass = false;
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            switch (c) {
                case '\\':
                    assert i + 1 < pattern.length();
                    sb.append(c);
                    i++;
                    c = pattern.charAt(i);
                    // The patterns used in RegExp objects can not only have literal LineTerminators
                    // (e.g. RegExp("\n")), they can also have identity escapes of literal
                    // LineTerminators (e.g. RegExp("\\\n")) (note that this is only valid when the
                    // Unicode flag is not present). Since LineTerminators are not allowed in RegExp
                    // literals, we have to replace these identity escapes with other escapes.
                    switch (c) {
                        case '\n':
                            sb.append('n');
                            break;
                        case '\r':
                            sb.append('r');
                            break;
                        case '\u2028':
                            sb.append("u2028");
                            break;
                        case '\u2029':
                            sb.append("u2029");
                            break;
                        default:
                            sb.append(c);
                    }
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\u2028':
                    sb.append("\\u2028");
                    break;
                case '\u2029':
                    sb.append("\\u2029");
                    break;
                case '/':
                    // According to the syntax of RegularExpressionLiterals, forward slashes are
                    // allowed inside character classes and therefore do not have to be escaped.
                    if (!insideCharClass) {
                        sb.append("\\/");
                    } else {
                        sb.append('/');
                    }
                    break;
                case '[':
                    insideCharClass = true;
                    sb.append(c);
                    break;
                case ']':
                    insideCharClass = false;
                    sb.append(c);
                    break;
                default:
                    sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }
}
