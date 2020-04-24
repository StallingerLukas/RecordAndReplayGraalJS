# Migration guide from Nashorn to GraalVM JavaScript

This document serves as migration guide for code previously targeted to the Nashorn engine.
See the [JavaInterop.md](JavaInterop.md) for an overview of supported Java interoperability features.

Both Nashorn and GraalVM JavaScript support a similar set of syntax and semantics for Java interoperability.
The most important differences relevant for migration are listed here.

Nashorn features available by default:
* `Java.type`, `Java.typeName`
* `Java.from`, `Java.to`
* `Java.extend`, `Java.super`
* Java package globals: `Packages`, `java`, `javafx`, `javax`, `com`, `org`, `edu`

## Nashorn compatibility mode
GraalVM JavaScript provides a Nashorn compatibility mode.
Some of the functionality necessary for Nashorn compatibility is only available when the `js.nashorn-compat` option is enabled.
This is the case for Nashorn-specific extensions that GraalVM JavaScript does not want to expose by default.
Note that you have to enable [experimental options](Options.md#Stable and Experimental options) to use this flag.

The `js.nashorn-compat` option can be set using a command line option:
```
$ js --experimental-options --js.nashorn-compat=true
```

Or using the polyglot API:
```java
import org.graalvm.polyglot.Context;

try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("js.nashorn-compat", "true").build()) {
    context.eval("js", "print(__LINE__)");
}
```

Or using a system property when starting a Java application (remember to enable `allowExperimentalOptions` on the `Context.Builder` in your application as well):
```
$ java -Dpolyglot.js.nashorn-compat=true MyApplication
```

Functionality only available under this flag includes:
* `Java.isJavaFunction`, `Java.isJavaMethod`, `Java.isScriptObject`, `Java.isScriptFunction`
* `new Interface|AbstractClass(fn|obj)`
* `JavaImporter`
* `JSAdapter`
* `java.lang.String` methods on string values
* `load("nashorn:parser.js")`, `load("nashorn:mozilla_compat.js")`
* `exit`, `quit`

## Nashorn syntax extensions

[Nashorn syntax extensions](https://wiki.openjdk.java.net/display/Nashorn/Nashorn+extensions) can be enabled using the `js.syntax-extensions` experimental option.
They're also enabled by default in Nashorn compatibility mode (`js.nashorn-compat`).

## Intentional design differences
GraalVM JavaScript differs from Nashorn in some aspects that were intentional design decisions.

### Launcher name `js`
When shipped with GraalVM, GraalVM JavaScript comes with a binary launcher named `js`.
Note that, depending on the build setup, GraalVM might still ship Nashorn and its `jjs` launcher.

### ScriptEngine name `graal.js`
GraalVM JavaScript is shipped with ScriptEngine support.
It registers under several names, including "graal.js", "JavaScript", "js".
Be sure to activate the Nashorn compatibility mode as described above if you need full Nashorn compatibility.
Depending on the build setup, GraalVM might still ship Nashorn and provide it via ScriptEngine.

For more details, see [ScriptEngine.md](ScriptEngine.md).

### `ClassFilter`
GraalVM JavaScript supports a class filter when starting with a polyglot `Context`.
See the [JavaDoc of `Context.Builder.hostClassFilter`](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#hostClassFilter-java.util.function.Predicate-)

### Fully qualified names
GraalVM Javascript requires the use of `Java.type(typename)`.
It does not support accessing classes just by their fully qualified class name by default.
`Java.type` brings more clarity and avoids the accidental use of Java classes in JavaScript code.
Patterns like this:

```js
var bd = new java.math.BigDecimal('10');
```

should be expressed as:

```js
var BigDecimal = Java.type('java.math.BigDecimal');
var bd = new BigDecimal('10');
```

### Lossy conversion
GraalVM JavaScript does not allow lossy conversions of arguments when calling Java methods.
This could lead to bugs with numeric values that are hard to detect.

GraalVM JavaScript will always select the overloaded method with the narrowest possible argument types that can be converted to without loss.
If no such overloaded method is available, GraalVM JavaScript throws a `TypeError` instead of lossy conversion.
In general, this affects which overloaded method is executed.

### `ScriptObjectMirror` objects
GraalVM JavaScript does not provide objects of the class `ScriptObjectMirror`.
Instead, JavaScript objects are exposed to Java code as objects implementing Java's `Map` interface.

Code referencing `ScriptObjectMirror` instances can be rewritten by changing the type to either an interface (`Map`, `List`) or the polyglot [Value](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html) class which provides similar capabilities.

### Multithreading
GraalVM JavaScript supports multithreading by creating several `Context` objects from Java code.
Contexts can be shared between threads, but each context must be accessed by a single thread at a time.
Multiple JavaScript engines can be created from a Java application, and can be safely executed in parallel on multiple threads.

```js
Context polyglot = Context.create();
Value array = polyglot.eval("js", "[1,2,42,4]");

```

GraalVM JavaScript does not allow the creation of threads from JavaScript applications with access to the current `Context`.
Moreover, GraalVM JavaScript does not allow concurrent threads to access the same `Context` at the same time.
This could lead to unmanagable synchronization problems like data races in a language that is not prepared for multithreading.

```js
new Thread(function() {
    print('printed from another thread'); // throws Exception due to potential synchronization problems
}).start();
```

JavaScript code can create and start threads with `Runnable`s implemented in Java.
The child thread may not access the `Context` of the parent thread or of any other polyglot thread.
In case of violations, an `IllegalStateException` will be thrown.
A child thread may create a new `Context` instance, though.

```js
new Thread(aJavaRunnable).start(); // allowed on GraalVM JavaScript
```

With proper synchronization in place, multiple contexts can be shared between different threads. Example Java applications using GraalVM JavaScript `Context`s from multiple threads can be found [here](https://github.com/graalvm/graaljs/tree/master/graal-js/src/com.oracle.truffle.js.test.threading/src/com/oracle/truffle/js/test/threading).

## Extensions only available in Nashorn compatibility mode
The following extensions to JavaScript available in Nashorn are deactivated in GraalVM JavaScript by default.
They are provided in GraalVM's Nashorn compatibility mode.
It is highly recommended not to implement new applications based on those features, but only to use it as a means to migrate existing applications to GraalVM.

### String `length` property
GraalVM JavaScript does not treat the length property of a String specially.
The canonical way of accessing the String length is reading the `length` property.

```
myJavaString.length;
```

Nashorn allows to both access `length` as a property and a function.
Existing function calls `length()` should be expressed as property access.
Nashorn behavior is mimicked in the Nashorn compatibility mode.

### Java packages in the JavaScript global object
GraalVM JavaScript requires the use of `Java.type` instead of fully qualified names.
In Nashorn compatibility mode, the following Java package are added to the JavaScript global object: `java`, `javafx`, `javax`, `com`, `org`, `edu`.

### JavaImporter
The `JavaImporter` feature is available only in Nashorn compatibility mode.

### JSAdapter
Use of the non-standard `JSAdapter` is discouraged and should be replaced with the equivalent standard `Proxy` feature.
For compatibility, `JSAdapter` is still available in Nashorn compatibility mode.

### Java.* methods
Several methods provided by Nashorn on the `Java` global object are available only in Nashorn compatibility mode or currently not supported by GraalVM JavaScript.
Available in Nashorn compatibility mode are: `Java.isJavaFunction`, `Java.isJavaMethod`, `Java.isScriptObject`, `Java.isScriptFunction`.
Currently not supported: `Java.asJSONCompatible`.

### Accessors
In Nashorn compatibility mode, GraalVM JavaScript allows to access getters and setters just by the name as properties, while omitting `get`, `set`, or `is`.

```js
var Date = Java.type('java.util.Date');
var date = new Date();

var myYear = date.year; // calls date.getYear()
date.year = myYear + 1; // calls date.setYear(myYear + 1);
```

GraalVM JavaScript defines an ordering in which it searches for the field or getters.
It will always first try to read or write the field with the name as provided in the property.
If the field cannot be read or written, it will try to call a getter or setter:
* In case of a read operation, GraalVM JavaScript will first try to call a getter with the name `get` and the property name in camel case. If that is not available, a getter with the name `is` and the property name in camel case is called. In the second case, the value is returned even if it is not of type boolean.
* In case of a write operation, GraalVM JavaScript will try to call a setter with the name `set` and the property name in camel case, providing the value as argument to that function.

Nashorn can expose random behavior when both `getFieldName` and `isFieldName` are available.
Nashorn also gives precedence to getters, even when a public field of the exact name is available.

## Additional aspects to consider

### Features of GraalVM JavaScript
GraalVM JavaScript supports features of the newest ECMAScript specification and some extensions to that, see [JavaScriptCompatibility.md](JavaScriptCompatibility.md).
Note that this e.g. adds objects to the global scope that might interfere with existing source code unaware of those extensions.

### Console output
GraalVM JavaScript provides a `print` builtin function compatible with Nashorn.

Note that GraalVM JavaScript also provides a `console.log` function.
This is an alias for `print` in pure JavaScript mode, but uses an implementation provided by Node.js when running in Node mode.
Behavior around Java objects differs for `console.log` in Node mode as Node.js does not implement special treatment for such objects.

