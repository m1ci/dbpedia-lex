package org.dbpedialex

import java.nio.file.{Path, Paths}

import org.apache.spark.sql.SparkSession

object LexApp {

  def main(args: Array[String]): Unit = {
    val lang = getParam("lang_tag")

    val labels = getOptionParam("labels_fn")
    val redirects = getOptionParam("redirects_fn")
    val disambiguations = getOptionParam("disambiguations_fn")
    val textlinks = getOptionParam("textlinks_fn")
    val outFolder = Paths.get(getParam("output_folder"))
    val outRoot = outFolder.resolve(lang)

    println(
      s"""
         |Welcome to Lex App.
         |You specified following config:
         |labels file: $labels
         |redirects file: $redirects
         |disambiguations file: $disambiguations
         |textlinks file: $textlinks
         |output folder: $outFolder
         |""".stripMargin
    )

    val outRedirectsSyn = outRoot.resolve("redirects_syn.nt")
    val outTextlinksSyn = outRoot.resolve("textlinks_syn.nt")
    val outWikilinksPolysem = outRoot.resolve("textlinks_polysem.nt")
    val outDisambigPolysem = outRoot.resolve("disambig_polysem.nt")
    val outLexEntrLabels = outRoot.resolve("labels_lex_entries.nt")

    val outAllMerged = outRoot.resolve("all_merged.nt")

    lazy val spark = initSpark()

    val labs = labels match {
      case Some(l) => Seq(LexExtractor.extractLexEntriesFromLabels(l, outLexEntrLabels, lang)(spark))
      case _ => Seq.empty
    }

    val redir = (redirects, labels) match {
      case (Some(r), Some(l)) => Seq(LexExtractor.extractSynonymsFromRedirects(r, l, outRedirectsSyn, lang)(spark))
      case _ => Seq.empty
    }

    val links = (redirects, textlinks) match {
      case (Some(r), Some(w)) => LexExtractor.extractPolysemAndSynonymsFromTextlinks(w, r, outWikilinksPolysem, outTextlinksSyn, lang)(spark)
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
      redir
    ).flatten

    println(s"Generated following files: $fns.")

    if (fns.size > 1) {
      println("Now merging.")
      LexExtractor.mergeAllDistinct(fns, outAllMerged)(spark)
      println(s"Merge completed!")
    }

    println("Success!")
  }

  implicit def paToSrt(p: Path): String =
    p.toAbsolutePath.toString

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
