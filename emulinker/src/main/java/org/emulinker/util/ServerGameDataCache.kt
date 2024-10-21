package org.emulinker.util

import java.lang.IndexOutOfBoundsException
import java.util.Arrays

@Deprecated("This has promise but indexOf does not work reliably!", level = DeprecationLevel.ERROR)
class ServerGameDataCache(size: Int) : GameDataCache {
  // array holds the elements
  private var array: Array<ByteArray?> = arrayOfNulls(size)

  // hashmap for quicker indexOf access, but slows down inserts
  //	protected HashMap<byte[], Integer>	map;
  private var map: HashMap<Int, Int> = HashMap(size, .05f)

  // head points to the first logical element in the array, and
  // tail points to the element following the last. This means
  // that the list is empty when head == tail. It also means
  // that the array array has to have an extra space in it.
  private var head = 0
  private var tail = 0

  // the size can also be worked out each time as: (tail + array.length -
  // head) % array.length
  // Strictly speaking, we don't need to keep a handle to size,
  // as it can be calculated programmatically, but keeping it
  // makes the algorithms faster.
  override var size = 0
    private set

  override fun contains(element: ByteArray): Boolean = indexOf(element) >= 0

  override fun containsAll(elements: Collection<ByteArray>): Boolean = elements.all { contains(it) }

  override fun isEmpty(): Boolean = size == 0

  override fun iterator(): Iterator<ByteArray> = array.asSequence().filterNotNull().iterator()

  override fun indexOf(data: ByteArray): Int {
    val i = map[data.contentHashCode()]
    return i?.let { unconvert(it) } ?: -1
  }

  override fun get(index: Int): ByteArray? {
    rangeCheck(index)
    return array[convert(index)]
  }

  override fun set(index: Int, data: ByteArray): ByteArray? {
    rangeCheck(index)
    val convertedIndex = convert(index)
    val oldValue = array[convertedIndex]
    array[convertedIndex] = data
    //		map.put(Arrays.toString(data), convertedIndex);
    map[data.contentHashCode()] = convertedIndex
    return oldValue
  }

  // This method is the main reason we re-wrote the class.
  // It is optimized for removing first and last elements
  // but also allows you to remove in the middle of the list.
  override fun remove(index: Int): ByteArray? {
    rangeCheck(index)
    val pos = convert(index)
    return try {
      //			map.remove(array[pos]);
      map.remove(array[pos].contentHashCode())
      //			map.remove(Arrays.toString(array[pos]));
      array[pos]
    } finally {
      array[pos] = null // Let gc do its work

      // optimized for FIFO access, i.e. adding to back and
      // removing from front
      if (pos == head) head = (head + 1) % array.size
      else if (pos == tail) tail = Math.floorMod(tail - 1, array.size)
      else {
        if (pos > head && pos > tail) { // tail/head/pos
          System.arraycopy(array, head, array, head + 1, pos - head)
          head = (head + 1) % array.size
        } else {
          System.arraycopy(array, pos + 1, array, pos, tail - pos - 1)
          tail = Math.floorMod(tail - 1, array.size)
        }
      }
      size--
    }
  }

  override fun clear() {
    for (i in 0 until size) {
      array[convert(i)] = null
    }
    size = 0
    tail = size
    head = tail
    map.clear()
  }

  override fun add(data: ByteArray): Int {
    if (size == array.size) remove(0)
    val pos = tail
    array[tail] = data
    //		map.put(Arrays.toString(data), tail);
    //		map.put(data, tail);
    map[Arrays.hashCode(data)] = tail
    tail = (tail + 1) % array.size
    size++
    return unconvert(pos)
  }

  // The convert() method takes a logical index (as if head was always 0) and
  // calculates the index within array
  private fun convert(index: Int): Int = (index + head) % array.size

  // there gotta be a better way to do this but I can't figure it out
  private fun unconvert(index: Int): Int =
    if (index >= head) index - head else array.size - head + index

  private fun rangeCheck(index: Int) {
    if (index >= size || index < 0) throw IndexOutOfBoundsException("index=$index, size=$size")
  }
}
