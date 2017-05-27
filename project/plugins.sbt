ivyLoggingLevel := UpdateLogging.Quiet
scalacOptions in Compile ++= Seq("-feature", "-deprecation")

addSbtPlugin("com.fommil" % "sbt-sensible" % "1.2.0")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.14")
