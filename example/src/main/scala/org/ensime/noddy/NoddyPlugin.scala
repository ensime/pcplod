// Copyright: 2010 - 2016 Rory Graves, Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.noddy

import scala.collection.breakOut
import scala.reflect.internal.ModifierFlags
import scala.reflect.internal.util._
import scala.tools.nsc._
import scala.tools.nsc.plugins._
import scala.tools.nsc.transform._

/**
 * A major source of bugs in macros and compiler plugins is failure to
 * preserve mutable information when transforming `global.Tree`
 * instances.
 *
 * Copying a `global.Tree` will result in the `pos`, `symbol` and
 * `tpe` fields being reset.
 *
 * The `global.treeCopy._` methods should be used instead of `copy` on
 * `global.Tree` implementations.
 *
 * Note that the `Modifiers.withAnnotations` method adds to existing
 * methods instead of replacing, so we provide a more cananocial form
 * here in `copyWithAnns`.
 *
 * http://docs.scala-lang.org/overviews/reflection/symbols-trees-types#trees
 */
trait WithPos { this: BackCompat =>
  val global: Global

  // Modifiers is an inner class, oh joy
  implicit class RichModifiers(t: global.Modifiers) {
    /** withAnnotations appears to be broken */
    def copyWithAnns(anns: List[global.Tree]): global.Modifiers =
      t.copy(annotations = anns).setPositions(t.positions)
  }

  implicit class RichTree[T <: global.Tree](t: T) {
    /** when generating a tree, use this to generate positions all the way down. */
    def withAllPos(pos: Position): T = {
      t.foreach { p =>
        val _ = p.setPos(new TransparentPosition(pos.source, pos.startOrCursor, pos.endOrCursor, pos.endOrCursor))
      }
      t
    }
  }
}

/**
 * Convenient way to tell if you are running in an interactive context.
 */
trait IsIde {
  val global: Global

  val isIde = global.isInstanceOf[tools.nsc.interactive.Global]
}

class NoddyPlugin(override val global: Global) extends Plugin {
  private val target = "noddy"
  override val description: String = s"Generates code when using an annotation named '$target'"
  override val name: String = "noddy"

  abstract class TransformingComponent(override val global: Global) extends PluginComponent
    with TypingTransformers with WithPos with IsIde with BackCompat {

    val Noddy = global.newTypeName(target)

    override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
      override def apply(unit: global.CompilationUnit): Unit = newTransformer(unit).transformUnit(unit)
    }
    override val runsAfter: List[String] = "parser" :: Nil
    override val runsBefore: List[String] = "namer" :: Nil

    def newTransformer(unit: global.CompilationUnit) = new TypingTransformer(unit) {
      override def transform(tree: global.Tree): global.Tree = {
        TransformingComponent.this.transform(super.transform(tree))
      }
    }

    def transform: global.Tree => global.Tree
  }

  private val NoddyParameters = new TransformingComponent(global) {
    override val phaseName: String = "noddy-params"
    import global._

    def noddyParameters(mods: global.Modifiers) = {
      mods.annotations.map {
        case ann @ Apply(Select(New(Ident(Noddy)), nme.CONSTRUCTOR), _) =>
          // NOTE: ignores all parameters, even bad ones
          treeCopy.Apply(
            ann,
            ann.fun,
            Literal(Constant(true)) :: Literal(Constant(true)) :: Literal(Constant(0)) :: Nil
          )

        case other => other
      }
    }

    override def transform = {
      case t: ModuleDef if t.mods.hasAnnotationNamed(Noddy) =>
        val annotations = noddyParameters(t.mods)
        treeCopy.ModuleDef(t, t.mods.copyWithAnns(annotations), t.name, t.impl)

      case t: ClassDef if t.mods.hasAnnotationNamed(Noddy) =>
        val annotations = noddyParameters(t.mods)
        treeCopy.ClassDef(t, t.mods.copyWithAnns(annotations), t.name, t.tparams, t.impl)

      case t => t
    }
  }

  private val NoddyCompanion = new TransformingComponent(global) {
    override val phaseName: String = "noddy-companion"
    import global._

    /** generates a zero-functionality companion for a class */
    def genCompanion(clazz: ClassDef): ModuleDef = ModuleDef(
      Modifiers(),
      clazz.name.companionName,
      Template(
        List(Select(Ident(nme.scala_), nme.AnyRef.toTypeName)),
        emptyValDef, // deprecated in 2.11, but replacement not in 2.10
        List(
          DefDef(
            Modifiers(),
            nme.CONSTRUCTOR,
            List(),
            List(List()),
            TypeTree(),
            Block(
              List(
                Apply(Select(Super(This(tpnme.EMPTY), tpnme.EMPTY), nme.CONSTRUCTOR), List())
              ),
              Literal(Constant(()))
            )
          )
        )
      )
    )

    def genLog: DefDef = DefDef(
      Modifiers(ModifierFlags.PRIVATE),
      newTermName("log"),
      Nil,
      Nil,
      Select(Select(Select(Ident(nme.java), newTermName("util")), newTermName("logging")), newTypeName("Logger")),
      Literal(Constant(null))
    )

    def genProperty(name: String, tpt: Tree): DefDef = DefDef(
      Modifiers(),
      newTermName(name),
      Nil,
      Nil,
      tpt,
      Literal(Constant(null))
    )

    def addMethods(template: Template, methods: List[DefDef]): Template = {
      val body = methods ::: template.body
      treeCopy.Template(template, template.parents, template.self, body)
    }

    /** adds a log method to a class */
    def updateClass(clazz: ClassDef): ClassDef = {
      val log = genLog.withAllPos(clazz.pos)
      val impl = addMethods(clazz.impl, log :: Nil)
      treeCopy.ClassDef(clazz, clazz.mods, clazz.name, clazz.tparams, impl)
    }

    /** adds a apply and log methods to a companion to a class */
    def updateCompanion(clazz: ClassDef, companion: ModuleDef): ModuleDef = {
      // not sure these can be re-used, so create fresh ones
      //def name = newTermName(clazz.name.toString)
      def tpe = newTypeName(clazz.name.toString)
      def stripVariance(t: TypeDef): TypeDef = {
        val mods = t.mods &~ ModifierFlags.COVARIANT &~ ModifierFlags.CONTRAVARIANT
        val params = t.tparams.map(stripVariance)
        treeCopy.TypeDef(t, mods, t.name, params, t.rhs)
      }.duplicate

      clazz.impl.body.collectFirst {
        case d @ DefDef(_, nme.CONSTRUCTOR, _, params, _, _) => (params, d.pos)
      } match {
        case None => companion // traits don't have constructor parameters
        case Some((ps, pos)) =>
          val apply = DefDef(
            Modifiers(),
            newTermName("apply"),
            clazz.tparams.map(stripVariance),
            ps.map(_.map { v =>
              val mods = v.mods &~ ModifierFlags.PARAMACCESSOR
              treeCopy.ValDef(v, mods, v.name, v.tpt.duplicate, v.rhs)
            }),
            if (clazz.tparams.isEmpty) Ident(tpe)
            else AppliedTypeTree(Ident(tpe), clazz.tparams.map { t => Ident(t.name) }),
            // PC plugins don't need implementations
            Literal(Constant(null))
          ).withAllPos(pos)

          val log = genLog.withAllPos(pos)

          val properties = ps.flatMap {
            vs =>
              vs.flatMap {
                v =>
                  // 1. need to take the type of v: V and turn it into Future[V]
                  // 2. add the type parameters of the class to the method (taking only what is needed for the method)
                  //genProperty(v.name.toString, Ident("scala.concurrent.Future[Unit]"))
                  Nil
              }
          }

          treeCopy.ModuleDef(
            companion, companion.mods, companion.name, {
            addMethods(companion.impl, apply :: log :: properties)
          }
          )
      }
    }

    def hasNoddyClasses(t: PackageDef): Boolean =
      t.stats.collectFirst {
        case c: ClassDef if c.mods.hasAnnotationNamed(Noddy) => c
      }.isDefined

    override def transform = {
      case t: PackageDef if hasNoddyClasses(t) =>
        val classes: Map[TypeName, ClassDef] = t.stats.collect {
          case c: ClassDef => c.name -> c
        }(breakOut)

        val companions: Map[TermName, ModuleDef] = t.stats.collect {
          case m: ModuleDef => m.name -> m
        }(breakOut)

        object ClassNoCompanion {
          def unapply(t: Tree): Option[ClassDef] = t match {
            case c: ClassDef if !companions.contains(c.name.companionName) => Some(c)
            case _ => None
          }
        }

        object ClassHasCompanion {
          def unapply(t: Tree): Option[ClassDef] = t match {
            case c: ClassDef if companions.contains(c.name.companionName) => Some(c)
            case _ => None
          }
        }

        object CompanionAndClass {
          def unapply(t: Tree): Option[(ModuleDef, ClassDef)] = t match {
            case m: ModuleDef =>
              classes.get(m.name.companionName).map { c => (m, c) }
            case _ => None
          }
        }

        val updated = t.stats.flatMap {
          case ClassNoCompanion(c) if c.mods.hasAnnotationNamed(Noddy) =>
            val companion = updateCompanion(c, genCompanion(c)).withAllPos(c.pos)
            List(updateClass(c), companion)

          case ClassHasCompanion(c) if c.mods.hasAnnotationNamed(Noddy) =>
            List(updateClass(c))

          case CompanionAndClass(companion, c) if c.mods.hasAnnotationNamed(Noddy) =>
            List(updateCompanion(c, companion))

          case tr =>
            List(tr)
        }

        treeCopy.PackageDef(t, t.pid, updated)

      case t => t
    }

  }

  override val components = List(NoddyParameters, NoddyCompanion)
}
