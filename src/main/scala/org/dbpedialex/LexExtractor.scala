package org.dbpedialex

import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.spark.sql.SparkSession
import LexUtils.Spark._
import org.apache.jena.graph.Node
import org.apache.spark.rdd.RDD
import org.apache.jena.graph.{Triple => JenaTriple}

object LexExtractor {

  private[dbpedialex] val textlinksLink = "http://www.w3.org/2005/11/its/rdf#taIdentRef"
  private[dbpedialex] val textlinksSf = "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf"


  def mergeAllDistinct(files: Seq[String], outFN: String)(spark: SparkSession) =
    files
      .map(fn => spark.sparkContext.textFile(fn))
      .reduce((l, r) => l.union(r))
      .distinct()
      .saveToFile(outFN)

  def extractLexEntriesFromLabels(labelsFN: String, outputFN: String, lang: String)(spark: SparkSession) = {
    spark
      .sparkContext
      .textFile(labelsFN)
      .extractTriples(None)
      .map(LexUtils.labelTripleToModel(_, lang))
      .extractSingleTripes
      .saveToNTriplesFile(outputFN)
    outputFN
  }

  def extractPolysemFromDisambiguations(disambigFN: String, labelsFN: String, outputFN: String, lang: String)(spark: SparkSession) = {
    val filters = Set(
      "http://dbpedia.org/ontology/wikiPageDisambiguates"
    )

    val trpls = spark.sparkContext
      .textFile(disambigFN)
      .extractTriples(Some(filterFromSet(filters)))

    val labels = spark.sparkContext
      .textFile(labelsFN)
      .extractTriples(None)

    polysemDisambigGroupedTriples(trpls, labels)
      .map(dt =>
        (
          (dt.head._1, dt.head._2.getSubject.getURI),
          dt.map(t => (t._3, t._2.getObject.getURI))
        )
      )
      .map(m => LexUtils.polysemiDisambig(m._1, m._2.toSeq, lang)(ModelFactory.createDefaultModel()))
      .extractSingleTripes
      .saveToNTriplesFile(outputFN)
    outputFN
  }

  def polysemDisambigGroupedTriples(disambig: RDD[JenaTriple], labels: RDD[JenaTriple]): RDD[Iterable[(String, JenaTriple, Option[String])]] = {
    val labJoin = labels.keyBy(_.getSubject.getURI)
    disambig
      .map(t => (t.getSubject.getURI, t))
      .join(labJoin)
      .map(r => {
        val subj_label = r._2._2.getObject.getLiteralValue.toString
        (r._2._1.getObject.getURI, (subj_label, r._2._1))
      })
      .leftOuterJoin(labJoin)
      .map(r => {
        val obj_label = r._2._2.map(_.getObject.getLiteralValue.toString)
        (r._2._1._1, r._2._1._2, obj_label)
      })
      .groupBy(_._2.getSubject.getURI)
      .map(_._2)
  }

  def extractSynonymsFromRedirects(redirectsFN: String, labelsFN: String, outputFN: String, lang: String)(spark: SparkSession) = {
    val redTriples = spark.sparkContext
      .textFile(redirectsFN)
      .extractTriples(None)

    val labels = spark.sparkContext
      .textFile(labelsFN)
      .extractTriples(None)

    synsFromTriplesForRedirects(redTriples, labels)
      .map(p =>
        (p.head._2.getObject.getURI, p.map(_._1) ++ Seq(p.head._3))
      )
      .map(p =>
        LexUtils.synonyms(p._1, p._2.toSeq.map(s => (s, -1)), lang, true, false)(ModelFactory.createDefaultModel())
      )
      .extractSingleTripes
      .saveToNTriplesFile(outputFN)
    outputFN
  }

  def synsFromTriplesForRedirects(redirects: RDD[JenaTriple], labels: RDD[JenaTriple]): RDD[Iterable[(String, JenaTriple, String)]] = {
    val labs = labels.keyBy(_.getSubject.getURI)
    redirects
      .keyBy(_.getSubject.getURI)
      .join(labs)
      .map(r => {
        val subj_label = r._2._2.getObject.getLiteralValue.toString
        (r._2._1.getObject.toString, (subj_label, r._2._1))
      })
      .join(labs)
      .map(r => {
        val obj_label = r._2._2.getObject.getLiteralValue.toString
        (r._2._1._1, r._2._1._2, obj_label)
      })
      .groupBy(_._2.getObject.getURI)
      .map(_._2)
  }


  def extractPolysemAndSynonymsFromTextlinks(textlinksFN: String, redirectsFN: String, outputPolysemFN: String, outputSynonymsFN: String, lang: String, doFiltering: Boolean)(spark: SparkSession) = {
    val filters = Set(
      textlinksLink,
      textlinksSf
    )

    val triplesGrouped = spark.sparkContext
      .textFile(textlinksFN)
      .extractTriples(Some(filterFromSet(filters)))
      .groupBy(_.getSubject)

    val reds = spark.sparkContext
      .textFile(redirectsFN)
      .extractTriples(None)

    extractPolysem(triplesGrouped, reds, outputPolysemFN, lang, doFiltering)
    extractSynonyms(triplesGrouped, outputSynonymsFN, lang, doFiltering)
    Seq(outputPolysemFN, outputSynonymsFN)
  }

  def polySemModelsFromTriples(tpls: RDD[Iterable[JenaTriple]], lang: String, filter: Boolean): RDD[Model] = {
    tpls
      .map(tps => {
        val li = tps.find(_.getPredicate.getURI == textlinksLink)
          .map(_.getObject.getURI)
        val sfo = tps.find(_.getPredicate.getURI == textlinksSf)
          .map(_.getObject.getLiteralValue)
        // here the order in the tuple matters!
        (sfo, li)
      })
      .groupBy(_._1)
      .map(gp => {
        (gp._1, gp._2.flatMap(_._2)
          .groupBy(a => a)
          .map(p => (p._1, p._2.size))
        )
      })
      .flatMap(p => p._1.map(m => (m.toString, p._2)))
      .map(p =>
        LexUtils.polysemi(p._1, p._2.toSeq, lang, filter)(ModelFactory.createDefaultModel())
      )
  }

  def synonymModelsFromTriples(tpls: RDD[Iterable[JenaTriple]], lang: String, filter: Boolean): RDD[Model] = {
    tpls
      .map(tps => {
        val li = tps.find(_.getPredicate.getURI == textlinksLink)
          .map(_.getObject.getURI)
        val sfo = tps.filter(_.getPredicate.getURI == textlinksSf)
          .map(_.getObject.getLiteralValue).toSeq
        // here the order in the tuple matters!
        (li, sfo)
      })
      .groupBy(_._1)
      .map(gp => {
        (gp._1, gp._2.flatMap(_._2)
          .groupBy(_.toString)
          .map(p => (p._1, p._2.size)))
      })
      .flatMap(p => p._1.map(m => (m, p._2)))
      .map(p =>
        LexUtils.synonyms(p._1, p._2.toSeq, lang, false, filter)(ModelFactory.createDefaultModel())
      )
  }


  private def extractPolysem(triplesById: RDD[(Node, Iterable[JenaTriple])], redirects: RDD[JenaTriple], outputFN: String, lang: String, doFiltering: Boolean) = {
    val seqs = polySemModelsFromTriples(triplesById.map(_._2), lang, doFiltering)
      .extractSingleTripes
    replaceWithRedirects(
      seqs,
      redirects
    ).saveToNTriplesFile(outputFN)
    outputFN
  }

  private def replaceWithRedirects(triples: RDD[JenaTriple], redirects: RDD[JenaTriple]): RDD[JenaTriple] = {
    val senses = triples.filter(_.getPredicate.getURI == LexUtils.OntolexReference)
    val other = triples.filter(_.getPredicate.getURI != LexUtils.OntolexReference)

    senses.keyBy(_.getObject.getURI)
      .leftOuterJoin(redirects.keyBy(_.getSubject.getURI))
      .map(_._2)
      .map(p =>
        p._2
          .map(red =>
            JenaTriple.create(p._1.getSubject, p._1.getPredicate, red.getObject)
          )
          .getOrElse(p._1)
      )
      .union(other)
  }

  private def extractSynonyms(triplesById: RDD[(Node, Iterable[JenaTriple])], outputFN: String, lang: String, doFiltering: Boolean) = {
    synonymModelsFromTriples(triplesById
      .map(_._2), lang, doFiltering)
      .extractSingleTripes
      .saveToNTriplesFile(outputFN)
    outputFN
  }

  private[dbpedialex] def filterFromSet(takeOnlyContaining: Set[String]): String => Boolean =
    (s: String) => takeOnlyContaining.exists(s.contains)


}
