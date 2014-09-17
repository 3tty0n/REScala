package rescala

import java.util.UUID

import rescala.events._
import rescala.log.ReactiveLogging
import rescala.propagation._
import rescala.signals.Signal

/** A Reactive is a value type which has a dependency to other Reactives */
trait Reactive extends ReactiveLogging {

  val id: UUID = UUID.randomUUID()

  var _level = 0
  def ensureLevel(l: Int): Unit
  def level: Int = _level


  /** called when it is this events turn to be evaluated
    * (head of the evaluation queue) */
  protected[rescala] def reevaluate()(implicit turn: Turn): EvaluationResult

  /** called to finalize the pulse value (turn commits) */
  protected[rescala] def commit(implicit turn: Turn): Unit

  log.nodeCreated(this)
}

/** A node that has nodes that depend on it */
trait Dependency[+P] extends Reactive {
  private var _dependants: Set[Dependant] = Set()
  final def dependants: Set[Dependant] = _dependants

  def addDependant(dep: Dependant): Unit = {
    if (!_dependants.contains(dep)) {
      _dependants += dep
      log.nodeAttached(dep, this)
    }
  }

  def removeDependant(dep: Dependant) = _dependants -= dep

  override def ensureLevel(l: Int): Unit = {
    val oldLevel = level
    if (l >= _level) _level = l + 1
    val newLevel = level
    if (oldLevel < newLevel) _dependants.foreach(_.ensureLevel(newLevel))
  }

  private[this] var pulses: Map[Turn, Pulse[P]] = Map()

  def pulse(implicit turn: Turn): Pulse[P] = pulses.getOrElse(turn, NoChangePulse)

  final protected[this] def pulse(pulse: Pulse[P])(implicit turn: Turn): Unit = {
    pulses += turn -> pulse
    log.nodePulsed(this)
  }

  def commit(implicit turn: Turn): Unit = {
    pulses -= turn
  }

}

/** A node that depends on other nodes */
trait Dependant extends Reactive {
  private var dependencies: Set[Dependency[_]] = Set()

  def addDependency(dep: Dependency[_]): Unit = {
    if (!dependencies.contains(dep)) {
      ensureLevel(dep.level)
      dependencies += dep
      dep.addDependant(this)
    }
  }
  def setDependencies(deps: TraversableOnce[Dependency[_]]): Unit = {
    val newDependencies = deps.toSet
    val removed = dependencies.diff(newDependencies)
    val added = newDependencies.diff(dependencies)
    removed.foreach(removeDependency)
    added.foreach(addDependency)
    dependencies = deps.toSet
  }
  def removeDependency(dep: Dependency[_]): Unit = {
    dep.removeDependant(this)
    dependencies -= dep
  }
}

/** An inner node which depends on other values */
trait DependentSignal[+T] extends Signal[T] with Dependant


