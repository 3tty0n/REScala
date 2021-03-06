package rescala.engines

import java.util.concurrent.{Executor, Executors}

import rescala.graph.Struct
import rescala.parrp._
import rescala.propagation.Turn

import scala.language.existentials

object Engines extends CommonEngines {


  def byName[S <: Struct](name: String): Engine[S, Turn[S]] = name match {
    case "synchron" => synchron.asInstanceOf[Engine[S, Turn[S]]]
    case "unmanaged" => unmanaged.asInstanceOf[Engine[S, Turn[S]]]
    case "parrp" => parrp.asInstanceOf[Engine[S, Turn[S]]]
    case "fair" => synchronFair.asInstanceOf[Engine[S, Turn[S]]]
    case "locksweep" => locksweep.asInstanceOf[Engine[S, Turn[S]]]

    case other => throw new IllegalArgumentException(s"unknown engine $other")
  }

  def all: List[TEngine] = List[TEngine](unmanaged, parrp, locksweep)

  implicit val parrp: Engine[ParRP, ParRP] = parrpWithBackoff(() => new Backoff)

  implicit val locksweep: Engine[LSStruct.type, LockSweep] = locksweepWithBackoff(() => new Backoff())

  implicit val parallellocksweep: EngineImpl[LSStruct.type, ParallelLockSweep] = {
    val ex: Executor = Executors.newWorkStealingPool()
    new EngineImpl[LSStruct.type, ParallelLockSweep]("ParallelLockSweep", (engine, prior) => new ParallelLockSweep(new Backoff(), ex, engine, prior))
  }

  implicit val default: Engine[ParRP, ParRP] = parrp

  def locksweepWithBackoff(backOff: () => Backoff):Engine[LSStruct.type, LockSweep]  = new EngineImpl[LSStruct.type, LockSweep]("LockSweep", (_, prior) => new LockSweep(backOff(), prior))
  def parrpWithBackoff(backOff: () => Backoff): Engine[ParRP, ParRP] = new EngineImpl[ParRP, ParRP]("ParRP", (_, prior) => new ParRP(backOff(), prior))

}
