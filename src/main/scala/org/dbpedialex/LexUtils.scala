package org.dbpedialex

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.file.Paths
import java.util.UUID

import org.apache.jena.iri.IRIFactory
import org.apache.jena.rdf.model.{Model, ModelFactory, Resource}
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParserBuilder}
import org.apache.jena.vocabulary.RDF
import org.apache.spark.rdd.RDD
import org.apache.jena.graph.{Triple => JenaTriple}
import org.apache.hadoop.fs.{FileSystem, FileUtil, Path}

import scala.util.Try

object LexUtils {

  private val InsideBracketsPattern = "\\((.+?)\\)".r

  val LexPrefix = "https://dbpedia.org/lex/"
  val OntolexPrefix = "http://www.w3.org/ns/lemon/ontolex/"
  val LexInfoPrefix = "http://www.lexinfo.net/ontology/2.0/lexinfo#"
  val DctTermsPrefix = "http://purl.org/dc/terms/"

  val OntolexCanonicalForm = OntolexPrefix + "canonicalForm"
  val OntolexSense = OntolexPrefix + "sense"
  val OntolexLexicalEntry = OntolexPrefix + "LexicalEntry"
  val OntolexLexicalSense = OntolexPrefix + "LexicalSense"
  val OntolexWrittenRep = OntolexPrefix + "writtenRep"
  val OntolexReference = OntolexPrefix + "reference"
  val LexInfoSyn = LexInfoPrefix + "synonym"
  val DctSubject = DctTermsPrefix + "subject"

  object Spark {

    implicit def rddStrToTripleExtr(rdd: RDD[String]) = new TripleExtractor(rdd)

    implicit def rddTripleToTripleProc(rdd: RDD[JenaTriple]) = new TripleProcessor(rdd)

    implicit def rddTSeqToTripleSeqProc(rdd: RDD[Seq[JenaTriple]]) = new TripleSeqProcessor(rdd)

    implicit def rddToAnyProc[T](rdd: RDD[T]) = new AnyProcessor[T](rdd)

    class TripleExtractor(rdd: RDD[String]) {

      def extractTriples(filters: Option[Set[String]]): RDD[JenaTriple] = {
        val tmp = rdd.map(_.trim)
          .filter(_.nonEmpty)

        filters
          .map(f => tmp.filter(s => f.exists(s.contains)))
          .getOrElse(tmp)
          //todo: here we can improve by parsing batches of strings
          .flatMap(s => Try(parseString(s)).toOption)
          .flatMap(m => Try {
            val trpl = m.getGraph.find().next()
            m.close()
            trpl
          }.toOption)
      }
    }

    class AnyProcessor[T](rdd: RDD[T]) {

      def printFirstN(n: Int): Unit =
        rdd
          .take(n)
          .foreach(t => println(t.toString))

    }

    class TripleProcessor(rdd: RDD[JenaTriple]) {

      def saveToNTriplesFile(filename: String): Unit = {
        val tmp_fld = UUID.randomUUID().toString
        val out_pa = Paths.get(filename)
        val out = new Path(out_pa.toAbsolutePath.toFile.getAbsolutePath)
        val tmp_out = out_pa.getParent.resolve(tmp_fld)
        rdd
          .map(t => {
            val m = ModelFactory.createDefaultModel()
            m.getGraph.add(t)
            val out = new ByteArrayOutputStream()
            RDFDataMgr.write(out, m.getGraph, Lang.NTRIPLES)
            m.close()
            out.toString.replaceAllLiterally("\n", "").replaceAllLiterally("\r", "")
          })
          .repartition(1)
          .saveAsTextFile(tmp_out.toFile.getAbsolutePath)
        val hdfs = FileSystem.get(rdd.sparkContext.hadoopConfiguration)
        hdfs.setWriteChecksum(false)

        val tmp_out_pa = new Path(tmp_out.resolve("part-00000").toFile.getAbsolutePath)
        FileUtil.copy(hdfs,
          tmp_out_pa,
          hdfs,
          out,
          false,
          true,
          rdd.sparkContext.hadoopConfiguration
        )
        FileUtil.fullyDelete(tmp_out.toFile)
      }
    }

    class TripleSeqProcessor(rdd: RDD[Seq[JenaTriple]]) {

      def printOne(lang: String) =
        rdd
          .map(LexUtils.trpilesToRawTtlString(_, lang))
          .take(1)
          .foreach(m => {
            println(m)
          })

    }

  }


  def trpilesToRawTtlString(tpls: Seq[JenaTriple], lang: String): String = {
    val model = ModelFactory.createDefaultModel()
    tpls.foreach(model.getGraph.add)
    model.setNsPrefix("ontolex", LexUtils.OntolexPrefix)
    model.setNsPrefix("lexinfo", LexUtils.LexInfoPrefix)
    model.setNsPrefix("lex", LexUtils.LexPrefix + lang + "/")
    model.setNsPrefix("dct", LexUtils.DctTermsPrefix)
    val out = new ByteArrayOutputStream()
    RDFDataMgr.write(out, model.getGraph, Lang.TTL)
    model.close()
    out.toString
  }

  def synonyms(senseLink: String, words: Seq[String], lang: String, replaceBrackets: Boolean)(model: Model) = {
    val senses = words.map(w => {
      val p = (w, model.createResource(ls(lang, w, 1)))
      if (replaceBrackets) {
        LexUtils.extractFromFirstBrackets(w)
          .foreach(s => p._2.addProperty(model.createProperty(DctSubject), model.createLiteral(s, lang)))
      }
      p
    }).toMap
    words.foreach(w => {
      val ler = model.createResource(le(lang, w))
      val lsr = senses(w)
      val cfr = model.createResource(cf(lang, w))
      lexEntry(ler, lsr, cfr)(model)
      val s = if (replaceBrackets) LexUtils.replaceBrackets(w) else w
      cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(s, lang))
      synEntry(lsr, (senses - w).values.toSeq, senseLink)(model)
    })
    model
  }

  def polysemi(word: String, links: Seq[String], lang: String)(model: Model) = {
    val senses = links.zipWithIndex.map(p => {
      val r = model.createResource(ls(lang, word, p._2))
      r.addProperty(RDF.`type`, model.createResource(OntolexLexicalSense))
      r.addProperty(model.createProperty(OntolexReference), model.createResource(p._1))
    })

    val ler = model.createResource(le(lang, word))
    val cfr = model.createResource(cf(lang, word))

    ler.addProperty(RDF.`type`, model.createResource(OntolexLexicalEntry))
    ler.addProperty(model.createProperty(OntolexCanonicalForm), cfr)
    cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(word, lang))
    senses.foreach(r => ler.addProperty(model.createProperty(OntolexSense), r))
    model
  }


  def replaceBrackets(s: String): String =
    s.replaceAll(InsideBracketsPattern.toString(), "").trim


  def extractFromFirstBrackets(s: String): Option[String] =
    InsideBracketsPattern.findFirstMatchIn(s).map(_.group(1))

  def polysemi(word: (String, String), wordsNlinks: Seq[(String, String)], lang: String)(model: Model) = {
    val senses = wordsNlinks.map(_._1).map(w => {
      val p = (w, model.createResource(ls(lang, w, 1)))
      LexUtils.extractFromFirstBrackets(w)
        .foreach(s => p._2.addProperty(model.createProperty(DctSubject), model.createLiteral(s, lang)))
      p
    }).toMap
    val ler = model.createResource(le(lang, word._1))
    val cfr = model.createResource(cf(lang, word._1))

    ler.addProperty(RDF.`type`, model.createResource(OntolexLexicalEntry))
    ler.addProperty(model.createProperty(OntolexCanonicalForm), cfr)
    senses.values.foreach(s => ler.addProperty(model.createProperty(OntolexSense), s))
    cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(LexUtils.replaceBrackets(word._1), lang))

    wordsNlinks.foreach(p => {
      val ler = model.createResource(le(lang, p._1))
      val cfr = model.createResource(cf(lang, p._1))
      val lsr = senses(p._1)
      LexUtils.lexEntry(ler, lsr, cfr)(model)
      cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(LexUtils.replaceBrackets(word._1), lang))

      lsr.addProperty(RDF.`type`, model.createResource(OntolexLexicalSense))
      lsr.addProperty(model.createProperty(OntolexReference), model.createResource(p._2))
    })

    model
  }

  def le(lang: String, value: String): String =
    link(lang, "le", value)

  def cf(lang: String, value: String): String =
    link(lang, "cf", value)

  def ls(lang: String, value: String, id: Int = 1): String =
    link(lang, "ls", value + "_sense" + id)

  def parseString(s: String) = {
    val model = ModelFactory.createDefaultModel
    RDFParserBuilder.create()
      .fromString(s)
      .lang(Lang.NTRIPLES)
      .build()
      .parse(model)
    model
  }

  def lexEntry(ler: Resource, lsr: Resource, cfr: Resource)(model: Model) = {
    ler.addProperty(RDF.`type`, model.createResource(OntolexLexicalEntry))
    ler.addProperty(model.createProperty(OntolexSense), lsr)
    ler.addProperty(model.createProperty(OntolexCanonicalForm), cfr)
    model
  }

  def synEntry(wordLs: Resource, synsLs: Seq[Resource], link: String)(model: Model) = {
    wordLs.addProperty(RDF.`type`, model.createResource(OntolexLexicalSense))
    synsLs.foreach(se => wordLs.addProperty(model.createProperty(LexInfoSyn), se))
    wordLs.addProperty(model.createProperty(OntolexReference), model.createResource(link))
    model
  }

  private def link(lang: String, sn: String, value: String): String = {
    val va = value.replaceAll("\\s", "_")
    val re = LexPrefix + s"$lang/${sn}_"
    val full = re + va
    //todo here we need to properly normalize instead of replacing
    val iri = IRIFactory.iriImplementation().create(full)
    if (iri.hasViolation(true)) {
      val errs = value
        .toSet
        .filter(c => IRIFactory.iriImplementation()
          .create(c.toString)
          .hasViolation(true)
        )
      re + errs.foldLeft(value) {
        case (s, c) => s.replaceAllLiterally(c.toString, URLEncoder.encode(c.toString, "UTF-8"))
      }
    } else {
      iri.toString
    }
  }

}
