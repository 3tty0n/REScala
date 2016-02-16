package rescala.engines

import rescala.graph.Struct
import rescala.propagation.Turn

import scala.annotation.implicitNotFound
import scala.language.implicitConversions

@implicitNotFound(msg = "could not generate a ticket." +
  " tickets are available whenever an implicit turn is available" +
  " (in which case that turn is used) or if no turn is present" +
  " an implicit engine will be used to generate new tickets")
final case class Ticket[S <: Struct](self: Either[Turn[S], Engine[S, Turn[S]]]) extends AnyVal {
  def apply[T](f: Turn[S] => T): T = self match {
    case Left(turn) => f(turn)
    case Right(factory) => factory.subplan()(f)
  }
}

object Ticket extends LowPriorityTicket {
  implicit def explicit[S <: Struct](implicit turn: Turn[S]): Ticket[S] = Ticket(Left(turn))
}

sealed trait LowPriorityTicket {
  implicit def dynamic[S <: Struct](implicit factory: Engine[S, Turn[S]]): Ticket[S] = Ticket(Right(factory))
}