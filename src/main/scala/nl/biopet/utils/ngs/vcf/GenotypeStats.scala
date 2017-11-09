package nl.biopet.utils.ngs.vcf

import java.io.{File, PrintWriter}

import htsjdk.variant.variantcontext.{Genotype, VariantContext}
import htsjdk.variant.vcf.VCFHeader
import nl.biopet.utils.Counts
import nl.biopet.utils.ngs.vcf

import scala.collection.JavaConversions._
import scala.language.implicitConversions

/**
  * This class will collect general stats from vcf records
  */
class GenotypeStats(header: VCFHeader) extends Serializable {

  /** This map binds names to sample index */
  val samples: Map[String, Int] =
    header.getSampleNameToOffset.toMap.map(x => x._1 -> x._2.toInt)

  /** Counts object to store results */
  protected[GenotypeStats] val counts
    : Map[Int, Counts[vcf.GenotypeStats.Value]] =
    samples.values
      .map(
        _ -> new Counts[GenotypeStats.Value](
          GenotypeStats.values.map(_ -> 0L).toMap))
      .toMap

  /** Adding a [[VariantContext]] to the counts */
  def addRecord(record: VariantContext): Unit = {
    for ((name, idx) <- samples) {
      val genotype = record.getGenotype(idx)
      GenotypeStats.values.filter(_.method(genotype)).foreach(counts(idx).add)
    }
  }

  /** Write results to a file */
  def writeToTsv(file: File): Unit = {
    val writer = new PrintWriter(file)
    val sorted = samples.toList.sortBy(_._1)
    writer.println(sorted.map(_._1).mkString("Sample\t", "\t", ""))
    for (method <- GenotypeStats.values) {
      writer.print(method + "\t")
      writer.println(
        sorted.map(x => counts(x._2).get(method).getOrElse(0L)).mkString("\t"))
    }
    writer.close()
  }

  /** Convert to immutable Map */
  def toMap: Map[String, Map[GenotypeStats.Value, Long]] =
    samples.map(x => x._1 -> counts(x._2).countsMap)

  /** Combine multiple classes into 1 */
  def +=(other: GenotypeStats): GenotypeStats = {
    require(this.samples == other.samples)
    other.counts.foreach(x => this.counts(x._1) += x._2)
    this
  }
}

/** Enum to store methods */
object GenotypeStats extends Enumeration {
  protected case class Val(method: Genotype => Boolean) extends super.Val
  implicit def valueToVal(x: Value): Val = x.asInstanceOf[Val]

  val Total = Val(_ => true)
  val Het = Val(_.isHet)
  val HetNonRef = Val(_.isHetNonRef)
  val Hom = Val(_.isHom)
  val HomRef = Val(_.isHomRef)
  val HomVar = Val(_.isHomVar)
  val Mixed = Val(_.isMixed)
  val NoCall = Val(_.isNoCall)
  val NonInformative = Val(_.isNonInformative)
  val Available = Val(_.isAvailable)
  val Called = Val(_.isCalled)
  val Filtered = Val(_.isFiltered)
  val Variant = Val(g => g.isHetNonRef || g.isHet || g.isHomVar)
}
