val scala3Version = "3.3.3"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .in(file("shared"))
  .settings(
    name := "fm-game-shared",
    scalaVersion := scala3Version,
    // Źródła współdzielone w shared/src/main/scala (crossProject domyślnie szuka w shared/shared/)
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala",
    libraryDependencies += "io.circe" %%% "circe-core" % "0.14.6",
    libraryDependencies += "io.circe" %%% "circe-generic" % "0.14.6",
    libraryDependencies += "io.circe" %%% "circe-parser" % "0.14.6",
  )

lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js

lazy val backend = project
  .in(file("backend"))
  .dependsOn(sharedJVM)
  .settings(
    name := "fm-game-backend",
    scalaVersion := scala3Version,
    libraryDependencies += "dev.zio" %% "zio" % "2.0.21",
    libraryDependencies += "dev.zio" %% "zio-json" % "0.6.2",
    libraryDependencies += "io.circe" %% "circe-core" % "0.14.6",
    libraryDependencies += "io.circe" %% "circe-generic" % "0.14.6",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.6",
    libraryDependencies += "org.tpolecat" %% "doobie-core" % "1.0.0-RC5",
    libraryDependencies += "org.tpolecat" %% "doobie-h2" % "1.0.0-RC5",
    libraryDependencies += "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC5",
    libraryDependencies += "dev.zio" %% "zio-http" % "3.0.0-RC6",
    libraryDependencies += "dev.zio" %% "zio-http-testkit" % "3.0.0-RC6" % Test,
    libraryDependencies += "com.github.jwt-scala" %% "jwt-circe" % "10.0.0",
    libraryDependencies += "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0" cross CrossVersion.for3Use2_13,
    libraryDependencies += "com.h2database" % "h2" % "2.2.224",
    libraryDependencies += "org.postgresql" % "postgresql" % "42.7.2",
    libraryDependencies += "dev.zio" %% "zio-interop-cats" % "23.1.0.0",
    libraryDependencies += "dev.zio" %% "zio-test" % "2.0.21" % Test,
    libraryDependencies += "dev.zio" %% "zio-test-sbt" % "2.0.21" % Test,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )

lazy val frontend = project
  .in(file("frontend"))
  .dependsOn(sharedJS)
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "fm-game-frontend",
    scalaVersion := scala3Version,
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "com.raquo" %%% "laminar" % "17.2.1",
    libraryDependencies += "io.circe" %%% "circe-core" % "0.14.6",
    libraryDependencies += "io.circe" %%% "circe-generic" % "0.14.6",
    libraryDependencies += "io.circe" %%% "circe-parser" % "0.14.6",
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
  )

lazy val root = project
  .in(file("."))
  .aggregate(sharedJVM, sharedJS, backend, frontend)
  .settings(
    name := "fm-game-root",
  )
