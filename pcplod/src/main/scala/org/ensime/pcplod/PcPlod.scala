// Copyright: 2010 - 2016 Rory Graves, Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.pcplod

import java.io.{File, InputStream}
import java.util.regex.Pattern

import scala.reflect.internal.util.BatchSourceFile

object PcPlod {
  private def splitProp(name: String): List[String] =
    sys.props.get(name).toList.flatMap(_.split(","))

  def apply(): PcPlod = new PcPlod(
    splitProp("pcplod.classpath").map(new File(_)),
    splitProp("pcplod.settings")
  )

  private val TokenPattern = Pattern.compile("@([a-zA-Z0-9_]+)@")
  // returns the cleaned file and the map of symbol names to locations
  def parseNoddy(contents: String): (String, Map[String, Int]) = {
    val matcher = TokenPattern.matcher(contents)

    var cleaned = new java.lang.StringBuilder // iteratively cleaned up contents
    var tracked = 0 // where in the contents the cleaned has parsed to

    var symbols = Map.empty[String, Int]

    while (matcher.find()) {
      cleaned.append(contents.substring(tracked, matcher.start))
      tracked = matcher.end
      symbols += matcher.group(1) -> (cleaned.length - 1)
    }
    cleaned.append(contents.substring(tracked))

    (cleaned.toString, symbols)
  }
}

class PcPlod(
  classpath: List[File],
  options: List[String]
) {

  case class FileInfo(path: String, contents: String, tokenLocations: Map[String, Int], f: BatchSourceFile)

  private var files: Map[String, FileInfo] = Map.empty
  val (pc, reporter) = PoshPresentationCompiler.create(classpath, options)

  /**
   * Load a Scala file into the PC - the file is a resource location
   * N.b. the file contains tags (surrounded by @, to denote interesting locations in the code, these are stripped out
   * here.
   *
   * @param res A resource file containing the contents
   */
  def loadScala(res: String): Unit = {
    val path = if (res.startsWith("/")) res else s"/$res"
    val stream: InputStream = getClass.getResourceAsStream(path)
    require(stream != null, s"Scala file $res not found as resource")
    val rawInputStream = scala.io.Source.fromInputStream(stream)

    val rawContents = try {
      rawInputStream.getLines.mkString("\n")
    } finally {
      rawInputStream.close()
    }
    val (contents, symbols) = PcPlod.parseNoddy(rawContents)
    val f = pc.loadFile(res, contents)
    val fileInfo = FileInfo(res, contents, symbols, f)
    files += res -> fileInfo
  }

  def compilerWarnings: List[PcMessage] = {
    import reporter.{INFO, WARNING, ERROR}

    reporter.infos.map { info =>
      val severity = info.severity match {
        case INFO =>
          PcMessageSeverity.Info
        case WARNING =>
          PcMessageSeverity.Warning
        case ERROR =>
          PcMessageSeverity.Error
      }
      PcMessage(info.pos.source.file.toString, severity, info.msg)
    }.toList
  }

  def unloadScala(res: String): Unit = {
    pc.unloadFile(res)
    files -= res
  }

  def symbolAtPoint(res: String, p: Point): Option[String] = {
    // retrieve and use the FileInfo for the loaded file.
    files.get(res) match {
      case Some(fi) =>
        val idx = positionOffsetGivenPoint(res, fi, p)
        pc.askSymbolInfoAt(fi.f, idx)
      case None =>
        throw new IllegalArgumentException(s"res $res not loaded in PC")
    }
  }

  def typeAtPoint(res: String, p: Point): Option[String] = {
    // retrieve and use the FileInfo for the loaded file.
    files.get(res) match {
      case Some(fi) =>
        val idx = positionOffsetGivenPoint(res, fi, p)
        pc.askTypeAt(fi.f, idx)
      case None =>
        throw new IllegalArgumentException(s"res $res not loaded in PC")
    }
  }

  def positionOffsetGivenPoint(res: String, fi: FileInfo, p: Point): Int = {
    p match {
      case NoddyPoint(symbol) =>
        val rawName = symbol.name
        fi.tokenLocations.get(rawName) match {
          case Some(filePos) =>
            filePos
          case None =>
            throw new IllegalArgumentException(s"Token $symbol not found in $res")
        }
      case PositionPoint(filePos) =>
        filePos
      case LineColumnPoint(line, col) =>
        PCPlodUtil.calcPosForLineCol(res, line, col)
    }
  }

  def messages: List[PcMessage] = {
    compilerWarnings
  }

}

object MrPlod {
  def apply(res: String): MrPlod = {
    new MrPlod(res, PcPlod())
  }
}

class MrPlod(
  val res: String,
  val pc: PcPlod
) {
  // lazy init means tests will fail in the user's code, not in framework code
  var initialised = false

  private def init(): Unit = if (!initialised) {
    initialised = true
    pc.loadScala(res)
  }

  def symbolAtPoint(p: Point): Option[String] = { init(); pc.symbolAtPoint(res, p) }

  def typeAtPoint(p: Point): Option[String] = { init(); pc.typeAtPoint(res, p) }

  def messages: List[PcMessage] = { init(); pc.messages }
}
