name := "dbpedia-lex"
scalaVersion := "2.12.10"

val sparkVersion = "3.2.0"
val jenaVersion = "3.17.0"

libraryDependencies ++= Seq(
  "org.apache.jena" % "apache-jena-libs" % jenaVersion,

  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-core" % sparkVersion
)


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}