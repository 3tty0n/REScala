package rescala

import scala.collection.mutable.ListBuffer
import rescala.events.Event
import rescala.events.ChangedEventNode
import rescala.events.EventNode

//trait FixedDepHolder extends Reactive {
//  val fixedDependents = new ListBuffer[Dependent]
//  def addFixedDependent(dep: Dependent) = fixedDependents += dep
//  def removeFixedDependent(dep: Dependent) = fixedDependents -= dep
// def notifyDependents(change: Any): Unit = dependents.map(_.dependsOnchanged(change,this))
//}

/* A node that has nodes that depend on it */
class VarSynt[T](private[this] var value: T) extends Var[T] {

  def get = value

  def set(newValue: T): Unit = {
    if (value != newValue) {
      value = newValue
      TS.nextRound() // Testing
      timestamps += TS.newTs // testing

      notifyDependents(value)
      ReactiveEngine.startEvaluation()

    } else {
      ReactiveEngine.log.nodePropagationStopped(this)
      timestamps += TS.newTs // testing
    }
  }

  def reEvaluate(): T = value
}

object VarSynt {
  def apply[T](initialValue: T) = new VarSynt(initialValue)
}

/* A dependant reactive value with dynamic dependencies (depending signals can change during evaluation) */
class SignalSynt[+T](reactivesDependsOnUpperBound: List[DepHolder])(expr: SignalSynt[T] => T)
  extends DependentSignal[T] {

  def this(expr: SignalSynt[T] => T) = this(List())(expr)

  reactivesDependsOnUpperBound.map(r => { // For glitch freedom
    if (r.level >= level) level = r.level + 1
  })

  /* Initial evaluation */
  val reactivesDependsOnCurrent = ListBuffer[DepHolder]()
  private[this] var currentValue = reEvaluate()

  def get = currentValue

  def triggerReevaluation() = reEvaluate()

  def reEvaluate(): T = {
    ReactiveEngine.log.nodeEvaluationStarted(this)

    /* Collect dependencies during the evaluation */
    reactivesDependsOnCurrent.map(_.removeDependent(this)) // remove me from the dependencies of the vars I depend on !
    reactivesDependsOnCurrent.clear()
    timestamps += TS.newTs // Testing

    // support mutable values by using hashValue rather than ==
    //val hashBefore = currentValue.hashCode
    val tmp = expr(this) // Evaluation)
    //val hashAfter = tmp.hashCode

    setDependOn(reactivesDependsOnCurrent)
    reactivesDependsOnCurrent.map(_.addDependent(this))

    /* Notify dependents only of the value changed */
    if (currentValue != tmp) {
      currentValue = tmp
      timestamps += TS.newTs // Testing
      notifyDependents(currentValue)
    } else {
      ReactiveEngine.log.nodePropagationStopped(this)
      timestamps += TS.newTs // Testing
    }
    ReactiveEngine.log.nodeEvaluationEnded(this)
    tmp
  }
  override def dependsOnchanged(change: Any, dep: DepHolder) = {
    ReactiveEngine.addToEvalQueue(this)
  }

}

/**
 * A syntactic signal
 */
object SignalSynt {
  def apply[T](reactivesDependsOn: List[DepHolder])(expr: SignalSynt[T] => T) =
    new SignalSynt(reactivesDependsOn)(expr)

  def apply[T](expr: SignalSynt[T] => T): SignalSynt[T] = apply(List())(expr)
  def apply[T](dependencyHolders: DepHolder*)(expr: SignalSynt[T] => T): SignalSynt[T] = apply(dependencyHolders.toList)(expr)

}





/** A wrappend event inside a signal, that gets "flattened" to a plain event node */
class WrappedEvent[T](wrapper: Signal[Event[T]]) extends EventNode[T] with Dependent {
  
  var inQueue = false
  var currentEvent = wrapper.get
  var currentValue: T = _
  
  // statically depend on wrapper
  wrapper.addDependent(this)
  addDependOn(wrapper)
  // dynamically depend on inner event
  currentEvent.addDependent(this)
  addDependOn(currentEvent)
  
  protected def updateProxyEvent(newEvent: Event[T]){
    // untie from from current event stream
    currentEvent.removeDependent(this)
    removeDependOn(currentEvent)
    // tie to new event stream
    newEvent.addDependent(this)
    addDependOn(newEvent)
    // remember current event
    currentEvent = newEvent
  }
  
  def triggerReevaluation() {
    timestamps += TS.newTs
    notifyDependents(currentValue)
    inQueue = false
  }
  
  override def dependsOnchanged(change: Any, dep: DepHolder) = {
    
    if(dep eq wrapper) { // wrapper changed the proxy event
	    val newEvent = change.asInstanceOf[Event[T]]
	    if(newEvent ne currentEvent)
	      updateProxyEvent(newEvent)
    }
    else if(dep eq currentEvent) { // proxied event changed
      // store the value to propagate
      currentValue = change.asInstanceOf[T]
      
      // and queue this node
      if (!inQueue) {
    	  inQueue = true
    	  ReactiveEngine.addToEvalQueue(this)
      }
    }    
    else throw new IllegalStateException("Illegal DepHolder " + dep)

  }
  
  override val timestamps = ListBuffer[Stamp]()
}
