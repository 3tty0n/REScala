package tests.rescala


import org.scalatest.prop.Whenever
import rescala.Infiltrator.assertLevel
import rescala.engines.Ticket




class DynamicSignalTestSuite extends RETests with Whenever {



  allEngines("signal ReEvaluates The Expression When Something ItDepends OnIsUpdated"){ engine => import engine._
    val v = Var(0)
    var i = 1
    val s = dynamic(v) { s => v(s) + i }
    i = 2
    v.set(2)
    assert(s.now == 4)
  }

  allEngines("the Expression IsNote Evaluated Every Time Get Val IsCalled"){ engine => import engine._
    var a = 10
    val s = dynamic()(s => 1 + 1 + a)
    assert(s.now === 12)
    a = 11
    assert(s.now === 12)
  }


  allEngines("simple Signal Returns Correct Expressions"){ engine => import engine._
    val s = dynamic()(s => 1 + 1 + 1)
    assert(s.now === 3)
  }

  allEngines("the Expression IsEvaluated Only Once"){ engine => import engine._

    var a = 0
    val v = Var(10)
    val s1 = dynamic(v) { s => a += 1; v(s) % 10 }
    var s2 = dynamic(s1) { s => a }


    assert(a == 1)
    v.set(11)
    assert(a == 2)
    v.set(21)
    assert(a == 3)
  }

  allEngines("handlers Are Executed"){ engine => import engine._

    var test = 0
    val v = Var(1)

    val s1 = dynamic(v) { s => 2 * v(s) }
    val s2 = dynamic(v) { s => 3 * v(s) }
    val s3 = dynamic(s1, s2) { s => s1(s) + s2(s) }

    s1.changed += { (_) => test += 1 }
    s2.changed += { (_) => test += 1 }
    s3.changed += { (_) => test += 1 }

    assert(test == 0)

    v.set(3)
    assert(test == 3)

  }

  allEngines("level IsCorrectly Computed"){ engine => import engine._

    val v = Var(1)

    val s1 = dynamic() { s => 2 * v(s) }
    val s2 = dynamic() { s => 3 * v(s) }
    val s3 = dynamic() { s => s1(s) + s2(s) }

    assertLevel(v, 0)
    assertLevel(s1, 1)
    assertLevel(s2, 1)
    assertLevel(s3, 2)


  }


  /* Specific of SignalSynt */


  allEngines("signal Does Not ReEvaluate The Expression IfDepends OnIsUpdated That IsNot InCurrent Dependencies"){ engine => import engine._
    val v1 = Var(true)
    val v2 = Var(0)
    val v3 = Var(10)
    var i = 0
    val s = dynamic(v1, v2, v3) { s =>
      i += 1
      if (v1(s)) v2(s) else v3(s)
    }

    assert(i == 1)
    assert(s.now == 0)
    v2.set(1)
    assert(i == 2)
    assert(s.now == 1)
    v3.set(11) // No effect
    assert(i == 2)
    assert(s.now == 1)

    v1.set(false)
    assert(i == 3)
    assert(s.now == 11)
    v3.set(12)
    assert(i == 4)
    assert(s.now == 12)
    v2.set(2) // No effect
    assert(i == 4)
    assert(s.now == 12)
  }


  allEngines("keep_fixed Dependencies"){ engine => import engine._

    val v1 = Var(true)
    val v2 = Var(0)
    val v3 = Var(10)
    var i = 0
    var test = 0

    val s = dynamic() { s =>
      i += 1
      if (v1(s)) v2(s) else v3(s)
    }

    val e = s.change
    e += (x => test += 1)

    assert(test == 0)
    v2.set(1)
    assert(test == 1)

    v1.set(false)
    assert(test == 2)
    v3.set(11)
    assert(test == 3)

    v2.set(2)
    assert(test == 3)

    v1.set(true)
    assert(test == 4)
    v2.set(3)
    assert(test == 5)


  }

  allEngines("dependant IsOnly Invoked OnValue Changes"){ engine => import engine._
    var changes = 0
    val v = Var(1)
    val s = dynamic(v) { s =>
      changes += 1; v(s) + 1
    }
    assert(changes === 1)
    assert(s.now === 2)
    v.set(2)
    assert(s.now === 3)
    assert(changes === 2)
    v.set(2)
    assert(changes === 2) // is actually 3
  }

  allEngines("creating Signals Inside Signals") { engine => import engine._

    // ignore for locksweep, as it does not support predeclared levels, so would run into an endless loop below
    whenever(engine != rescala.engines.Engines.locksweep &&
      engine != rescala.engines.Engines.parallellocksweep) {

      val outside = Var(1)

      val testsig = dynamic() { t =>
        //remark 01.10.2014: without the bound the inner signal will be enqueued (it is level 0 same as its dependency)
        //this will cause testsig to reevaluate again, after the inner signal is fully updated.
        // leading to an infinite loop
        dynamic(outside) { t => outside(t) }.apply(t)
      }

      assert(testsig.now === 1)
      outside() = 2
      assert(testsig.now === 2)
    }
  }

  allEngines("creating Signals Inside Signals Workaround"){ engine => import engine._


    val outside = Var(1)

    val dynsig: Signal[Signal[Int]] = dynamic() { t =>
      dynamic() { t => outside(t) }
    }
    val testsig = dynsig.flatten

      assert(testsig.now === 1)
    outside() = 2
    assert(testsig.now === 2)
  }

  allEngines("dynamic dependency changes ontop of stuff that is not changing"){ engine => import engine._
    val v0 = Var("level 0")
    val v3 = v0.map(_ => "level 1").map(_ => "level 2").map(_ => "level 3")

    val condition = Var(false)
    val `dynamic signal changing from level 1 to level 4` = dynamic() { turn =>
      if (condition(turn)) v3(turn) else v0(turn)
    }
    assert(`dynamic signal changing from level 1 to level 4`.now == "level 0")
    assertLevel(`dynamic signal changing from level 1 to level 4`, 1)

    condition.set(true)
    assert(`dynamic signal changing from level 1 to level 4`.now == "level 3")
    assertLevel(`dynamic signal changing from level 1 to level 4`, 4)
  }

  allEngines("creating signals in signals based on changing signals"){ engine => import engine._
    val v0 = Var("level 0")
    val v3 = v0.map(_ + "level 1").map(_  + "level 2").map(_ + "level 3")

    val `dynamic signal changing from level 1 to level 4` = dynamic() { turn =>
      if (v0(turn) == "level 0") v0(turn) else {
        v3.map(_ + "level 4 inner")(turn).apply(turn)
      }
    }
    assert(`dynamic signal changing from level 1 to level 4`.now == "level 0")
    assertLevel(`dynamic signal changing from level 1 to level 4`, 1)

    v0.set("level0+")
    assert(`dynamic signal changing from level 1 to level 4`.now == "level0+level 1level 2level 3level 4 inner")
    assertLevel(`dynamic signal changing from level 1 to level 4`, 5)
  }

  allEngines("creating signals in signals based on changing signals dynamic"){ engine => import engine._
    val v0 = Var("level 0")
    val v3 = v0.map(_ + "level 1").map(_  + "level 2").map(_ + "level 3")

    val `dynamic signal changing from level 1 to level 4` = dynamic() { turn =>
      if (v0(turn) == "level 0") v0(turn) else {
        // the static bound is necessary here, otherwise we get infinite loops
        dynamic(v3) { t =>  v3(t) + "level 4 inner" }(Ticket.fromTurn(turn)).apply(turn)
      }
    }
    assert(`dynamic signal changing from level 1 to level 4`.now == "level 0")
    assertLevel(`dynamic signal changing from level 1 to level 4`, 1)

    v0.set("level0+")
    assert(`dynamic signal changing from level 1 to level 4`.now == "level0+level 1level 2level 3level 4 inner")
    assertLevel(`dynamic signal changing from level 1 to level 4`, 5)
  }
}
