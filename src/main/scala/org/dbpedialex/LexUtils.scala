package org.dbpedialex

import java.io.ByteArrayOutputStream
import java.net.{URLDecoder, URLEncoder}
import java.nio.file.Paths
import java.util.UUID

import org.apache.jena.iri.IRIFactory
import org.apache.jena.rdf.model.{Model, ModelFactory, Resource}
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParserBuilder}
import org.apache.jena.vocabulary.RDF
import org.apache.spark.rdd.RDD
import org.apache.jena.graph.{Triple => JenaTriple}
import org.apache.hadoop.fs.{FileSystem, FileUtil, Path}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.Try

object LexUtils {

  private val InsideBracketsPattern = "\\((.+?)\\)".r

  val LexPrefix = "https://dbpedia.org/lex/"
  val OntolexPrefix = "http://www.w3.org/ns/lemon/ontolex#"
  val LexInfoPrefix = "http://www.lexinfo.net/ontology/2.0/lexinfo#"
  val DctTermsPrefix = "http://purl.org/dc/terms/"
  val FracPrefix = "http://www.w3.org/nl/lemon/frac#"

  val OntolexCanonicalForm = OntolexPrefix + "canonicalForm"
  val OntolexSense = OntolexPrefix + "sense"
  val OntolexLexicalEntry = OntolexPrefix + "LexicalEntry"
  val OntolexLexicalSense = OntolexPrefix + "LexicalSense"
  val OntolexWrittenRep = OntolexPrefix + "writtenRep"
  val OntolexReference = OntolexPrefix + "reference"
  val LexInfoSyn = LexInfoPrefix + "synonym"
  val DctSubject = DctTermsPrefix + "subject"
  val FracFrequency = FracPrefix + "frequency"
  val FracCorpusFrequency = FracPrefix + "CorpusFrequency"
  val FracCorpus = FracPrefix + "corpus"
  private val FracCorpusValue = "https://databus.dbpedia.org/dbpedia/text/nif-text-links/"

  def synonyms(senseLink: String, wordsNFreqs: Seq[(String, Int)], lang: String, replaceBrackets: Boolean, doFiltering: Boolean)(model: Model) = {
    val words = if (doFiltering) filterUnreliable(wordsNFreqs) else wordsNFreqs
    val senses = words.map(w => {
      val p = (w._1, model.createResource(ls(lang, w._1, 1)))
      if (replaceBrackets) {
        LexUtils.extractFromFirstBrackets(w._1)
          .foreach(s => p._2.addProperty(model.createProperty(DctSubject), model.createLiteral(s, lang)))
      }
      if (w._2 > 0) addFreqToSense(p._2, w._2)(model)
      p
    }).toMap
    words.map(_._1).foreach(w => {
      val s = if (replaceBrackets) LexUtils.replaceBrackets(w) else w
      val ler = model.createResource(le(lang, s))
      val lsr = senses(w)
      val cfr = model.createResource(cf(lang, s))
      lexEntry(ler, lsr, cfr)(model)
      cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(s, lang))
      synEntry(lsr, (senses - w).values.toSeq, senseLink)(model)
    })
    model
  }

  def polysemi(word: String, links: Seq[(String, Int)], lang: String, doFiltering: Boolean)(model: Model) = {
    val senses = (if (doFiltering) filterUnreliable(links) else links)
      .zipWithIndex.map(p => {
      val r = model.createResource(ls(lang, word, p._2 + 1))
      r.addProperty(RDF.`type`, model.createResource(OntolexLexicalSense))
      r.addProperty(model.createProperty(OntolexReference), model.createResource(p._1._1))
      addFreqToSense(r, p._1._2)(model)
    })

    val ler = model.createResource(le(lang, word))
    val cfr = model.createResource(cf(lang, word))

    ler.addProperty(RDF.`type`, model.createResource(OntolexLexicalEntry))
    ler.addProperty(model.createProperty(OntolexCanonicalForm), cfr)
    cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(word, lang))
    senses.foreach(r => ler.addProperty(model.createProperty(OntolexSense), r))
    model
  }

  def polysemiDisambig(word: (String, String), labsNlinks: Seq[(Option[String], String)], lang: String)(model: Model) = {
    val wordsNlinks = labsNlinks.map(s => (s._1.getOrElse(LexUtils.extractLabelFromIri(s._2)), s._2))
    val senses = wordsNlinks
      .map(_._1)
      .map(w => {
        val p = (w, model.createResource(ls(lang, w, 1)))
        LexUtils.extractFromFirstBrackets(w)
          .foreach(s => p._2.addProperty(model.createProperty(DctSubject), model.createLiteral(s, lang)))
        p
      }).toMap
    val ler = model.createResource(le(lang, LexUtils.replaceBrackets(word._1)))
    val cfr = model.createResource(cf(lang, LexUtils.replaceBrackets(word._1)))

    ler.addProperty(RDF.`type`, model.createResource(OntolexLexicalEntry))
    ler.addProperty(model.createProperty(OntolexCanonicalForm), cfr)
    senses.values.foreach(s => ler.addProperty(model.createProperty(OntolexSense), s))
    cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(LexUtils.replaceBrackets(word._1), lang))

    wordsNlinks.foreach(p => {
      val ler = model.createResource(le(lang, LexUtils.replaceBrackets(p._1)))
      val cfr = model.createResource(cf(lang, LexUtils.replaceBrackets(p._1)))
      val lsr = senses(p._1)
      LexUtils.lexEntry(ler, lsr, cfr)(model)
      cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(LexUtils.replaceBrackets(word._1), lang))

      lsr.addProperty(RDF.`type`, model.createResource(OntolexLexicalSense))
      lsr.addProperty(model.createProperty(OntolexReference), model.createResource(p._2))
    })

    model
  }

  def filterUnreliable(wordsNFreq: Seq[(String, Int)]): Seq[(String, Int)] = {
    val size = wordsNFreq.size
    val sumFreq = wordsNFreq.map(_._2).sum
    if (size > 0 && sumFreq > 0) {
      val averageFreq = sumFreq / size
      wordsNFreq.filter(_._2 >= averageFreq)
    } else {
      wordsNFreq
    }
  }

  def extractLabelFromIri(iri: String): String =
    URLDecoder
      .decode(
        Paths.get(
          IRIFactory.iriImplementation()
            .create(iri)
            .getRawPath
        ).getFileName.toString,
        "UTF-8")
      .replace('_', ' ')

  def replaceBrackets(s: String): String =
    s.replaceAll(InsideBracketsPattern.toString(), "").trim

  def extractFromFirstBrackets(s: String): Option[String] =
    InsideBracketsPattern.findFirstMatchIn(s).map(_.group(1))

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

  def addFreqToSense(sense: Resource, freq: Int)(model: Model): Resource = {
    val frr = model.createResource()
    sense.addProperty(model.createProperty(FracFrequency), frr)
    frr.addProperty(RDF.`type`, model.createResource(FracCorpusFrequency))
    frr.addProperty(model.createProperty(FracCorpus), model.createResource(FracCorpusValue))
    frr.addProperty(RDF.value, model.createTypedLiteral(Integer.valueOf(freq)))
    sense
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

  def labelTripleToModel(triple: JenaTriple, lang: String): Model = {
    val word = triple.getObject.getLiteralValue.toString
    val model = ModelFactory.createDefaultModel()
    val ler = model.createResource(le(lang, LexUtils.replaceBrackets(word)))
    val cfr = model.createResource(cf(lang, LexUtils.replaceBrackets(word)))
    val lsr = model.createResource(ls(lang, word, 1))
    cfr.addProperty(model.createProperty(OntolexWrittenRep), model.createLiteral(LexUtils.replaceBrackets(word), lang))
    lsr.addProperty(RDF.`type`, model.createResource(OntolexLexicalSense))
    lsr.addProperty(model.createProperty(OntolexReference), model.createResource(triple.getSubject.getURI))
    LexUtils.extractFromFirstBrackets(word)
      .foreach(s => lsr.addProperty(model.createProperty(DctSubject), model.createLiteral(s, lang)))
    lexEntry(ler, lsr, cfr)(model)
  }

  def triplesToRawTtlString(tpls: Seq[JenaTriple], lang: String): String = {
    val model = ModelFactory.createDefaultModel()
    tpls.foreach(model.getGraph.add)
    model.setNsPrefix("ontolex", LexUtils.OntolexPrefix)
    model.setNsPrefix("lexinfo", LexUtils.LexInfoPrefix)
    model.setNsPrefix("lex", LexUtils.LexPrefix + lang + "/")
    model.setNsPrefix("dct", LexUtils.DctTermsPrefix)
    model.setNsPrefix("frac", LexUtils.FracPrefix)
    val out = new ByteArrayOutputStream()
    RDFDataMgr.write(out, model.getGraph, Lang.TTL)
    model.close()
    out.toString
  }

  private[dbpedialex] def link(lang: String, sn: String, value: String): String = {
    val va = value.replaceAll("\\s", "_")
    val re = LexPrefix + s"$lang/${sn}_"
    val full = re + va
    //todo here we need to properly normalize instead of replacing
    val iri = IRIFactory.iriImplementation().create(full)
    if (iri.hasViolation(true)) {
      val errs = va
        .toSet
        .filter(c => IRIFactory.iriImplementation()
          .create(c.toString)
          .hasViolation(true)
        )
      re + errs.foldLeft(va) {
        case (s, c) => s.replaceAllLiterally(c.toString, URLEncoder.encode(c.toString, "UTF-8"))
      }
    } else {
      iri.toString
    }
  }

  object Spark {

    implicit def rddStrToTripleExtr(rdd: RDD[String]) = new TripleExtractor(rdd)

    implicit def rddTripleToTripleProc(rdd: RDD[JenaTriple]) = new TripleProcessor(rdd)

    implicit def rddTSeqToTripleSeqProc(rdd: RDD[Seq[JenaTriple]]) = new TripleSeqProcessor(rdd)

    implicit def rddToAnyProc[T](rdd: RDD[T]) = new AnyProcessor[T](rdd)

    implicit def rddToModelProcessor(rdd: RDD[Model]) = new ModelProcessor(rdd)

    class TripleExtractor(rdd: RDD[String]) {

      def extractTriples(filter: Option[String => Boolean]): RDD[JenaTriple] = {
        val tmp = rdd.map(_.trim)
          .filter(_.nonEmpty)

        filter
          .map(f => tmp.filter(f))
          .getOrElse(tmp)
          //todo: here we can improve by parsing batches of strings
          .flatMap(s => Try(parseString(s)).toOption)
          .flatMap(m => Try {
            val trpl = m.getGraph.find().next()
            m.close()
            trpl
          }.toOption)
      }


      def saveToFile(fn: String): Unit = {
        val tmp_fld = UUID.randomUUID().toString
        val out_pa = Paths.get(fn)
        val out = new Path(out_pa.toAbsolutePath.toFile.getAbsolutePath)
        val tmp_out = out_pa.getParent.resolve(tmp_fld)
        rdd
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

    class ModelProcessor(rdd: RDD[Model]) {

      def extractTriplesSeq: RDD[Seq[JenaTriple]] =
        extractTriples(rdd)

      def extractSingleTripes: RDD[JenaTriple] =
        extractTriples(rdd)
          .flatMap(s => s)

      private def extractTriples(rdd: RDD[Model]) = {
        rdd.map(m => {
          val re = m.getGraph.find().asScala.toSeq
          m.close()
          re
        })
      }

    }

    class AnyProcessor[T](rdd: RDD[T]) {

      def printFirstN(n: Int): Unit =
        rdd
          .take(n)
          .foreach(t => println(t.toString))

      def limit(n: Int)(implicit tag: ClassTag[T]): RDD[T] =
        if (n > 0) {
          rdd.sparkContext
            .makeRDD(rdd.take(n), 1)
        } else {
          rdd
        }

    }

    class TripleProcessor(rdd: RDD[JenaTriple]) {

      def saveToNTriplesFile(filename: String): Unit = {
        rdd
          .map(t => {
            val m = ModelFactory.createDefaultModel()
            m.getGraph.add(t)
            val out = new ByteArrayOutputStream()
            RDFDataMgr.write(out, m.getGraph, Lang.NTRIPLES)
            m.close()
            out.toString.replaceAllLiterally("\n", "").replaceAllLiterally("\r", "")
          })
          .saveToFile(filename)
      }
    }

    class TripleSeqProcessor(rdd: RDD[Seq[JenaTriple]]) {

      def printOne(lang: String, lbl: Option[String] = None) =
        rdd
          .map(LexUtils.triplesToRawTtlString(_, lang))
          .take(1)
          .foreach(m => {
            println(s"Model for $lbl list of triples: ")
            println(m)
          })

    }

  }

}
