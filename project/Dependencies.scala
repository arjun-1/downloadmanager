import sbt._

object Dependencies {

  object Versions {
    val http4s          = "0.21.6"
    val zio             = "1.0.0"
    val sttp            = "2.2.3"
    val circe           = "0.13.0"
    val log4j           = "2.13.3"
    val endpoints4s     = "1.1.0"
    val zioLogging      = "0.4.0"
    val newtype         = "0.4.4"
    val cats            = "2.1.1"
    val circeDerivation = "0.13.0-M4"
    val zioCats         = "2.1.4.0"
    val pureConfig      = "0.13.0"
  }

  val circeDerivation = "io.circe" %% "circe-derivation" % Versions.circeDerivation

  val circe = Seq("circe-core", "circe-refined", "circe-literal")
    .map("io.circe" %% _ % Versions.circe)

  val zio = Seq("zio", "zio-test", "zio-test-sbt", "zio-test-magnolia", "zio-macros")
    .map("dev.zio" %% _ % Versions.zio)

  val zioLogging = Seq("zio-logging", "zio-logging-slf4j").map("dev.zio" %% _ % Versions.zioLogging)

  val zioCats = "dev.zio" %% "zio-interop-cats" % Versions.zioCats

  val log4j = Seq("log4j-core", "log4j-slf4j-impl")
    .map("org.apache.logging.log4j" % _ % Versions.log4j)

  val sttp = Seq("core", "circe", "http4s-backend")
    .map("com.softwaremill.sttp.client" %% _ % Versions.sttp) :+
    ("com.softwaremill.sttp.client" %% "zio" % Versions.sttp % "test")

  val cats = "org.typelevel" %% "cats-core" % Versions.cats

  val http4s = Seq("http4s-circe", "http4s-dsl", "http4s-blaze-server", "http4s-blaze-client")
    .map("org.http4s" %% _ % Versions.http4s)

  val pureConfig = "com.github.pureconfig" %% "pureconfig" % Versions.pureConfig

  val newtype = "io.estatico" %% "newtype" % Versions.newtype

  val endpoints4s = Seq("json-schema-generic", "algebra", "algebra-json-schema")
    .map("org.endpoints4s" %% _ % Versions.endpoints4s)

  val endpoints4sHttp4sServer = "org.endpoints4s" %% "http4s-server" % "2.0.0"

  val deps = circe ++ endpoints4s ++ http4s ++ zio ++ zioLogging ++ log4j ++ sttp :+ zioCats :+
    cats :+ newtype :+ circeDerivation :+ pureConfig :+ endpoints4sHttp4sServer

}
