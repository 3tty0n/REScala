package rescala.engines

import rescala.engines.Engines.{synchron, synchronFair, unmanaged}
import rescala.graph.Struct
import rescala.parrp._
import rescala.pipelining.{PipelineEngine, PipelineStruct, PipeliningTurn}
import rescala.propagation.Turn
import rescala.stm.STMTurn

import scala.concurrent.stm.atomic
import scala.language.existentials

object JVMEngines {

  type TEngine = Engine[S, Turn[S]] forSome { type S <: Struct }

  def byName[S <: Struct](name: String): Engine[S, Turn[S]] = name match {
    case "synchron" => synchron.asInstanceOf[Engine[S, Turn[S]]]
    case "unmanaged" => unmanaged.asInstanceOf[Engine[S, Turn[S]]]
    case "parrp" => parrp.asInstanceOf[Engine[S, Turn[S]]]
    case "stm" => stm.asInstanceOf[Engine[S, Turn[S]]]
    case "fair" => synchronFair.asInstanceOf[Engine[S, Turn[S]]]
    case "pipeline" => pipeline.asInstanceOf[Engine[S, Turn[S]]]
    case "locksweep" => locksweep.asInstanceOf[Engine[S, Turn[S]]]

    case other => throw new IllegalArgumentException(s"unknown engine $other")
  }

  def all: List[TEngine] = List[TEngine](stm, parrp, synchron, unmanaged, synchronFair, locksweep)

  implicit val parrp: Engine[ParRP, ParRP] = spinningWithBackoff(() => new Backoff)

  implicit val locksweep: Engine[LSStruct.type, LockSweep] = new EngineImpl[LSStruct.type, LockSweep](new LockSweep(new Backoff()))

  implicit val default: Engine[ParRP, ParRP] = parrp

  implicit val pipeline: Engine[PipelineStruct.type, PipeliningTurn] = new PipelineEngine()

  implicit val stm: Engine[STMTurn, STMTurn] = new EngineImpl[STMTurn, STMTurn](new STMTurn()) {
    override def plan[R](i: Reactive*)(f: STMTurn => R): R = atomic { tx => super.plan(i: _*)(f) }
  }

  def spinningWithBackoff(backOff: () => Backoff): Engine[ParRP, ParRP] = new EngineImpl[ParRP, ParRP](new ParRP(backOff()))

}
