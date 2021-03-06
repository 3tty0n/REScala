package rescala.pipelining.tests

import org.scalatest.FlatSpec
import rescala.pipelining.PipelineEngine
import rescala.reactives.{Signals, Var}

class RecursiveResolvingTest extends FlatSpec {



  ignore should "resolve Transitive Conflict" in {
    implicit var engine = new PipelineEngine()

    /*
     *   S1    S2     S3
     *   | \  / | \  / |
     *   |  \/  |  \/  |
     *   |  /\  |  /\  |
     *   | /  \ | /  \ |
     *   VV    VVV    VV
     *   D1    D2     D3
     *
     */

    val s1 = Var(0)
    val s2 = Var(0)
    val s3 = Var(0)

    val d1 = Signals.lift(s1, s2) {_ + _}
    val d2 = Signals.lift(s1, s2, s3) {_ + _ + _}
    val d3 = Signals.lift(s2, s3) {_ + _}
  }


}
