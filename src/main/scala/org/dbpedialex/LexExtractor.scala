package org.dbpedialex

import org.apache.jena.rdf.model.ModelFactory
import org.apache.spark.sql.SparkSession
import LexUtils.Spark._
import org.apache.jena.graph.Node
import org.apache.spark.rdd.RDD
import org.apache.jena.graph.{Triple => JenaTriple}

import scala.collection.JavaConverters._

object LexExtractor {

  private val wikilinksLink = "http://www.w3.org/2005/11/its/rdf#taIdentRef"
  private val wikilinksSf = "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf"

  def extractPolysemFromDisambiguations(disambigFN: String, labelsFN: String, outputFN: String, lang: String)(spark: SparkSession) = {
    val filters = Set(
      "http://dbpedia.org/ontology/wikiPageDisambiguates"
    )

    val trpls = spark.sparkContext
      .textFile(disambigFN)
      .extractTriples(Some(filters))

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
        (r._2._1.getObject.toString, (subj_label, r._2._1))
      })
      .join(labels)
      .map(r => {
        val obj_label = r._2._2.getObject.getLiteralValue.toString
        (r._2._1._1, r._2._1._2, obj_label)
      })
      .groupBy(_._2.getSubject.getURI)
      .map(r => {
        r._2
      })

    rs.printFirstN(10)

    val ds = rs
      .map(dt =>
        (
          (dt.head._1, dt.head._2.getSubject.getURI),
          dt.map(t => (t._3, t._2.getObject.getURI))
        )
      )
      .map(m => LexUtils.polysemi(m._1, m._2.toSeq, lang)(ModelFactory.createDefaultModel()))
      .map(m => {
        val re = m.getGraph.find().asScala.toSeq
        m.close()
        re
      })

    ds
      .filter(tps => tps.count(_.getObject.toString.contains("LexicalEntry")) == 4)
      .printOne(lang)

    ds
      .flatMap(a => a)
      .saveToNTriplesFile(outputFN)
  }

  def extractSynonymsFromRedirects(redirectsFN: String, labelsFN: String, outputFN: String, lang: String)(spark: SparkSession) = {
    val redTriples = spark.sparkContext
      .textFile(redirectsFN)
      .extractTriples(None)

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
        LexUtils.synonyms(p._1, p._2.toSeq, lang, true)(model)
      })
      .map(m => {
        val re = m.getGraph.find().asScala.toSeq
        m.close()
        re
      })

    models
      .filter(tps => tps.count(_.getObject.toString.contains("LexicalEntry")) == 3)
      .printOne(lang)

    models
      .flatMap(a => a)
      .saveToNTriplesFile(outputFN)
  }


  def extractPolysemAndSynonymsFromWikilinks(wikilinksFN: String, outputPolysemFN: String, outputSynonymsFN: String, lang: String)(spark: SparkSession) = {
    val filters = Set(
      wikilinksLink,
      wikilinksSf
    )

    val triples = spark.sparkContext
      .textFile(wikilinksFN)
      .extractTriples(Some(filters))

    triples.printFirstN(100)

    val groupById = triples
      .groupBy(_.getSubject)
    extractPolysem(groupById, outputPolysemFN, lang)
    extractSynonyms(groupById, outputSynonymsFN, lang)
  }


  private def extractPolysem(triplesById:  RDD[(Node, Iterable[JenaTriple])], outputFN: String, lang: String) = {
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

    polysemy
      .flatMap(p => p._1.map(m => (m.toString, p._2)))
      .map(p =>
        LexUtils.polysemi(p._1, p._2.keys.toSeq, lang)(ModelFactory.createDefaultModel())
      )
      .flatMap(m => m.getGraph.find().asScala)
      .saveToNTriplesFile(outputFN)
  }

  private def extractSynonyms(triplesById:  RDD[(Node, Iterable[JenaTriple])], outputFN: String, lang: String) = {
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

    synonyms
      .flatMap(p => p._1.map(m => (m, p._2)))
      .map(p =>
        LexUtils.synonyms(p._1, p._2.keys.toSeq, lang, false)(ModelFactory.createDefaultModel())
      )
      .flatMap(m => m.getGraph.find().asScala)
      .saveToNTriplesFile(outputFN)
  }

}
