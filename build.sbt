inThisBuild {
  Seq(
    scalaVersion := "2.12.2",
    organization := "org.ensime",
    sonatypeGithub := ("ensime", "pcplod"),
    licenses := Seq(Apache2),
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )
}

val common = Seq(
  scalacOptions += "-language:implicitConversions",
  javaOptions in Test ++= {
    val jar = (packageBin in Compile).value
    Seq(
      s"""-Dpcplod.settings=-Xplugin:${jar.getAbsolutePath},-Jdummy=${jar.lastModified}""",
      s"""-Dpcplod.classpath=${(fullClasspath in Test).value.map(_.data).mkString(",")}"""
    )
  },
  mimaPreviousArtifacts := Set(organization.value %% name.value % "1.2.0")
)

lazy val pcplod = project.settings(common)

lazy val example = project.dependsOn(pcplod % "test").settings(
  // too awkward to remove the deprecated warning
  scalacOptions -= "-Xfatal-warnings",
  scalacOptions in Test ++= {
    val jar = (packageBin in Compile).value
    Seq(s"-Xplugin:${jar.getAbsolutePath}", s"-Jdummy=${jar.lastModified}") // ensures recompile
  }
).settings(common)
