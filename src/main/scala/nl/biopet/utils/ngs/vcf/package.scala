package nl.biopet.utils.ngs

import java.io.File

import htsjdk.variant.variantcontext.{Allele, Genotype, VariantContext}
import htsjdk.variant.vcf.{VCFFileReader, VCFHeader}
import nl.biopet.utils.conversions
import nl.biopet.utils.ngs.intervals.BedRecord

import java.util

import scala.collection.JavaConversions._

package object vcf {

  /**
    * Method will extend a allele till a new length
    * @param bases Allele
    * @param newSize New size of allele
    * @param fillWith Char to fill gap
    * @return
    */
  def fillAllele(bases: String, newSize: Int, fillWith: Char = '-'): String = {
    bases + Array.fill[Char](newSize - bases.length)(fillWith).mkString
  }

  /**
    * Stands for scalaListToJavaObjectArrayList
    * Convert a scala List[Any] to a java ArrayList[Object]. This is necessary for BCF conversions
    * As scala ints and floats cannot be directly cast to java objects (they aren't objects),
    * we need to box them.
    * For items not Int, Float or Object, we assume them to be strings (TODO: sane assumption?)
    * @param array scala List[Any]
    * @return converted java ArrayList[Object]
    */
  def scalaListToJavaObjectArrayList(
      array: List[Any]): util.ArrayList[Object] = {
    val out = new util.ArrayList[Object]()

    array.foreach {
      case x: Long => out.add(Long.box(x))
      case x: Int => out.add(Int.box(x))
      case x: Char => out.add(Char.box(x))
      case x: Byte => out.add(Byte.box(x))
      case x: Double => out.add(Double.box(x))
      case x: Float => out.add(Float.box(x))
      case x: Boolean => out.add(Boolean.box(x))
      case x: String => out.add(x)
      case x: Object => out.add(x)
      case x => out.add(x.toString)
    }
    out
  }

  //TODO: Add genotype comparing to this function
  def identicalVariantContext(var1: VariantContext,
                              var2: VariantContext): Boolean = {
    var1.getContig == var2.getContig &&
    var1.getStart == var2.getStart &&
    var1.getEnd == var2.getEnd &&
    var1.getAttributes == var2.getAttributes
  }

  /**
    * Return true if header is a block-type GVCF file
    * @param header header of Vcf file
    * @return boolean
    */
  def isBlockGVcf(header: VCFHeader): Boolean = {
    header.getMetaDataLine("GVCFBlock") != null
  }

  /**
    * Get sample IDs from vcf File
    * @param vcf File object pointing to vcf
    * @return list of strings with sample IDs
    */
  def getSampleIds(vcf: File): List[String] = {
    val reader = new VCFFileReader(vcf, false)
    val samples = reader.getFileHeader.getSampleNamesInOrder.toList
    reader.close()
    samples
  }

  def getVcfIndexFile(vcfFile: File): File = {
    val name = vcfFile.getAbsolutePath
    if (name.endsWith(".vcf")) new File(name + ".idx")
    else if (name.endsWith(".vcf.gz")) new File(name + ".tbi")
    else
      throw new IllegalArgumentException(
        s"File given is no vcf file: $vcfFile")
  }

  def vcfFileIsEmpty(file: File): Boolean = {
    val reader = new VCFFileReader(file, false)
    val hasNext = reader.iterator().hasNext
    reader.close()
    !hasNext
  }

  /**
    * Check whether genotype is of the form 0/.
    * @param genotype genotype
    * @return boolean
    */
  def isCompoundNoCall(genotype: Genotype): Boolean = {
    genotype.isCalled && genotype.getAlleles.exists(_.isNoCall) && genotype.getAlleles
      .exists(_.isReference)
  }

  /** Give back the number of alleles that overlap */
  def alleleOverlap(g1: List[Allele], g2: List[Allele], start: Int = 0): Int = {
    if (g1.isEmpty) start
    else {
      val found = g2.contains(g1.head)
      val g2tail = if (found) {
        val index = g2.indexOf(g1.head)
        g2.drop(index + 1) ++ g2.take(index)
      } else g2

      alleleOverlap(g1.tail, g2tail, if (found) start + 1 else start)
    }
  }

  /** Give back the number of alleles that overlap */
  def alleleIndexOverlap(g1: List[Int], g2: List[Int], start: Int = 0): Int = {
    if (g1.isEmpty) start
    else {
      val found = g2.contains(g1.head)
      val g2tail = if (found) {
        val index = g2.indexOf(g1.head)
        g2.drop(index + 1) ++ g2.take(index)
      } else g2

      alleleIndexOverlap(g1.tail, g2tail, if (found) start + 1 else start)
    }
  }

  /**
    * Read all records of a single regions
    * @param inputFile input vcf file
    * @param region Region to fetch
    * @return Vcf records
    */
  def loadRegion(inputFile: File, region: BedRecord): Seq[VariantContext] = {
    val reader = new VCFFileReader(inputFile, true)
    val records = loadRegion(reader, region)
    reader.close()
    records.toSeq
  }

  /**
    * Returns a iterator for records from region
    * @param reader reader to use
    * @param region Region to fetch
    * @return
    */
  def loadRegion(reader: VCFFileReader,
                 region: BedRecord): Iterator[VariantContext] = {
    val interval = region.toSamInterval
    reader.query(region.chr, interval.getStart, interval.getEnd)
  }

  /**
    * This method will return multiple region as a single iterator
    * @param inputFile input vcf file
    * @param regions regions to fetch
    * @return
    */
  def loadRegions(inputFile: File,
                  regions: Iterator[BedRecord]): Iterator[VariantContext] = {
    new Iterator[VariantContext] with AutoCloseable {
      private val reader = new VCFFileReader(inputFile, true)
      private val it = regions.flatMap(loadRegion(reader, _))

      def hasNext: Boolean = it.hasNext
      def next(): VariantContext = it.next()
      def close(): Unit = reader.close()
    }
  }

  implicit class BiopetVariantContext(record: VariantContext) {

    /**
      * Look up a list of doubles in the info fields
      * @param key Key to look up in the info fields
      * @param method methods to apply on list, default returns all values
      * @return
      */
    def getAttAsDouble(
        key: String,
        method: FieldMethod.Value = FieldMethod.All.asInstanceOf)
      : List[Double] = {
      val value =
        if (record.hasAttribute(key))
          conversions.anyToDoubleList(Option(record.getAttribute(key)))
        else Nil
      method.doubleMethod(value)
    }

    /**
      * Look up a list of Strings in the info fields
      * @param key Key to look up in the info fields
      * @param method methods to apply on list, default returns all values
      * @return
      */
    def getAttAsString(
        key: String,
        method: FieldMethod.Value = FieldMethod.All.asInstanceOf)
      : List[String] = {
      val value =
        if (record.hasAttribute(key))
          conversions.anyToStringList(Option(record.getAttribute(key)))
        else Nil
      method.stringMethod(value)
    }

    /**
      * Return longest allele of VariantContext.
      *
      * @return allele with most nucleotides
      */
    def getLongestAllele: Allele = {
      val alleles = record.getAlleles
      val longestAlleleId =
        alleles.map(_.getBases.length).zipWithIndex.maxBy(_._1)._2
      alleles(longestAlleleId)
    }
  }

  implicit class BiopetGenotype(genotype: Genotype) {

    /**
      * Look up a list of doubles in the genotype fields
      * @param key Key to look up in the genotype fields
      * @param method methods to apply on list, default returns all values
      * @return
      */
    def getAttAsDouble(
        key: String,
        method: FieldMethod.Value = FieldMethod.All.asInstanceOf)
      : List[Double] = {
      val value =
        if (genotype.hasAnyAttribute(key))
          conversions.anyToDoubleList(genotype.getAnyAttribute(key))
        else Nil
      method.doubleMethod(value)
    }

    /**
      * Look up a list of Strings in the genotype fields
      * @param key Key to look up in the genotype fields
      * @param method methods to apply on list, default returns all values
      * @return
      */
    def getAttAsString(
        key: String,
        method: FieldMethod.Value = FieldMethod.All.asInstanceOf)
      : List[String] = {
      val value =
        if (genotype.hasAnyAttribute(key))
          conversions.anyToStringList(genotype.getAnyAttribute(key))
        else Nil
      method.stringMethod(value)
    }

    /**
      * Check whether genotype has minimum genome Quality
      * @param minGQ minimum genome quality value
      * @return
      */
    def hasMinGenomeQuality(minGQ: Int): Boolean = {
      genotype.hasGQ && genotype.getGQ >= minGQ
    }
  }

}
