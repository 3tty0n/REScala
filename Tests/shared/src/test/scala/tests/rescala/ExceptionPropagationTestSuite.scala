package tests.rescala

import rescala.reactives.Signals

import scala.util.{Failure, Success, Try}




class ExceptionPropagationTestSuite extends RETests {


  // we need this because scalajs does not throw exceptions
  def div(in: Int) = div2(in, 100)
  def div2(in: Int, acc: Int) = if (in == 0) throw new ArithmeticException() else acc / in



  allEngines("basic Signal Exceptions"){ engine => import engine._
    val v = Var(42)
    val ds = Signal { div(v()) }
    val ss = v.map(div)

    assert(ds.now == 100 / v.now, "dynamic arithmetic error")
    assert(ss.now == 100 / v.now, "static arithmetic error")

    v.set(0)

    intercept[ArithmeticException](ds.now)
    intercept[ArithmeticException](ss.now)

  }

  allEngines("basic Event Excepitons"){ engine => import engine._
    val e = Evt[Int]
    val de = Event { e().map(div) }
    val se = e.map(div)

    var dres: Try[Int] = null
    var sres: Try[Int] = null

    de.map(Success(_)).recover(Failure(_)).observe(dres = _)
    se.map(Success(_)).recover(Failure(_)).observe(sres = _)

    e.fire(42)

    assert(dres === Success(100 / 42), "dynamic arithmetic error")
    assert(sres === Success(100 / 42), "static arithmetic error")

    e.fire(0)

    intercept[ArithmeticException](dres.get)
    intercept[ArithmeticException](sres.get)

  }


  allEngines("more Exceptions"){ engine => import engine._
    val input = Evt[String]
    val trimmed = input.map(_.trim)
    val toInted = trimmed.map(_.toInt)
    val folded = toInted.fold(100)((acc, v) => div2(v, acc))
    val `change'd` = folded.change

    var res: Signals.Diff[Int] = null
    `change'd`.observe(res = _)


    assert(folded.now === 100)

    input.fire("10")
    assert(folded.now === 10, "successful fold")
    assert(res.pair === (100 -> 10), "successful changed")

    input.fire(" 2  ")
    assert(folded.now === 5, "successful fold 2")
    assert(res.pair === (10 -> 5), "successful changed 2")

    input.fire(" 0  ")
    intercept[ArithmeticException](folded.now)
    intercept[ArithmeticException](res.pair)

    input.fire(" aet ")
    intercept[NumberFormatException](folded.now)
    intercept[NumberFormatException](res.pair)


    input.fire(" 2 ")
    intercept[NumberFormatException](folded.now)
    intercept[NumberFormatException](res.pair)

  }

  allEngines("signal Regenerating"){ engine => import engine._
    val input = Var("100")
    val trimmed = input.map(_.trim)
    val folded = trimmed.map(_.toInt)
    val `change'd` = folded.change

    var res: Signals.Diff[Int] = null

    `change'd`.observe(res = _)



    assert(folded.now === 100)

    input.set("10")
    assert(folded.now === 10, "successful fold")
    assert(res.pair === (100 -> 10), "successful changed")

    input.set(" 2  ")
    assert(folded.now === 2, "successful fold 2")
    assert(res.pair === (10 -> 2), "successful changed2")


    input.set(" 0  ")
    assert(folded.now === 0, "successful fold 3")
    assert(res.pair === (2 -> 0), "successful changed3")

    input.set(" aet ")
    intercept[NumberFormatException](folded.now)
    intercept[NumberFormatException](res.pair)


    input.set("100")
    assert(folded.now === 100, "successful fold 5")
    intercept[NumberFormatException](res.pair) //TODO: should maybe change?

    input.set("200")
    assert(res.pair === (100 -> 200), "successful changed3")







  }

}
