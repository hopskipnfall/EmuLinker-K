package org.emulinker.kaillera.model.impl

import com.google.common.flogger.FluentLogger
import java.lang.Exception
import java.lang.Runnable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.emulinker.kaillera.model.KailleraGame
import org.emulinker.kaillera.model.KailleraUser
import org.emulinker.util.EmuLang.getString
import org.emulinker.util.EmuUtil
import org.emulinker.util.VariableSizeByteArray

class AutoFireScanner2(private var game: KailleraGame, sensitivity: Int) : AutoFireDetector {
  override var sensitivity = 0
    set(value) {
      if (value in 0..5) {
        field = value
        maxDelay = SENSITIVITY_TABLE[value][0]
        minReps = SENSITIVITY_TABLE[value][1]
      } else {
        field = 0
      }
    }

  private var maxDelay = 0
  private var minReps = 0

  private var scanningJobs: Array<ScanningJob?>? = null

  override fun start(numPlayers: Int) {
    if (sensitivity <= 0) return
    scanningJobs = arrayOfNulls(numPlayers)
  }

  override fun addPlayer(user: KailleraUser, playerNumber: Int) {
    if (sensitivity <= 0 || scanningJobs == null) return
    scanningJobs!![playerNumber - 1] = ScanningJob(user, playerNumber)
  }

  override fun stop(playerNumber: Int) {
    if (sensitivity <= 0 || scanningJobs == null) return
    scanningJobs!![playerNumber - 1]!!.stop()
  }

  override fun stop() {
    if (sensitivity <= 0) return
    scanningJobs?.forEach { it?.stop() }
  }

  override fun addData(playerNumber: Int, data: VariableSizeByteArray, bytesPerAction: Int) {
    if (sensitivity <= 0) return
    scanningJobs?.get(playerNumber - 1)?.addData(data, bytesPerAction)
  }

  inner class ScanningJob(private val user: KailleraUser, private val playerNumber: Int) :
    Runnable {
    private var bytesPerAction = -1
    private val sizeLimit: Int = (maxDelay + 1) * minReps * 5
    private val bufferSize = 5
    private var size = 0
    private val buffer: Array<ByteArray> = Array(bufferSize) { ByteArray(sizeLimit) }
    private var head = 0
    private var tail = 0
    private var pos = 0
    private var running = false
    private var stopFlag = false

    @Synchronized
    fun addData(data: VariableSizeByteArray, bytesPerAction: Int) {
      // TODO(nue): Don't make a deep copy.
      val data = data.toByteArray()
      if (pos + data.size >= sizeLimit) {
        val firstSize = sizeLimit - pos
        System.arraycopy(data, 0, buffer[tail], pos, firstSize)
        tail++
        if (tail == bufferSize) tail = 0
        System.arraycopy(data, firstSize, buffer[tail], 0, data.size - firstSize)
        pos = data.size - firstSize
        size++
        if (this.bytesPerAction <= 0) this.bytesPerAction = bytesPerAction
        if (!running) executor.submit(this)
      } else {
        System.arraycopy(data, 0, buffer[tail], pos, data.size)
        pos += data.size
      }
    }

    fun stop() {
      stopFlag = true
    }

    override fun run() {
      synchronized(this) { running = true }
      try {
        while (size > 0 && !stopFlag) {
          var data: ByteArray?
          synchronized(this) {
            data = buffer[head]
            //						logger.atFine().log("Scanning " + data.length + " bytes from buffer position " +
            // head);
            // head = ((head+1) % bufferSize);
            head++
            if (head == bufferSize) head = 0
            size--
          }

          // determine the number of actions in this array
          val actionCount = data!!.size / bytesPerAction
          val thisAction = ByteArray(bytesPerAction)
          val lastAction = ByteArray(bytesPerAction)
          var actionA = ByteArray(bytesPerAction)
          var aPos = 0
          var aCount = 0
          var aSequence = 0
          var lastASequence = 0
          var aSequenceCount = 0
          var actionB = ByteArray(bytesPerAction)
          var bPos = 0
          var bCount = 0
          var bSequence = 0
          var lastBSequence = 0
          var bSequenceCount = 0
          for (i in 0 until actionCount) {
            System.arraycopy(data, i * bytesPerAction, thisAction, 0, bytesPerAction)
            //						logger.atFine().log("thisAction=" + EmuUtil.bytesToHex(thisAction) + " actionA="
            // +
            // EmuUtil.bytesToHex(actionA) + " aCount=" + aCount + " actionB=" +
            // EmuUtil.bytesToHex(actionB) + " bCount=" + bCount + " aSequence=" + aSequence + "
            // aSequenceCount=" + aSequenceCount + " bSequence=" + bSequence + " bSequenceCount=" +
            // bSequenceCount);
            if (aCount == 0) {
              System.arraycopy(thisAction, 0, actionA, 0, bytesPerAction)
              aPos = i
              aCount = 1
              aSequence = 1
            } else if (thisAction.contentEquals(actionA)) {
              aCount++
              if (thisAction.contentEquals(lastAction)) aSequence++
              else {
                if (lastASequence == aSequence && aSequence <= maxDelay) aSequenceCount++
                else aSequenceCount = 0
                lastASequence = aSequence
                aSequence = 1
              }
            } else if (bCount == 0) {
              System.arraycopy(thisAction, 0, actionB, 0, bytesPerAction)
              bPos = i
              bCount = 1
              bSequence = 1
            } else if (thisAction.contentEquals(actionB)) {
              bCount++
              if (thisAction.contentEquals(lastAction)) bSequence++
              else {
                if (lastBSequence == bSequence && bSequence <= maxDelay) bSequenceCount++
                else bSequenceCount = 0
                lastBSequence = bSequence
                bSequence = 1
              }
            } else {
              actionA = lastAction
              aCount = 1
              aSequence = 1
              aSequenceCount = 0
              actionB = thisAction
              bCount = 1
              bSequence = 0
              bSequenceCount = 0
            }
            System.arraycopy(thisAction, 0, lastAction, 0, bytesPerAction)

            //						if(aSequenceCount >= 3 && bSequenceCount >= 3 && !stopFlag)
            //						{
            //							logger.atFine().log("thisAction=" + EmuUtil.bytesToHex(thisAction) + "
            // actionA=" +
            // EmuUtil.bytesToHex(actionA) + " aCount=" + aCount + " actionB=" +
            // EmuUtil.bytesToHex(actionB) + " bCount=" + bCount + " aSequence=" + aSequence + "
            // aSequenceCount=" + aSequenceCount + " bSequence=" + bSequence + " bSequenceCount=" +
            // bSequenceCount);
            //						}
            if (aSequenceCount >= minReps && bSequenceCount >= minReps && !stopFlag) {
              val gameImpl = game as KailleraGameImpl
              gameImpl.announce(getString("AutoFireScanner2.AutoFireDetected", user!!.name))
              logger
                .atInfo()
                .log(
                  "AUTOUSERDUMP\t%s\t%d\t%d\t%s\t%s\t%s",
                  EmuUtil.DATE_FORMAT.format(gameImpl.startDate),
                  if (aSequence < bSequence) aSequence else bSequence,
                  game.id,
                  game.romName,
                  user.name,
                  user.socketAddress!!.address.hostAddress,
                )
              break
            }
          }
        }
      } catch (e: Exception) {
        logger.atSevere().withCause(e).log("AutoFireScanner2 thread for %s caught exception!", user)
      } finally {
        synchronized(this) { running = false }
      }
    }
  }

  companion object {
    private val logger = FluentLogger.forEnclosingClass()

    // MAX DELAY, MIN REPETITIONS
    private val SENSITIVITY_TABLE =
      arrayOf(
        intArrayOf(0, 0),
        intArrayOf(2, 13),
        intArrayOf(3, 11),
        intArrayOf(4, 9),
        intArrayOf(5, 7),
        intArrayOf(6, 5),
      )
    private var executor: ExecutorService = Executors.newCachedThreadPool()
  }

  init {
    this.sensitivity = sensitivity
  }
}
