package org.dbpedialex

import org.apache.jena.rdf.model.ModelFactory
import org.apache.spark.sql.SparkSession
import LexUtils.Spark._
import org.apache.jena.graph.Node
import org.apache.spark.rdd.RDD
import org.apache.jena.graph.{Triple => JenaTriple}

import scala.collection.JavaConverters._

object LexExtractor {

  val DebugLimit = -1

  private val wikilinksLink = "http://www.w3.org/2005/11/its/rdf#taIdentRef"
  private val wikilinksSf = "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf"


  def mergeAllDistinct(files: Seq[String], outFN: String)(spark: SparkSession) =
    files
      .map(fn => spark.sparkContext.textFile(fn))
      .reduce((l, r) => l.union(r))
      .distinct()
      .saveToFile(outFN)

  def extractLexEntriesFromLabels(labelsFN: String, outputFN: String, lang: String)(spark: SparkSession) = {
    val triples = spark
      .sparkContext
      .textFile(labelsFN)
      .extractTriples(None)
      .limit(DebugLimit)

    val seqs = triples
      .map(LexUtils.labelTripleToModel(_, lang))
      .extractTriplesSeq
    seqs
      .filter(tps => tps.exists(t => t.getSubject.getURI.contains(LexUtils.LexPrefix + lang + "/ls") && t.getSubject.getURI.contains("(")))
      .printOne("mk", Some("labels"))

    seqs.flatMap(s => s)
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
      .limit(DebugLimit)

    val labTriples = spark.sparkContext
      .textFile(labelsFN)
      .extractTriples(None)

    trpls.printFirstN(10)

    val labels = labTriples
      .map(t => (t.getSubject.getURI, t))

    val rs = trpls
      .map(t => (t.getSubject.getURI, t))
      .join(labels)
      .map(r => {
        val subj_label = r._2._2.getObject.getLiteralValue.toString
        (r._2._1.getObject.getURI, (subj_label, r._2._1))
      })
      .leftOuterJoin(labels)
      .map(r => {
        val obj_label = r._2._2.map(_.getObject.getLiteralValue.toString)
        (r._2._1._1, r._2._1._2, obj_label)
      })
      .groupBy(_._2.getSubject.getURI)
      .map(_._2)

    rs.printFirstN(10)

    val ds = rs
      .map(dt =>
        (
          (dt.head._1, dt.head._2.getSubject.getURI),
          dt.map(t => (t._3, t._2.getObject.getURI))
        )
      )
      .map(m => LexUtils.polysemiDisambig(m._1, m._2.toSeq, lang)(ModelFactory.createDefaultModel()))
      .extractTriplesSeq

    ds
      .filter(tps => tps.count(_.getObject.toString.contains("LexicalEntry")) == 4)
      .filter(tps => tps.exists(t => t.getSubject.getURI.contains(LexUtils.LexPrefix + lang + "/ls") && t.getSubject.getURI.contains("(")))
      .printOne(lang, Some("disambig"))

    ds
      .flatMap(a => a)
      .saveToNTriplesFile(outputFN)
    outputFN
  }

  def extractSynonymsFromRedirects(redirectsFN: String, labelsFN: String, outputFN: String, lang: String)(spark: SparkSession) = {
    val redTriples = spark.sparkContext
      .textFile(redirectsFN)
      .extractTriples(None)
      .limit(DebugLimit)

    val labTriples = spark.sparkContext
      .textFile(labelsFN)
      .extractTriples(None)

    redTriples
      .printFirstN(10)

    labTriples
      .printFirstN(10)

    val labels = labTriples
      .map(t => (t.getSubject.getURI, t))

    val rs = redTriples
      .map(t => (t.getSubject.getURI, t))
      .join(labels)
      .map(r => {
        val subj_label = r._2._2.getObject.getLiteralValue.toString
        (r._2._1.getObject.toString, (subj_label, r._2._1))
      })
      .join(labels)
      .map(r => {
        val obj_label = r._2._2.getObject.getLiteralValue.toString
        (r._2._1._1, r._2._1._2, obj_label)
      })
      .groupBy(_._2.getObject.getURI)
      .map(r => {
        r._2
      })

    val models = rs
      .map(p =>
        (p.head._2.getObject.getURI, p.map(_._1) ++ Seq(p.head._3))
      )
      .map(p => {
        val model = ModelFactory.createDefaultModel()
        LexUtils.synonyms(p._1, p._2.toSeq.map(s => (s, None)), lang, true)(model)
      })
      .map(m => {
        val re = m.getGraph.find().asScala.toSeq
        m.close()
        re
      })

    models
      .filter(tps => tps.count(_.getObject.toString.contains("LexicalEntry")) == 3)
      .filter(tps => tps.exists(t => t.getSubject.getURI.contains(LexUtils.LexPrefix + lang + "/ls") && t.getSubject.getURI.contains("(")))
      .printOne(lang, Some("redirects"))

    models
      .flatMap(a => a)
      .saveToNTriplesFile(outputFN)
    outputFN
  }


  def extractPolysemAndSynonymsFromWikilinks(wikilinksFN: String, redirectsFN: String, outputPolysemFN: String, outputSynonymsFN: String, lang: String)(spark: SparkSession) = {
    val filters = Set(
      wikilinksLink,
      wikilinksSf
    )

    val triples = spark.sparkContext
      .textFile(wikilinksFN)
      .extractTriples(Some(filterFromSet(filters)))
      .limit(DebugLimit)

    triples.printFirstN(100)

    val groupById = triples
      .groupBy(_.getSubject)

    val reds = spark.sparkContext
      .textFile(redirectsFN)
      .extractTriples(None)

    extractPolysem(groupById, reds, outputPolysemFN, lang)
    extractSynonyms(groupById, outputSynonymsFN, lang)
    Seq(outputPolysemFN, outputSynonymsFN)
  }


  private def extractPolysem(triplesById: RDD[(Node, Iterable[JenaTriple])], redirects: RDD[JenaTriple], outputFN: String, lang: String) = {
    val polysemy = triplesById
      .map(tps => {
        val li = tps._2.find(_.getPredicate.getURI == wikilinksLink)
          .map(_.getObject.getURI)
        val sfo = tps._2.find(_.getPredicate.getURI == wikilinksSf)
          .map(_.getObject.getLiteralValue)
        (sfo, li)
      })
      .groupBy(_._1)
      .map(gp => {
        (gp._1, gp._2.flatMap(_._2)
          .groupBy(a => a)
          .map(p => (p._1, p._2.size))
        )
      })
      .sortBy(_._2.size, ascending = false)

    polysemy
      .printFirstN(10)

    val seqs = polysemy
      .flatMap(p => p._1.map(m => (m.toString, p._2)))
      .map(p =>
        LexUtils.polysemi(p._1, p._2.toSeq, lang)(ModelFactory.createDefaultModel())
      )
      .extractTriplesSeq

    seqs
      .filter(tps => tps.count(_.getObject.toString.contains("LexicalSense")) > 3 && tps.count(_.getObject.toString.contains("LexicalSense")) < 10)
      .printOne(lang, Some("polysem_links"))

    replaceWithRedirects(
      seqs
        .flatMap(s => s),
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

  private def extractSynonyms(triplesById: RDD[(Node, Iterable[JenaTriple])], outputFN: String, lang: String) = {
    val synonyms = triplesById
      .map(tps => {
        val li = tps._2.find(_.getPredicate.getURI == wikilinksLink)
          .map(_.getObject.getURI)
        val sfo = tps._2.filter(_.getPredicate.getURI == wikilinksSf)
          .map(_.getObject.getLiteralValue).toSeq
        (li, sfo)
      })
      .groupBy(_._1)
      .map(gp => {
        (gp._1, gp._2.flatMap(_._2)
          .groupBy(_.toString)
          .map(p => (p._1, p._2.size)))
      })
      .sortBy(_._2.size, ascending = false)

    synonyms
      .printFirstN(10)

    val seqs = synonyms
      .flatMap(p => p._1.map(m => (m, p._2)))
      .map(p =>
        LexUtils.synonyms(p._1, p._2.mapValues(Some(_)).toSeq, lang, false)(ModelFactory.createDefaultModel())
      )
      .extractTriplesSeq

    seqs
      .filter(tps => tps.count(_.getObject.toString.contains("LexicalEntry")) > 3 && tps.count(_.getObject.toString.contains("LexicalEntry")) < 10)
      .printOne(lang, Some("synon_links"))

    seqs
      .flatMap(s => s)
      .saveToNTriplesFile(outputFN)
    outputFN
  }

  private def filterFromSet(takeOnlyContaining: Set[String]): String => Boolean =
    (s: String) => takeOnlyContaining.exists(s.contains)


}
