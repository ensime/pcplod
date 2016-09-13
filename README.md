**PC Plod is a testing library for macro and compiler plugin authors** to make assertions of their libraries in the [Presentation Compiler](http://scala-ide.org/docs/dev/architecture/presentation-compiler.html#scalapresentationcompiler), i.e. [ENSIME](http://ensime.org) and [Scala IDE](http://scala-ide.org/).

## Installation

Published as a regular artefact, the sbt installation is easy:

```scala
libraryDependencies += "org.ensime" %% "pcplod" % "1.0.0" % "test"
```

The `dependencyOverrides` feature of SBT is recommended to ensure that the correct version of the scala compiler is used

```scala
dependencyOverrides += "org.scala-lang" % "scala-compiler" % scalaVersion.value
```

If you are testing a compiler plugin you may already be aware that you must add something like the following to your test configuration

```scala
scalacOptions in Test ++= {
  val jar = (packageBin in Compile).value
  Seq(s"-Xplugin:${jar.getAbsolutePath}", s"-Jdummy=${jar.lastModified}") // ensures recompile
}
```

But to ensure that the plugin is used by pcplod, you must also add

```scala
javaOptions in Test += s"-Dpcplod.plugin=${(packageBin in Compile).value.getAbsolutePath}"
```

## How to use it

PC Plod uses the *loan pattern* to let you write tests in whatever testing framework you want.

### Loading Scala Sources

Since you're simulating a scala developer who is using your macro / plugin to write scala code, you put the code that you want to test into the `test/resources` folder of your project (not `test/scala`). Add source resources as needed

```scala
import org.ensime.pcplod._
```

```scala
withPcPlod { pc =>
  pc.loadScala("path/to/package/foo.scala")
  // your tests here
  pc.loadScala("path/to/package/bar.scala")
  pc.unloadScala("path/to/package/foo.scala")
  // more tests here
}
```

there is also a simplified variant (Mr Plod) which only supports one source (a simpler assertion API)

```scala
withMrPlod("path/to/package/foo.scala") { mr =>
  // your tests here
}
```

### Making Assertions

The `PcPlod` instance has the following main assertions:

- `symbolAtPoint(res: String, p: Point): Option[String]` - the name of the symbol at point.
- `typeAtPoint(res: String, p: Point): Option[String]` - the return type of the symbol at the point.
- `messages: List[PcMessage]`

Typically if `symbolAtPoint` or `typeAtPoint` do not work (or there are any errors) then your code is not expected to work in the presentation compiler.

A variant of `typeAtPoint` and `symbolAtPoint` without the `res` is provided for `withMrPlod`.

An implicit conversion for Point means that any of the following can be provided in its place:

- `Int` the number of characters
- `(Int, Int)` the line and column
- `Symbol` corresponding to *noddy syntax*

with all counting from zero.

### Noddy Syntax

Instead of having to manually count the location in the source, you can augment your test sources with a `@noddy_syntax@` that will be stripped and treated as meta data when loaded:

```scala
object F@foo@oo {
  def bar(a@input_a@: String): Int = ???
}
```

in this case, the symbol `'foo` will refer to the letter `F` of `Foo` and the symbol `'input_a` will refer to the parameter `a` of `bar` (i.e. the character immediately before the marker). Noddy names can only be alphanumeric or underscore.

## Customisation

By default, PC Plod will make everything that is on the test's classpath available for use in the presentation compiler. However, you may wish to provide a custom jar, which can be provided to `withPcPlod` as a parameter (not available for `withMrPlod`). It is recommended to use sbt to generate the jar and inject it into the test environment as a property.

```scala
val jar = sys.props("pcplod.test1.jar")
withPcPlod(jar) { pc =>
  pc.loadScala("path/to/package/foo.scala")
  // your tests here
  pc.loadScala("path/to/package/bar.scala")
  pc.unloadScala("path/to/package/foo.scala")
  // more tests here
}
```

## Example ENSIME Integration

To add the example compiler plugin example to ENSIME, automatically compiling the plugin first, add this file to your local clone of the repository in `ensime.sbt`

```scala
EnsimeKeys.ensimeCompilerArgs += {
  val jar = (packageBin in Compile in LocalProject("example")).value
  s"-Xplugin:${jar.getCanonicalPath}"
}
```
