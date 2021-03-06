package benchmarks.simple

import java.util.concurrent.TimeUnit

import benchmarks.{EngineParam, Size, Step, Workload}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.BenchmarkParams
import rescala.engines.Engine
import rescala.propagation.Turn
import rescala.reactives.Signals

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class NaturalGraph[S <: rescala.graph.Struct] {

  implicit var engine: Engine[S, Turn[S]] = _

  var source: rescala.reactives.Var[Int, S] = _
  var result: rescala.reactives.Signal[Int, S] = _

  @Setup
  def setup(params: BenchmarkParams, size: Size, step: Step, engineParam: EngineParam[S], work: Workload): Unit = {
    engine = engineParam.engine
    val localEngine = engine
    import localEngine._

    def inc(source: Signal[Int]): Signal[Int] = source.map { v => val r = v + 1; work.consume(); r}
    def sum(s1: Signal[Int], s2: Signal[Int]): Signal[Int] = Signals.static(s1, s2) { implicit t => val r =  s1.get + s2.get; work.consume(); r}
    def noc(sources: Signal[Int]*): Signal[Int] = Signals.static(sources: _*) { implicit t => work.consume(); 0 }

    source = Var(step.get())

    // row 3
    val c1 = inc(source)

    // row 2
    val b1 = inc(c1)
    val b2 = inc(b1)
    val b3 = inc(b2)

    // row 3
    val c2 = inc(b3)
    val c3 = noc(c2)
    val c4 = inc(c3)

    // row 1
    val a1 = inc(b2)
    val a2 = inc(a1)
    val a3 = sum(a2, b2)
    val a4 = inc(a3)

    // row2
    val b4 = noc(a4, b3)
    val b5 = inc(b4)
    val b6 = inc(b5)
    val b7 = inc(b6)
    val b8 = sum(b7, c2)

    // row 3
    val c5 = sum(c4, b8)

    // row 4
    val d1 = inc(c2)

    // row 5
    val e1 = noc(c1)
    val e2 = inc(e1)
    val e3 = inc(e2)
    val e4 = inc(e3)
    val e5 = sum(e4, c2)

    val e6 = inc(c2)
    val e7 = sum(e6, d1)

    result = c5
  }

  @Benchmark
  def run(step: Step): Unit = source.set(step.run())

}
