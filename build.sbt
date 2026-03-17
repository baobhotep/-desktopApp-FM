val scala3Version = "3.3.3"

val circeVersion      = "0.14.6"
val zioVersion        = "2.0.21"
val doobieVersion     = "1.0.0-RC5"
val zioHttpVersion    = "3.0.0-RC6"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .in(file("shared"))
  .settings(
    name := "fm-game-shared",
    scalaVersion := scala3Version,
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"    % circeVersion,
      "io.circe" %%% "circe-generic" % circeVersion,
      "io.circe" %%% "circe-parser"  % circeVersion,
    ),
  )

lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js

lazy val backend = project
  .in(file("backend"))
  .dependsOn(sharedJVM)
  .settings(
    name := "fm-game-backend",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Wunused:all"),
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"               % zioVersion,
      "io.circe"               %% "circe-core"        % circeVersion,
      "io.circe"               %% "circe-generic"     % circeVersion,
      "io.circe"               %% "circe-parser"      % circeVersion,
      "org.tpolecat"           %% "doobie-core"       % doobieVersion,
      "org.tpolecat"           %% "doobie-h2"         % doobieVersion,
      "org.tpolecat"           %% "doobie-postgres"   % doobieVersion,
      "org.tpolecat"           %% "doobie-hikari"     % doobieVersion,
      "dev.zio"                %% "zio-http"          % zioHttpVersion,
      "dev.zio"                %% "zio-http-testkit"  % zioHttpVersion  % Test,
      "com.github.jwt-scala"   %% "jwt-circe"         % "10.0.0",
      "com.github.t3hnar"      %% "scala-bcrypt"      % "4.3.0" cross CrossVersion.for3Use2_13,
      "com.h2database"          % "h2"                % "2.2.224",
      "org.postgresql"           % "postgresql"        % "42.7.2",
      "dev.zio"                %% "zio-interop-cats"  % "23.1.0.0",
      "dev.zio"                %% "zio-test"          % zioVersion      % Test,
      "dev.zio"                %% "zio-test-sbt"      % zioVersion      % Test,
    ),
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
    libraryDependencies ++= Seq(
      "com.raquo"      %%% "laminar"      % "17.2.1",
      "io.circe"       %%% "circe-core"   % circeVersion,
      "io.circe"       %%% "circe-generic" % circeVersion,
      "io.circe"       %%% "circe-parser" % circeVersion,
      "org.scala-js"   %%% "scalajs-dom"  % "2.8.0",
    ),
  )

val gdxVersion = "1.12.1"
lazy val desktop = project
  .in(file("desktop"))
  .dependsOn(sharedJVM, backend)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "fm-game-desktop",
    scalaVersion := scala3Version,
    Compile / mainClass := Some("fmgame.desktop.DesktopLauncher"),
    Universal / javaOptions ++= (if (System.getProperty("os.name").toLowerCase.contains("mac")) Seq("-XstartOnFirstThread") else Seq.empty),
    libraryDependencies ++= Seq(
      "com.badlogicgames.gdx" % "gdx" % gdxVersion,
      "com.badlogicgames.gdx" % "gdx-backend-lwjgl3" % gdxVersion,
      "com.badlogicgames.gdx" % "gdx-platform" % gdxVersion classifier "natives-desktop",
      "com.badlogicgames.gdx" % "gdx-freetype" % gdxVersion,
      "com.badlogicgames.gdx" % "gdx-freetype-platform" % gdxVersion classifier "natives-desktop",
    ),
    run / fork := true,
    run / javaOptions ++= (if (System.getProperty("os.name").toLowerCase.contains("mac")) Seq("-XstartOnFirstThread") else Seq.empty),
    Compile / run / mainClass := Some("fmgame.desktop.DesktopLauncher"),
  )

lazy val root = project
  .in(file("."))
  .aggregate(sharedJVM, sharedJS, backend, frontend, desktop)
  .settings(
    name := "fm-game-root",
  )
