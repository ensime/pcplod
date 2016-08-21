// Copyright: 2010 - 2016 Rory Graves, Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.pcplod.internal

import org.ensime.pcplod.{Point, PositionPoint}

import scala.reflect.internal.util.{BatchSourceFile, OffsetPosition, RangePosition, SourceFile}
import scala.reflect.io.VirtualFile
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.{CompilerControl, Global}
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.{Reporter, StoreReporter}
import scala.tools.nsc.util.FailedInterrupt

trait RichishCompilerControl extends CompilerControl {
  self: RichishPresentationCompiler =>

  //  def charset: Charset = Charset.forName(settings.encoding.value)

  def askOption[A](op: => A): Option[A] = {
    try {
      Some(ask(() => op))
    } catch {
      case fi: FailedInterrupt =>
        fi.getCause match {
          case e: InterruptedException =>
            Thread.currentThread().interrupt()
            println("interrupted exception in askOption:" + e)
            None
          case e =>
            println("Error during askOption:" + e)
            None
        }
      case e: Throwable =>
        println("Error during askOption:" + e)
        None
    }
  }

  private def typeOfTree(t: Tree): Option[Type] = {
    val tree = t match {
      case Select(qualifier, name) if t.tpe == ErrorType =>
        qualifier
      case t: ImplDef if t.impl != null =>
        t.impl
      case t: ValOrDefDef if t.tpt != null =>
        t.tpt
      case t: ValOrDefDef if t.rhs != null =>
        t.rhs
      case otherTree =>
        otherTree
    }

    Option(tree.tpe)
  }

  protected def typeAt(p: Position): Option[Type] = {
    wrapTypedTreeAt(p) match {
      case Import(_, _) => symbolAt(p).map(_.tpe)
      case tree         => typeOfTree(tree)
    }
  }

  //  def askDocSignatureAtPoint(p: Position): Option[DocSigPair] =
  //    askOption {
  //      symbolAt(p).orElse(typeAt(p).map(_.typeSymbol)).flatMap(docSignature(_, Some(p)))
  //    }.flatten
  //
  //  def askDocSignatureForSymbol(typeFullName: String, memberName: Option[String],
  //                               signatureString: Option[String]): Option[DocSigPair] =
  //    askOption {
  //      symbolMemberByName(
  //        typeFullName, memberName, signatureString
  //      ).flatMap(docSignature(_, None))
  //    }.flatten
  //

  //  def askSymbolByName(fqn: String, memberName: Option[String], signatureString: Option[String]): Option[SymbolInfo] =
  //    askOption(symbolMemberByName(fqn, memberName, signatureString).map(SymbolInfo(_))).flatten
  //
  //  def askTypeInfoAt(p: Position): Option[TypeInfo] =
  //    askOption(typeAt(p).map(TypeInfo(_, PosNeededYes))).flatten
  //
  //  def askTypeInfoById(id: Int): Option[TypeInfo] =
  //    askOption(typeById(id).map(TypeInfo(_, PosNeededYes))).flatten
  //
  //  def askTypeInfoByName(name: String): Option[TypeInfo] =
  //    askOption(typeByName(name).map(TypeInfo(_, PosNeededYes))).flatten
  //
  //  def askTypeInfoByNameAt(name: String, p: Position): Option[TypeInfo] = {
  //    val nameSegs = name.split("\\.")
  //    val firstName: String = nameSegs.head
  //    val x = new Response[List[Member]]()
  //    askScopeCompletion(p, x)
  //    (for (
  //      members <- x.get.left.toOption;
  //      infos <- askOption {
  //        val roots = filterMembersByPrefix(
  //          members, firstName, matchEntire = true, caseSens = true
  //        ).map { _.sym }
  //        val restOfPath = nameSegs.drop(1).mkString(".")
  //        val syms = roots.flatMap { symbolByName(restOfPath, _) }
  //        syms.find(_.tpe != NoType).map { sym => TypeInfo(sym.tpe) }
  //      }
  //    ) yield infos).flatten
  //  }
  //
  //  def askCallCompletionInfoById(id: Int): Option[CallCompletionInfo] =
  //    askOption(typeById(id).map(CallCompletionInfo(_))).flatten
  //
  //  def askPackageByPath(path: String): Option[PackageInfo] =
  //    askOption(PackageInfo.fromPath(path))

  def askReloadFile(f: SourceFile): Unit = {
    askReloadFiles(List(f))
  }

  def askReloadFiles(files: Iterable[SourceFile]): Either[Unit, Throwable] = {
    val x = new Response[Unit]()
    askReload(files.toList, x)
    x.get
  }

  //  def askLoadedTyped(f: SourceFile): Either[Tree, Throwable] = {
  //    val x = new Response[Tree]()
  //    askLoadedTyped(f, true, x)
  //    x.get
  //  }
  //
  //    def askUnloadAllFiles(): Unit = askOption(unloadAllFiles())
  def askRemoveFile(s: SourceFile): Unit = askOption(removeUnitOf(s))
  //  def askRemoveAllDeleted(): Option[Unit] = askOption(removeAllDeleted())
  //
  //  def askRemoveDeleted(f: File) = askOption(removeDeleted(AbstractFile.getFile(f)))
  //
  //  def askReloadAllFiles() = {
  //    val all = {
  //      for {
  //        file <- config.scalaSourceFiles
  //        source = getSourceFile(file.getAbsolutePath)
  //      } yield source
  //    }.toSet ++ activeUnits().map(_.source)
  //    askReloadFiles(all)
  //  }
  //
  //  def loadedFiles: List[SourceFile] = activeUnits().map(_.source)

  //  def askReloadExistingFiles() =
  //    askReloadFiles(loadedFiles)
  //
  //  def askInspectTypeById(id: Int): Option[TypeInspectInfo] =
  //    askOption(typeById(id).map(inspectType)).flatten
  //
  //  def askInspectTypeAt(p: Position): Option[TypeInspectInfo] =
  //    askOption(inspectTypeAt(p)).flatten
  //
  //  def askInspectTypeByName(name: String): Option[TypeInspectInfo] =
  //    askOption(typeByName(name).map(inspectType)).flatten
  //
  //  def askCompletePackageMember(path: String, prefix: String): List[CompletionInfo] =
  //    askOption(completePackageMember(path, prefix)).getOrElse(List.empty)
  //
  //  def askCompletionsAt(p: Position, maxResults: Int, caseSens: Boolean): CompletionInfoList =
  //    completionsAt(p, maxResults, caseSens)
  //
  //  def askReloadAndTypeFiles(files: Iterable[SourceFile]) =
  //    askOption(reloadAndTypeFiles(files))
  //
  //  def askUsesOfSymAtPoint(p: Position): List[RangePosition] =
  //    askOption(usesOfSymbolAtPoint(p).toList).getOrElse(List.empty)
  //
  //  // force the full path of Set because nsc appears to have a conflicting Set....
  //  def askSymbolDesignationsInRegion(p: RangePosition, tpes: List[SourceSymbol]): SymbolDesignations =
  //  askOption(
  //    new SemanticHighlighting(this).symbolDesignationsInRegion(p, tpes)
  //  ).getOrElse(SymbolDesignations(new File("."), List.empty))
  //
  //  def askImplicitInfoInRegion(p: Position): ImplicitInfos =
  //    ImplicitInfos(
  //      askOption(
  //        new ImplicitAnalyzer(this).implicitDetails(p)
  //      ).getOrElse(List.empty)
  //    )
  //
  //  def askClearTypeCache(): Unit = clearTypeCache()

  //  def askNotifyWhenReady(): Unit = ask(setNotifyWhenReady)
  //
  def createSourceFile(path: String) = getSourceFile(path)
  def createSourceFile(file: AbstractFile) = getSourceFile(file)
  //    def createSourceFile(file: SourceFileInfo) = file match {
  //    case SourceFileInfo(f, None, None) => getSourceFile(f.canon.getPath)
  //    case SourceFileInfo(f, Some(contents), None) => new BatchSourceFile(AbstractFile.getFile(f.canon.getPath), contents)
  //    case SourceFileInfo(f, None, Some(contentsIn)) =>
  //      val contents = FileUtils.readFile(contentsIn, charset) match {
  //        case Right(contentStr) => contentStr
  //        case Left(e) => throw e
  //      }
  //      new BatchSourceFile(AbstractFile.getFile(f.canon), contents)
  //  }
  //  def findSourceFile(path: String): Option[SourceFile] = allSources.find(
  //    _.file.path == path
  //  )

  //  def askLinkPos(sym: Symbol, path: AbstractFile): Option[Position] =
  //  askOption(linkPos(sym, createSourceFile(path)))
  //}
}

object RichishPresentationCompiler {
  def create(scalaLibrary: String, compileClasspath: String): RichishPresentationCompiler = {
    val settings = new Settings(s => println("PC: $s"))
    settings.YpresentationDebug.value = true
    settings.YpresentationVerbose.value = true
    settings.verbose.value = true
    //settings.usejavacp.value = true
    settings.bootclasspath.append(scalaLibrary)
    settings.classpath.value = compileClasspath

    val reporter = new StoreReporter()

    new RichishPresentationCompiler(settings, reporter)
  }
}

class RichishPresentationCompiler(
  override val settings: Settings,
  val richReporter: Reporter
) extends Global(settings, richReporter) with RichishCompilerControl {

  def loadFile(path: String, contents: String): BatchSourceFile = {
    val f = new BatchSourceFile(path, contents)
    askReloadFile(f)
    f
  }

  def loadFile(path: String): Unit = {
    val f = createSourceFile(path)

    askReloadFile(f)
  }

  def unloadFile(path: String): Unit = {
    val f = createSourceFile(path)
    askRemoveFile(f)
  }

  /*
  * The following functions wrap up operations that interact with
  * the presentation compiler. The wrapping just helps with the
  * create response / compute / get result pattern.
  *
  * These units of work should return `Future[T]`.
  */
  def wrap[A](compute: Response[A] => Unit, handle: Throwable => A): A = {
    val result = new Response[A]
    compute(result)
    result.get.fold(o => o, handle)
  }

  protected def symbolAt(pos: Position): Option[Symbol] = {
    val tree = wrapTypedTreeAt(pos)
    // This code taken mostly verbatim from Scala IDE sources. Licensed
    // under SCALA LICENSE.
    val wannabes =
      tree match {
        case Import(expr, selectors) =>
          if (expr.pos.includes(pos)) {
            @annotation.tailrec
            def locate(p: Position, inExpr: Tree): Symbol = inExpr match {
              case Select(qualifier, name) =>
                if (qualifier.pos.includes(p)) locate(p, qualifier)
                else inExpr.symbol
              case tree => tree.symbol
            }
            List(locate(pos, expr))
          } else {
            selectors.filter(_.namePos <= pos.point).sortBy(_.namePos).lastOption map { sel =>
              val tpe = stabilizedType(expr)
              List(tpe.member(sel.name), tpe.member(sel.name.toTypeName))
            } getOrElse Nil
          }
        case Annotated(atp, _) =>
          List(atp.symbol)
        case st: SymTree =>
          println("DEBUG: using symbol of " + tree.getClass + " tree")
          List(tree.symbol)
        case _ =>
          println("WARN symbolAt for " + tree.getClass + ": " + tree)
          Nil
      }
    wannabes.find(_.exists)
  }

  def wrapTypedTreeAt(position: Position): Tree =
    wrap[Tree](r => AskTypeAtItem(position, r).apply(), t => throw t)

  def askSymbolInfoAt(f: BatchSourceFile, idx: Int): Option[String] = {
    val pos = new OffsetPosition(f, idx)
    askOption(symbolAt(pos).map {
      (x: Symbol) =>
        // For debugging
        println("-------------------")
        println(x.fullName)
        println(x.toString())
        println(x.decodedName)
        println(x.fullNameString)
        println(x.encodedName)
        x.fullName
    }).flatten
  }

  def askTypeAt(f: BatchSourceFile, idx: Int): Option[String] = {
    val pos = new OffsetPosition(f, idx)
    askOption(typeAt(pos).map {
      (x: Type) =>
        //        println("=================")
        //        println(x.safeToString)
        //        println(x.toString())
        //        println(x.toLongString)
        //        println(x.directObjectString)
        x.safeToString
    }).flatten
  }
}
