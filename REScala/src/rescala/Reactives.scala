package rescala

import rescala.events._
import rescala.log._
import rescala.log.Logging
import java.util.UUID
import scala.collection.immutable.SortedSet

import scala.concurrent.stm.{TxnLocal, Ref, atomic}

/** A Reactive is a value type which has a dependency to other Reactives */
trait Reactive {
  // testing
  private var _timestamps = List[Stamp]()
  /** for compatibility reasons with existing tests */
  def timestamps: List[Stamp] = _timestamps
  def logTestingTimestamp() = _timestamps = TS.newTs :: _timestamps
  
  def level: Int = 0

  ReactiveEngine.log.nodeCreated(this)
}

/** A node that has nodes that depend on it */
trait DepHolder extends Reactive {
  private val dependents: Ref[Set[Dependent]] = Ref(Set[Dependent]())

  /** used for testing*/
  def dependentCount() = dependents.single.get.size

  def addDependent(dep: Dependent) = atomic { tx =>
    if (!dependents.get(tx).contains(dep)) {
      dependents.transform(_ + dep)(tx)
      ReactiveEngine.log.nodeAttached(dep, this)
    }
  }
  def removeDependent(dep: Dependent) = dependents.single.transform(_ - dep)
  def notifyDependents(change: Any): Unit = {
    ReactiveEngine.log.nodePulsed(this)
    dependents.single.get.foreach(_.dependsOnchanged(change, this))
  }
}

/** A node that depends on other nodes */
trait Dependent extends Reactive {
  private val dependOn: Ref[Set[DepHolder]] = Ref(Set[DepHolder]())

  /** for testing */
  def dependOnCount() = dependOn.single.get.size

  def addDependOn(dep: DepHolder) = atomic { tx =>
    if (!dependOn.get(tx).contains(dep)) {
      dependOn.transform(_ + dep)(tx)
      dep.addDependent(this)
      ReactiveEngine.log.nodeAttached(this, dep)
    }
  }
  def setDependOn(deps: TraversableOnce[DepHolder]) = atomic { tx =>
    val newDependencies = deps.toSet
    val oldDependencies = dependOn.get(tx)
    val removed = oldDependencies.diff(newDependencies)
    val added = newDependencies.diff(oldDependencies)
    removed.foreach(removeDependOn)
    added.foreach(addDependOn)
    dependOn.set(deps.toSet)(tx)
  }
  def removeDependOn(dep: DepHolder) = {
    dep.removeDependent(this)
    dependOn.single.transform(_ - dep)
  }

  override def level: Int = if (dependOnCount() <= 0) 0 else dependOn.single.get.map(_.level).max + 1

  /** called when it is this events turn to be evaluated
    * (head of the evaluation queue) */
  protected[rescala] def triggerReevaluation(): Unit

  /** callback when a dependency has changed */
  def dependsOnchanged(change: Any, dep: DepHolder): Unit
}

trait Changing[+T] {
  this: DepHolder =>

  /**
   * Create an event that fires every time the signal changes. It fires the tuple
   *  (oldVal, newVal) for the signal. The first tuple is (null, newVal)
   */
  lazy val change: Event[(T, T)] = new ChangedEventNode(this)

  /**
   * Create an event that fires every time the signal changes. The value associated
   * to the event is the new value of the signal
   */
  lazy val changed: Event[T] = change map ((x: (T, T)) => { x._2 })

    /** Convenience function filtering to events which change this reactive to value */
  def changedTo[V](value: V): Event[Unit] = (changed && { _ == value }).dropParam
}

trait Signal[+A] extends Changing[A] with FoldableReactive[A] with DepHolder {
  override def fold[B](init: B)(f: (B, A) => B): Signal[B] =
    new FoldedSignal(changed, init, f)

  def get: A

  final def apply(): A = get

  /** hook for subclasses to do something when they use their dependencies */
  def onDynamicDependencyUse[T](dependency: Signal[T]): Unit = { }

  def apply[T](signal: SignalSynt[T]): A = {
    signal.onDynamicDependencyUse(this)
    get
  }

  def map[B](f: A => B): Signal[B] = SignalSynt(this) { s: SignalSynt[B] => f(apply(s)) }

  /** Return a Signal that gets updated only when e fires, and has the value of this Signal */
  def snapshot(e: Event[_]): Signal[A] = IFunctions.snapshot(e, this)

  /** Switch to (and keep) event value on occurrence of e*/ // TODO: check types
  def switchTo[U >: A](e: Event[U]): Signal[U] = IFunctions.switchTo(e, this)

  /** Switch to (and keep) event value on occurrence of e*/ // TODO: check types
  def switchOnce[V >: A](e: Event[_])(newSignal: Signal[V]): Signal[V] = IFunctions.switchOnce(e, this, newSignal)

  /** Switch back and forth between this and the other Signal on occurrence of event e */
  def toggle[V >: A](e: Event[_])(other: Signal[V]): Signal[V] = IFunctions.toggle(e, this, other)

  /** Delays this signal by n occurrences */
  def delay(n: Int): Signal[A] = IFunctions.delay(this, n)
  
  /** Unwraps a Signal[Event[E]] to an Event[E] */
  def unwrap[E](implicit evidence: A <:< Event[E]): Event[E] = IFunctions.unwrap(this.map(evidence))

}

/** A root Reactive value without dependencies which can be set */
trait Var[T] extends Signal[T] {
  def set(newValue: T): Unit
  final def update(newValue: T): Unit = set(newValue)
}

object Var {
  def apply[T](initval: T) = new VarSynt(initval)
}

trait FoldableReactive[+A] {
  def fold[B](init: B)(f: (B, A) => B): Signal[B]

  /* ---------- derived methods follow ---------- */

  /** Iterates a value on the occurrence of the event. */
  def iterate[B](init: B)(f: B => B): Signal[B] =
    fold(init)((acc, _) => f(acc))

  /**
    * Counts the occurrences of the event. Starts from 0, when the event has never been
    *  fired yet. The argument of the event is simply discarded.
    */
  def count: Signal[Int] = fold(0)((acc, _) => acc + 1)

  /** Holds the latest value of an event as an Option, None before the
    * first event occured */
  def latestOption: Signal[Option[A]] =
    fold(None: Option[A])((acc, v) => Some(v))

  /** collects events resulting in a variable holding a list of all values. */
  def list: Signal[Seq[A]] =
    fold(List[A]())((acc, v) => v :: acc)

  /**
   * Returns a signal which holds the last n events in a list. At the beginning the
   *  list increases in size up to when n values are available
   */
  def last(n: Int): Signal[Seq[A]] =
    fold(Seq[A]()) { (acc,v) => acc.takeRight(n-1) :+ v }
}

/** An inner node which depends on other values */
trait DependentSignal[+T] extends Signal[T] with Dependent

/**
 * The engine that schedules the (glitch-free) evaluation
 * of the nodes in the dependency graph.
 */
object ReactiveEngine {

  /** If logging is needed, replace this with another instance of Logging */
  var log: Logging = NoLogging

  private object ReactiveOrdering extends Ordering[(Int, Dependent)] {
    override def compare(x: (Int, Dependent), y: (Int, Dependent)): Int = {
      val p1 = y._1.compareTo(x._1)
      if (p1 != 0) p1
      else System.identityHashCode(y._2).compareTo(System.identityHashCode(x._2))
    }
  }

  private val evalQueue = TxnLocal(SortedSet[(Int, Dependent)]()(ReactiveOrdering))

  /** Adds a dependant to the eval queue, duplicates are allowed */
  def addToEvalQueue(dep: Dependent): Unit = atomic { tx =>
      if (!evalQueue.get(tx).exists { case (_, elem) => elem eq dep }) {
        ReactiveEngine.log.nodeScheduled(dep)
        evalQueue.transform(_ + ((dep.level, dep)))(tx)
      }
  }

  /** Evaluates all the elements in the queue */
  def startEvaluation() = atomic { tx =>
    while (evalQueue.get(tx).nonEmpty) {
      val (level, head) = evalQueue.getAndTransform(_.tail)(tx).head
      // check the level if it changed queue again
      if (level == head.level) head.triggerReevaluation()
      else addToEvalQueue(head)
    }
  }
}
// TODO: check the use of these classes. Originally was only for testing
sealed case class Stamp(roundNum: Int, sequenceNum: Int)

object TS {
  private var _roundNum = 0
  private var _sequenceNum = 0

  def nextRound(): Unit = {
    _roundNum += 1
    _sequenceNum = 0

    ReactiveEngine.log.logRound(getCurrentTs)
  }

  def newTs: Stamp = {
    val ts = Stamp(_roundNum, _sequenceNum)
    _sequenceNum += 1
    ReactiveEngine.log.logRound(ts)
    ts
  }

  def getCurrentTs = Stamp(_roundNum, _sequenceNum)

  def reset(): Unit = {
    _roundNum = 0
    _sequenceNum = 0
  }
}
