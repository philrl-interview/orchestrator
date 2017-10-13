lazy val akkaHttpVersion = "10.0.10"
lazy val akkaVersion    = "2.5.4"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.orchestrator",
      scalaVersion    := "2.12.3"
    )),
    name := "Orchestrator",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,

      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,

      "org.scalatest"     %% "scalatest"         % "3.0.1"         % Test,
      "org.scalamock"     %% "scalamock-scalatest-support" % "3.6.0" % Test
    )
  )
