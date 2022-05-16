name := "dbpedia-lex"
version := "0.1"

scalaVersion := "2.12.13"

mainClass in assembly := Some("org.dbpedialex.LexApp")

val sparkVersion = "3.2.0"
val jenaVersion = "3.17.0"

libraryDependencies ++= Seq(
  "org.apache.jena" % "apache-jena-libs" % jenaVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-core" % sparkVersion
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
