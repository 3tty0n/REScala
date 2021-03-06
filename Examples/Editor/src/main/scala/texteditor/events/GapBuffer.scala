package texteditor.events

/**
 * Iterates over `array` whose content has the size of `count`.
 * If `count` is less than the length of `array`, there is assumed to be a
 * gap (a character sequence that does not count as content) in the array,
 * that starts at the position `caret`.
 *
 * This iterator class is used by the [[GapBuffer]] class.
 */
class CharacterIterator(buf: Array[Char], count: Int, caret: Int) extends Iterator[Char] {
  private var b = 0
  private var c = 0

  override def hasDefiniteSize = true
  override def size = count

  def hasNext = { c < count }
  def next = {
    if (b == caret)
      b += buf.length - count
    val ch = buf(b)
    b += 1
    c += 1
    ch
  }
}

/**
 * Implements a gap buffer that allows for efficient character insertion and
 * deletion at the current caret position.
 *
 * The content is located in two contiguous segments inside an array.
 * Both segments line up with the start resp. the end of the array,
 * which results in a gap between them.
 * That gap starts at the current caret position,
 * so that text inserted at the caret position can simply be inserted into the gap
 * adding text to one of the segments and decreasing the size of the gap.
 * Deletion of characters increases the size of the gap.
 * Moving the caret requires copying text from one segment to the other.
 */
class GapBuffer {
  private var buf = new Array[Char](0)
  private var size = 0
  private var offset = 0

  def caret = offset
  def caret_=(value: Int): Unit = {
    if (value >= 0 && value <= size) {
        // the caret has moved
        // which requires copying text from one segment to the other
        // to ensure that the gap starts at the current caret position
      val (cur, prev) = (value, caret)
      val (post, dist) = (buf.length - size + prev, math.abs(cur - prev))
      val (src, dest) = if (prev < cur) (post, prev) else (cur, post - dist)

      Array.copy(buf, src, buf, dest, dist)
      offset = cur
    }
  }

  def iterator = (new CharacterIterator(buf, size, caret)): Iterator[Char]

  def length = size

  def apply(i: Int) = buf(if (i >= caret) i + (buf.length - size) else i)

  def insert(str: String): Unit = {
    // insert text into the gap between the two text segments
    if (size + str.length > buf.length)
      expand(size + str.length)

    val post = buf.length - size + caret
    str.copyToArray(buf, post - str.length, str.length);
    size += str.length
  }

  def remove(count: Int): Unit = {
    // remove text by increasing the gap between the two text segments
    size -= math.min(count, size - caret)
  }

  private def expand(minsize: Int): Unit = {
    // the text does not fit into the buffer
    // which requires a larger buffer to be allocated
    // the two text segments have to be moved to a new buffer
    val postlength = size - caret
    val newlength = 2 * minsize
    var newbuf = new Array[Char](newlength)

    Array.copy(buf, 0, newbuf, 0, caret)
    Array.copy(buf, buf.length - postlength, newbuf, newbuf.length - postlength, postlength)

    buf = newbuf
  }
}
