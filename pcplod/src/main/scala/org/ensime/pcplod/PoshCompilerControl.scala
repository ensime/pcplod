// Copyright: 2010 - 2016 Rory Graves, Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.pcplod

import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.interactive.CompilerControl
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.FailedInterrupt

private[pcplod] trait PoshCompilerControl extends CompilerControl {
  self: PoshPresentationCompiler =>

  def askOption[A](op: => A): Option[A] = {
    try {
      Some(ask(() => op))
    } catch {
      case fi: FailedInterrupt =>
        fi.getCause match {
          case e: InterruptedException =>
            debugLog("interrupted exception in askOption:" + e)
            None
          case e =>
            debugLog("Error during askOption:" + e)
            None
        }
      case e: Throwable =>
        debugLog("Error during askOption:" + e)
        None
    }
  }

  private def typeOfTree(t: Tree): Option[Type] = {
    val tree = t match {
      case Select(qualifier, name) if t.tpe == ErrorType => qualifier
      case t: ImplDef if t.impl != null                  => t.impl
      case t: ValOrDefDef if t.tpt != null               => t.tpt
      case t: ValOrDefDef if t.rhs != null               => t.rhs
      case otherTree                                     => otherTree
    }

    Option(tree.tpe)
  }

  protected def typeAt(p: Position): Option[Type] = {
    wrapTypedTreeAt(p) match {
      case Import(_, _) => symbolAt(p).map(_.tpe)
      case tree         => typeOfTree(tree)
    }
  }

  def askReloadFile(f: SourceFile): Unit = {
    askReloadFiles(List(f))
  }

  def askReloadFiles(files: Iterable[SourceFile]): Unit = {
    val x = new Response[Unit]()
    askReload(files.toList, x)
    x.get match {
      case Left(_)   =>
      case Right(ex) => throw ex
    }
  }

  def askLoadedTyped(f: SourceFile): Tree = {
    val x = new Response[Tree]()
    askLoadedTyped(f, keepLoaded = true, x)
    x.get match {
      case Left(tree) => tree
      case Right(ex)  => throw ex
    }
  }

  def askRemoveFile(s: SourceFile): Unit = {
    val _ = askOption(removeUnitOf(s))
  }
  def createSourceFile(path: String) = getSourceFile(path)
  def createSourceFile(file: AbstractFile) = getSourceFile(file)
}
