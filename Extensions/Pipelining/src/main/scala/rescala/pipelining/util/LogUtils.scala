package rescala.pipelining.util

import scala.annotation.elidable
import scala.annotation.elidable._

private[pipelining] object LogUtils {
  
  @elidable(ASSERTION)
  def log(text : Any) = {
    //Console.println(s"${Thread.currentThread().getId}: $text")
  }

}