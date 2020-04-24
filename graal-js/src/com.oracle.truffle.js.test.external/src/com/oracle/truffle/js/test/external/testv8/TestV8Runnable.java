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
package com.oracle.truffle.js.test.external.testv8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.oracle.truffle.js.runtime.JSContextOptions;
import org.graalvm.polyglot.Source;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.ExitException;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.test.external.suite.TestCallable;
import com.oracle.truffle.js.test.external.suite.TestExtProcessCallable;
import com.oracle.truffle.js.test.external.suite.TestFile;
import com.oracle.truffle.js.test.external.suite.TestRunnable;
import com.oracle.truffle.js.test.external.suite.TestSuite;

public class TestV8Runnable extends TestRunnable {
    private static final int LONG_RUNNING_TEST_SECONDS = 55;

    private static final String HARMONY_HASHBANG_FLAG = "--harmony-hashbang";
    private static final String HARMONY_IMPORT_META = "--harmony-import-meta";
    private static final String HARMONY_DYNAMIC_IMPORT = "--harmony-dynamic-import";
    private static final String HARMONY_NUMERIC_SEPARATOR = "--harmony-numeric-separator";
    private static final String HARMONY_PROMISE_ALL_SETTLED = "--harmony-promise-all-settled";

    private static final String FLAGS_PREFIX = "// Flags: ";
    private static final Pattern FLAGS_FIND_PATTERN = Pattern.compile("// Flags: (.*)");
    private static final Pattern FLAGS_SPLIT_PATTERN = Pattern.compile("\\s+");

    public TestV8Runnable(TestSuite suite, TestFile testFile) {
        super(suite, testFile);
    }

    @Override
    public void run() {
        suite.printProgress(testFile);
        final File file = suite.resolveTestFilePath(testFile);
        List<String> code = TestSuite.readFileContentList(file);
        boolean negative = isNegativeTest(code);
        boolean shouldThrow = shouldThrow(code);
        boolean module = isModule(code);
        Set<String> flags = getFlags(code);

        Map<String, String> extraOptions = new HashMap<>(2);
        if (flags.contains(HARMONY_HASHBANG_FLAG)) {
            extraOptions.put(JSContextOptions.SHEBANG_NAME, "true");
        }

        suite.logVerbose("Starting: " + getName());

        if (getConfig().isPrintScript()) {
            printScript(TestSuite.toPrintableCode(code));
        }

        // ecma versions
        TestFile.EcmaVersion ecmaVersion = testFile.getEcmaVersion();
        if (ecmaVersion == null) {
            boolean requiresES2020 = flags.contains(HARMONY_IMPORT_META) || flags.contains(HARMONY_DYNAMIC_IMPORT) || flags.contains(HARMONY_NUMERIC_SEPARATOR) ||
                            flags.contains(HARMONY_PROMISE_ALL_SETTLED);
            ecmaVersion = TestFile.EcmaVersion.forVersions(requiresES2020 ? JSTruffleOptions.ECMAScript2020 : JSTruffleOptions.LatestECMAScriptVersion);
        }

        // now run it
        testFile.setResult(runTest(ecmaVersion, version -> runInternal(version, file, negative, shouldThrow, module, extraOptions)));
    }

    private TestFile.Result runInternal(int ecmaVersion, File file, boolean negative, boolean shouldThrow, boolean module, Map<String, String> extraOptions) {
        suite.logVerbose(getName() + ecmaVersionToString(ecmaVersion));
        TestFile.Result testResult;

        long startDate = System.currentTimeMillis();
        reportStart();
        if (suite.getConfig().isExtLauncher()) {
            testResult = runExtLauncher(ecmaVersion, file, negative, shouldThrow, module, extraOptions);
        } else {
            testResult = runInJVM(ecmaVersion, file, negative, shouldThrow, module, extraOptions);
        }
        if (negative) {
            if (!testResult.isFailure()) {
                logFailure(ecmaVersion, "negative test, was expected to fail but didn't");
            } else {
                testResult = TestFile.Result.PASSED;
            }
        }
        // test with --throws must fail
        if (shouldThrow) {
            if (!testResult.isFailure()) {
                logFailure(ecmaVersion, "--throws test, was expected to fail but didn't");
            } else {
                testResult = TestFile.Result.PASSED;
            }
        }
        reportEnd(startDate);
        return testResult;
    }

    private TestFile.Result runInJVM(int ecmaVersion, File file, boolean negative, boolean shouldThrow, boolean module, Map<String, String> extraOptions) {
        Source[] prequelSources = loadHarnessSources(ecmaVersion);
        Source[] sources = Arrays.copyOf(prequelSources, prequelSources.length + 2);
        sources[sources.length - 1] = Source.newBuilder(JavaScriptLanguage.ID, createTestFileNamePrefix(file), "").buildLiteral();
        sources[sources.length - 2] = ((TestV8) suite).getMockupSource();

        TestCallable tc = new TestCallable(suite, sources, toSource(file, module), file, ecmaVersion, extraOptions);
        if (!suite.getConfig().isPrintFullOutput()) {
            tc.setOutput(DUMMY_OUTPUT_STREAM);
        }
        try {
            tc.call();
            return TestFile.Result.PASSED;
        } catch (Throwable e) {
            if (e instanceof ExitException && ((ExitException) e).getStatus() == 0) {
                return TestFile.Result.PASSED;
            } else {
                if (!negative && !shouldThrow) {
                    logFailure(ecmaVersion, e);
                }
                return TestFile.Result.failed(e);
            }
        }
    }

    private TestFile.Result runExtLauncher(int ecmaVersion, File file, boolean negative, boolean shouldThrow, boolean module, Map<String, String> extraOptions) {
        Source[] prequelSources = loadHarnessSources(ecmaVersion);
        List<String> args = new ArrayList<>(prequelSources.length + (module ? 5 : 4));
        for (Source prequelSrc : prequelSources) {
            args.add(prequelSrc.getPath());
        }
        args.add("--eval");
        args.add(createTestFileNamePrefix(file));
        args.add(((TestV8) suite).getMockupSource().getPath());
        if (module) {
            args.add("--module");
        }
        args.add(file.getPath());
        TestExtProcessCallable tc = new TestExtProcessCallable(suite, ecmaVersion, args, extraOptions);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        tc.setOutput(byteArrayOutputStream);
        tc.setError(byteArrayOutputStream);
        try {
            switch (tc.call()) {
                case SUCCESS:
                    return TestFile.Result.PASSED;
                case FAILURE:
                    if (negative || shouldThrow) {
                        return TestFile.Result.failed("TestV8 expected failure");
                    } else {
                        String error = extLauncherFindError(byteArrayOutputStream.toString());
                        logFailure(ecmaVersion, error);
                        return TestFile.Result.failed(error);
                    }
                case TIMEOUT:
                    logTimeout(ecmaVersion);
                    return TestFile.Result.timeout("TIMEOUT");
                default:
                    throw new IllegalStateException("should not reach here");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String createTestFileNamePrefix(File file) {
        return "TEST_FILE_NAME = \"" + file.getPath().replaceAll("\\\\", "\\\\\\\\") + "\"";
    }

    private void reportStart() {
        synchronized (suite) {
            suite.getActiveTests().add(this);
        }
    }

    private void reportEnd(long startDate) {
        long endDate = System.currentTimeMillis();
        long executionTime = endDate - startDate;
        synchronized (suite) {
            if (executionTime > LONG_RUNNING_TEST_SECONDS * 1000) {
                System.out.println("Long running test finished: " + getTestFile().getFilePath() + " " + (endDate - startDate));
            }

            suite.getActiveTests().remove(this);
            StringJoiner activeTestNames = new StringJoiner(", ");
            if (suite.getConfig().isVerbose()) {
                for (TestRunnable test : suite.getActiveTests()) {
                    activeTestNames.add(test.getName());
                }
                suite.logVerbose("Finished test: " + getName() + " after " + executionTime + " ms, active: " + activeTestNames);
            }
        }
    }

    private void printScript(String scriptCode) {
        synchronized (suite) {
            System.out.println("================================================================");
            System.out.println("====== Testcase: " + getName());
            System.out.println("================================================================");
            System.out.println();
            System.out.println(scriptCode);
        }
    }

    private static boolean isNegativeTest(List<String> scriptCode) {
        for (String line : scriptCode) {
            if (line.contains("* @negative")) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldThrow(List<String> scriptCode) {
        for (String line : scriptCode) {
            if (line.contains("--throws")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModule(List<String> scriptCode) {
        for (String line : scriptCode) {
            if (line.startsWith("// MODULE")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> getFlags(List<String> scriptCode) {
        return getStrings(scriptCode, FLAGS_PREFIX, FLAGS_FIND_PATTERN, FLAGS_SPLIT_PATTERN).collect(Collectors.toSet());
    }

    private static final OutputStream DUMMY_OUTPUT_STREAM = new OutputStream() {

        @Override
        public void write(int b) throws IOException {
        }
    };

}
