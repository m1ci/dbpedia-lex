package org.dbpedialex

import org.apache.jena.rdf.model.ModelFactory
import org.dbpedialex.LexApp.{getOptionParam, getParam}
import org.dbpedialex.LexExtractor.{filterFromSet, polySemModelsFromTriples, synonymModelsFromTriples, textlinksLink, textlinksSf}
import org.dbpedialex.LexUtils.Spark._

object ExamplesGenerator extends App {
  val lang = getParam("lang_tag")

  val labels = getOptionParam("labels_fn")
  val redirects = getOptionParam("redirects_fn")
  val disambiguations = getOptionParam("disambiguations_fn")
  val textlinks = getOptionParam("textlinks_fn")


  val spark = LexApp.initSpark()

  labels.foreach(fn => {
    val strings = spark
      .sparkContext
      .textFile(fn)

    strings
      .printFirstN(1)

    strings
      .extractTriples(None)
      .map(LexUtils.labelTripleToModel(_, lang))
      .extractTriplesSeq
      .printOne(lang, Some("labels"))
  })

  (labels, disambiguations) match {
    case (Some(lf), Some(df)) =>
      val lbls = spark.sparkContext.textFile(lf)
        .extractTriples(None)

      val dis = spark.sparkContext.textFile(df)
        .extractTriples(None)

      val tpls = LexExtractor.polysemDisambigGroupedTriples(dis, lbls)

      tpls
        .map(_.map(_._2))
        .filter(_.size == 3)
        .map(a => LexUtils.triplesToNTriples(a.toSeq))
        .printFirstN(1)

      tpls
        .map(dt =>
          (
            (dt.head._1, dt.head._2.getSubject.getURI),
            dt.map(t => (t._3, t._2.getObject.getURI))
          )
        )
        .map(m => LexUtils.polysemiDisambig(m._1, m._2.toSeq, lang)(ModelFactory.createDefaultModel()))
        .extractTriplesSeq
        .printOne(lang, Some("disambig"))

    case _ =>
  }

  (labels, redirects) match {
    case (Some(lf), Some(rf)) =>
      val lbls = spark.sparkContext.textFile(lf)
        .extractTriples(None)
      val reds = spark.sparkContext.textFile(rf)
        .extractTriples(None)

      val syns = LexExtractor.synsFromTriplesForRedirects(reds, lbls)

      syns
        .map(_.map(_._2))
        .filter(_.size == 3)
        .map(a => LexUtils.triplesToNTriples(a.toSeq))
        .printFirstN(1)

      syns
        .map(p =>
          (p.head._2.getObject.getURI, p.map(_._1) ++ Seq(p.head._3))
        )
        .map(p =>
          LexUtils.synonyms(p._1, p._2.toSeq.map(s => (s, -1)), lang, true, false)(ModelFactory.createDefaultModel())
        )
        .extractTriplesSeq
        .printOne(lang, Some("redirects"))

    case _ =>
  }

  textlinks.foreach(tf => {
    val filters = Set(
      textlinksLink,
      textlinksSf
    )
    val triplesGrouped = spark.sparkContext
      .textFile(tf)
      .extractTriples(Some(filterFromSet(filters)))
      .groupBy(_.getSubject)
      .map(_._2)

    triplesGrouped
      .map(a => LexUtils.triplesToNTriples(a.toSeq))
      .printFirstN(10)

    polySemModelsFromTriples(triplesGrouped, lang, false)
      .extractTriplesSeq
      .filter(s => s.size > 20 && s.size < 50)
      .printOne(lang, Some("links polysem"))

    synonymModelsFromTriples(triplesGrouped, lang, false)
      .extractTriplesSeq
      .filter(s => s.size > 20 && s.size < 50)
      .printOne(lang, Some("links synonyms"))

  })

}
