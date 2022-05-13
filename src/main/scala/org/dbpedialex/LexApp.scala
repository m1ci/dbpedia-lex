package org.dbpedialex

import org.apache.spark.sql.SparkSession

object LexApp extends App {

  val lang = getParam("lang_tag")

  val labels = getOptionParam("labels_fn")
  val redirects = getOptionParam("redirects_fn")
  val disambiguations = getOptionParam("disambiguations_fn")
  val wikilinks = getOptionParam("wikilinks_fn")
  val outFolder = getParam("output_folder")
  val outRoot = outFolder + lang + "/"

  println(
    s"""
       |Welcome to Lex App.
       |You specified following config:
       |labels file: $labels
       |redirects file: $redirects
       |disambiguations file: $disambiguations
       |wikilinks file: $wikilinks
       |output folder: $outFolder
       |""".stripMargin
  )

  val outRedirectsSyn = outRoot + "redirects_syn.nt"
  val outWikilinksSyn = outRoot + "wikilinks_syn.nt"
  val outWikilinksPolysem = outRoot + "wikilinks_polysem.nt"
  val outDisambigPolysem = outRoot + "disambig_polysem.nt"
  val outLexEntrLabels = outRoot + "labels_lex_entries.nt"

  val outAllMerged = outRoot + "all_merged.nt"

  val spark = initSpark()

  val labs = labels match {
    case Some(l) => Seq(LexExtractor.extractLexEntriesFromLabels(l, outLexEntrLabels, lang)(spark))
    case _ => Seq.empty
  }

  val redir = (redirects, labels) match {
    case (Some(r), Some(l)) => Seq(LexExtractor.extractSynonymsFromRedirects(r, l, outRedirectsSyn, lang)(spark))
    case _ => Seq.empty
  }

  val links = (redirects, wikilinks) match {
    case (Some(r), Some(w)) => LexExtractor.extractPolysemAndSynonymsFromWikilinks(w, r, outWikilinksPolysem, outWikilinksSyn, lang)(spark)
    case _ => Seq.empty
  }

  val disr = (disambiguations, labels) match {
    case (Some(d), Some(l)) => Seq(LexExtractor.extractPolysemFromDisambiguations(d, l, outDisambigPolysem, lang)(spark))
    case _ => Seq.empty
  }


  val fns = Seq(
    disr,
    labs,
    links,
    labs
  ).flatten

  println(s"Generated following files: $fns. Now merging.")

  LexExtractor.mergeAllDistinct(fns, outAllMerged)(spark)

  println(s"Merge completed. Success.")

  def getParam(pname: String): String =
    getOptionParam(pname)
      .getOrElse(throw new RuntimeException(s"Configuration parameter $pname not found"))

  def getOptionParam(pname: String): Option[String] =
    Option(System.getProperty(pname))
      .orElse(Option(System.getenv(pname)))
      .map(_.trim)
      .filter(_.nonEmpty)

  def initSpark() =
    SparkSession
      .builder
      .config("spark.executor.memory", "4g")
      .config("spark.driver.maxResultSize", "2g")
      .master("local[*]")
      .appName("DBpediaLex")
      .getOrCreate()

}
