package org.emulinker.kaillera.controller

import com.google.common.truth.Expect
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.emulinker.kaillera.access.AccessManager
import org.emulinker.kaillera.controller.connectcontroller.protocol.ConnectMessage
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortRequest
import org.emulinker.kaillera.controller.connectcontroller.protocol.RequestPrivateKailleraPortResponse
import org.emulinker.kaillera.controller.v086.action.ActionModule
import org.emulinker.kaillera.controller.v086.protocol.AllReady
import org.emulinker.kaillera.controller.v086.protocol.CachedGameData
import org.emulinker.kaillera.controller.v086.protocol.ClientAck
import org.emulinker.kaillera.controller.v086.protocol.CreateGameNotification
import org.emulinker.kaillera.controller.v086.protocol.CreateGameRequest
import org.emulinker.kaillera.controller.v086.protocol.GameData
import org.emulinker.kaillera.controller.v086.protocol.JoinGameNotification
import org.emulinker.kaillera.controller.v086.protocol.JoinGameRequest
import org.emulinker.kaillera.controller.v086.protocol.QuitGameNotification
import org.emulinker.kaillera.controller.v086.protocol.QuitGameRequest
import org.emulinker.kaillera.controller.v086.protocol.QuitNotification
import org.emulinker.kaillera.controller.v086.protocol.QuitRequest
import org.emulinker.kaillera.controller.v086.protocol.ServerAck
import org.emulinker.kaillera.controller.v086.protocol.StartGameNotification
import org.emulinker.kaillera.controller.v086.protocol.StartGameRequest
import org.emulinker.kaillera.controller.v086.protocol.UserInformation
import org.emulinker.kaillera.controller.v086.protocol.UserJoined
import org.emulinker.kaillera.controller.v086.protocol.V086Bundle
import org.emulinker.kaillera.controller.v086.protocol.V086Message
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.kaillera.pico.koinModule
import org.emulinker.util.FastGameDataCache
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

class GameDataE2ETest : KoinComponent {

  private lateinit var channel: EmbeddedChannel
  private lateinit var controller: CombinedKailleraController
  private lateinit var userActionsExecutor: ThreadPoolExecutor

  private val clientQueues = ConcurrentHashMap<Int, BlockingQueue<OutgoingMsg>>()
  private val clientMap = ConcurrentHashMap<Int, Client>()

  class FakeAccessManager : AccessManager {
    override fun isAddressAllowed(address: InetAddress): Boolean = true

    override fun isSilenced(address: InetAddress): Boolean = false

    override fun isEmulatorAllowed(emulator: String): Boolean = true

    override fun isGameAllowed(game: String): Boolean = true

    override fun getAccess(address: InetAddress): Int = AccessManager.ACCESS_ADMIN

    override fun getAnnouncement(address: InetAddress): String? = null

    override fun addTempBan(addressPattern: String, duration: Duration) {}

    override fun addTempAdmin(addressPattern: String, duration: Duration) {}

    override fun addTempModerator(addressPattern: String, duration: Duration) {}

    override fun addTempElevated(addressPattern: String, duration: Duration) {}

    override fun addSilenced(addressPattern: String, duration: Duration) {}

    override fun clearTemp(address: InetAddress, clearAll: Boolean): Boolean = false

    override fun close() {}
  }

  @Before
  fun setup() {
    AppModule.charsetDoNotUse = Charsets.UTF_8

    startKoin {
      allowOverride(true)
      modules(koinModule, ActionModule, module { single<AccessManager> { FakeAccessManager() } })
    }
    controller = get()
    userActionsExecutor = get(named("userActionsExecutor"))
    channel = EmbeddedChannel(controller)
  }

  @After
  fun teardown() {
    try {
      clientMap.values.forEach { it.logout() }
    } catch (e: Exception) {
      // ignore
    }
    channel.close()
    controller.stop()
    userActionsExecutor.shutdown()
    stopKoin()
  }

  @Rule @JvmField val expect = Expect.create()

  @Test
  fun test1PlayerGameDataSync() {
    val port = 11111
    val client = Client(port, "User1", this)
    clientMap[port] = client
    client.login()
    val gameId = client.createGame()
    client.startGame()
    client.sendReady()
    client.waitForReady()

    // Advance iterator as requested (100 * playerNumber, player 1)
    client.advanceIterator(100)

    // Store history of sent packets to verify delayed echo
    val sentHistory = java.util.ArrayList<ByteArray>()

    // Loop 1000 times as requested
    for (i in 1..1000) {
      val inputData = client.nextInput()
      client.sendGameData(inputData)
      sentHistory.add(inputData.toByteArray())

      val received = client.receiveGameData()
      val receivedBytes = received.toByteArray()

      if (i <= 6) {
        // Phase 1: Buffering (FrameCount < TotalDelay)
        // Server buffers input, buffers are calculated based on input size.
        // We expect a packet of Zeros with the same size as the input.
        expect.that(receivedBytes.size).isEqualTo(inputData.toByteArray().size)
        expect.that(receivedBytes).isEqualTo(ByteArray(receivedBytes.size) { 0 })
      } else if (i <= 12) {
        // Phase 2: Draining (FrameCount >= TotalDelay, Buffer not empty)
        // Server processes buffered packets (1-6). Current inputs (7-12) are DROPPED.
        val historicIndex = i - 7 // i=7 -> index 0 (Packet 1)
        val expectedData = sentHistory[historicIndex]
        expect.that(receivedBytes).isEqualTo(expectedData)
      } else {
        // Phase 3: Synced (Buffer empty)
        // Server processes current inputs. Packets 7-12 were lost.
        // In 1-player mode with empty buffer,
        // newly added data triggers immediate send.
        val expectedData = inputData.toByteArray()
        expect.that(receivedBytes).isEqualTo(expectedData)
      }
    }
    println("Shutting down...")
    client.quitGame()
    client.quit()
  }

  @Test
  fun test4PlayerGameDataSync() {
    println("Initializing 4 clients...")
    val p1 = Client(1, "P1", this)
    val p2 = Client(2, "P2", this)
    val p3 = Client(3, "P3", this)
    val p4 = Client(4, "P4", this)
    val clients = listOf(p1, p2, p3, p4)

    println("Logging in clients...")
    clients.forEach { it.login() }

    // P1 Create Game
    println("P1 creating game...")
    p1.createGame()

    // Others Join
    println("Others joining game...")
    p2.joinGame(1)
    p3.joinGame(1)
    p4.joinGame(1)

    // P1 Start Game
    println("P1 starting game...")
    p1.startGame()

    // Wait for GameStarted
    println("Waiting for GameStarted...")
    clients.filter { it != p1 }.forEach { it.waitForGameStarted() }

    // Send Ready
    println("Sending Ready...")
    clients.forEach { it.sendReady() }
    println("Waiting for AllReady...")
    clients.forEach { it.waitForReady() }

    // Advance iterators to ensure unique data streams
    println("Advancing iterators...")
    p1.advanceIterator(100)
    p2.advanceIterator(200)
    p3.advanceIterator(300)
    p4.advanceIterator(400)

    // History of COMBINED packets (P1+P2+P3+P4)
    val sentHistory = java.util.ArrayList<ByteArray>()

    println("Starting 4-player loop...")
    for (i in 1..1000) {
      // 1. Collect Input for this frame
      val inputs = clients.map { it.nextInput() }

      // 2. Calculate Combined Packet for History
      // Server fans out: [P1_Data][P2_Data][P3_Data][P4_Data]
      val combinedPacket = java.io.ByteArrayOutputStream()
      inputs.forEach { combinedPacket.write(it.toByteArray()) }
      val expectedCombinedData = combinedPacket.toByteArray()
      sentHistory.add(expectedCombinedData)

      // 3. Send Data (All players send their part)
      clients.forEachIndexed { index, client -> client.sendGameData(inputs[index]) }

      // 4. Receive Data (All players receive the SAME combined packet)
      clients.forEach { client ->
        val received = client.receiveGameData()
        val receivedBytes = received.toByteArray()

        if (i <= 6) {
          // Phase 1: Buffering
          // Server buffers inputs. Returns Zeros.
          // Size = Sum of all input sizes
          expect.that(receivedBytes.size).isEqualTo(expectedCombinedData.size)
          expect.that(receivedBytes).isEqualTo(ByteArray(receivedBytes.size) { 0 })
        } else if (i <= 12) {
          // Phase 2: Draining
          // Server processes buffered packets (1-6). i-7 is the historic index.
          val historicIndex = i - 7
          val expectedData = sentHistory[historicIndex]
          expect.that(receivedBytes).isEqualTo(expectedData)
        } else {
          // Phase 3: Synced
          // Buffer empty. Receive current combined input.
          expect.that(receivedBytes).isEqualTo(expectedCombinedData)
        }
      }
    }
    println("4-player loop complete.")
    println("Shutting down...")
    // If player 1 quits, it closes the game. reverse() to make sure player 1 is last to quit.
    clients.reversed().forEach {
      it.quitGame()
      it.quit()
    }
  }

  @Test
  fun test4PlayerGoodConnection() {
    println("Initializing 4 clients with GOOD connection...")
    val p1 = Client(1, "P1", this, connectionType = ConnectionType.GOOD)
    val p2 = Client(2, "P2", this, connectionType = ConnectionType.GOOD)
    val p3 = Client(3, "P3", this, connectionType = ConnectionType.GOOD)
    val p4 = Client(4, "P4", this, connectionType = ConnectionType.GOOD)
    val clients = listOf(p1, p2, p3, p4)

    println("Logging in clients...")
    clients.forEach { it.login() }

    // P1 Create Game
    println("P1 creating game...")
    p1.createGame()

    // Others Join
    println("Others joining game...")
    p2.joinGame(1)
    p3.joinGame(1)
    p4.joinGame(1)

    // P1 Start Game
    println("P1 starting game...")
    p1.startGame()

    // Wait for GameStarted
    println("Waiting for GameStarted...")
    clients.filter { it != p1 }.forEach { it.waitForGameStarted() }

    // Send Ready
    println("Sending Ready...")
    clients.forEach { it.sendReady() }
    println("Waiting for AllReady...")
    clients.forEach { it.waitForReady() }

    // Advance iterators to ensure unique data streams
    println("Advancing iterators...")
    p1.advanceIterator(100)
    p2.advanceIterator(200)
    p3.advanceIterator(300)
    p4.advanceIterator(400)

    // History of COMBINED packets (P1+P2+P3+P4)
    val sentHistory = java.util.ArrayList<ByteArray>()

    println("Starting 4-player GOOD connection loop...")
    val actionsPerMessage = ConnectionType.GOOD.byteValue.toInt() // 3

    for (i in 1..333) {
      // 1. Collect Input for this packet (3 frames per client)
      // We keep frames separated first to construct expected combined data (interleaved)
      val clientFrames =
        clients.map { client ->
          val frames = mutableListOf<ByteBuf>()
          repeat(actionsPerMessage) { frames.add(client.nextInput()) }
          frames
        }

      // 2. Calculate Combined Packet for History
      // Server interleaves: Frame 1 (All Players), Frame 2 (All Players), Frame 3 (All Players)
      val combinedPacket = java.io.ByteArrayOutputStream()
      for (frameIdx in 0 until actionsPerMessage) {
        for (clientIdx in clients.indices) {
          combinedPacket.write(clientFrames[clientIdx][frameIdx].toByteArray())
        }
      }
      val expectedCombinedData = combinedPacket.toByteArray()
      sentHistory.add(expectedCombinedData)

      // Prepare packet inputs (Client just concatenates its own frames)
      // Note: This consumes the clientFrames ByteBufs (readerIndex moves)
      val packetInputs =
        clientFrames.map { frames ->
          val buffer = Unpooled.buffer()
          frames.forEach { buffer.writeBytes(it) }
          buffer
        }

      // 3. Send Data (All players send their part)
      clients.forEachIndexed { index, client -> client.sendGameData(packetInputs[index]) }

      // 4. Receive Data (All players receive the SAME combined packet)
      clients.forEach { client ->
        val received = client.receiveGameData()
        val receivedBytes = received.toByteArray()

        if (i <= 6) {
          // Phase 1: Buffering
          // Server buffers inputs. Returns Zeros.
          // Size = Sum of all input sizes
          expect.that(receivedBytes.size).isEqualTo(expectedCombinedData.size)
          expect.that(receivedBytes).isEqualTo(ByteArray(receivedBytes.size) { 0 })
        } else if (i <= 12) {
          // Phase 2: Draining
          // Server processes buffered packets (1-6). i-7 is the historic index.
          val historicIndex = i - 7
          val expectedData = sentHistory[historicIndex]
          expect.that(receivedBytes).isEqualTo(expectedData)
        } else {
          // Phase 3: Synced
          // Buffer empty. Receive current combined input.
          expect.that(receivedBytes).isEqualTo(expectedCombinedData)
        }
      }
    }
    println("Shutting down...")
    // If player 1 quits, it closes the game. reverse() to make sure player 1 is last to quit.
    clients.reversed().forEach {
      it.quitGame()
      it.quit()
    }
  }

  @Test
  fun test3PlayerVariableDelay() {
    println("Initializing 3 clients with variable delays...")
    // Target Delays: P1=2 frames, P2=3 frames, P3=4 frames.
    val p1 = Client(1, "P1", this, loginDelay = 25.milliseconds)
    val p2 = Client(2, "P2", this, loginDelay = 41.milliseconds)
    val p3 = Client(3, "P3", this, loginDelay = 58.milliseconds)
    val clients = listOf(p1, p2, p3)

    println("Logging in clients...")
    clients.forEach { it.login() }

    // P1 Create Game
    println("P1 creating game...")
    p1.createGame()

    // Others Join
    println("Others joining game...")
    p2.joinGame(1)
    p3.joinGame(1)

    // P1 Start Game
    println("P1 starting game...")
    p1.startGame()

    // Wait for GameStarted
    println("Waiting for GameStarted...")
    clients.filter { it != p1 }.forEach { it.waitForGameStarted() }

    // Send Ready
    println("Sending Ready...")
    clients.forEach { it.sendReady() }
    println("Waiting for AllReady...")
    clients.forEach { it.waitForReady() }

    // Advance iterators to ensure unique data streams
    println("Advancing iterators...")
    p1.advanceIterator(100)
    p2.advanceIterator(200)
    p3.advanceIterator(300)

    println("Starting 3-player variable delay loop...")
    val sentHistory = java.util.ArrayList<ByteArray>()

    // We use a shorter timeout for receive in the loop to allow fast clients to progress
    // while slow clients (P3) wait for sync.
    for (i in 1..50) {
      val inputs = clients.map { it.nextInput() }

      val combinedPacket = java.io.ByteArrayOutputStream()
      inputs.forEach { combinedPacket.write(it.toByteArray()) }
      val expectedCombinedData = combinedPacket.toByteArray()
      sentHistory.add(expectedCombinedData)

      clients.forEachIndexed { index, client -> client.sendGameData(inputs[index]) }

      val currentReceived = java.util.ArrayList<ByteArray>()
      clients.forEach { client ->
        try {
          // Use very short timeout to avoid deadlock during disparate buffering phases
          val received = client.receiveGameData(timeout = 50.milliseconds)
          val receivedBytes = received.toByteArray()
          currentReceived.add(receivedBytes)
        } catch (e: Exception) {
          // It is expected that some clients (like P3) might timeout in early frames.
        }
      }

      if (i > 35) {
        // Verify that we received packets from ALL clients (Sync achieved)
        expect.that(currentReceived.size).isEqualTo(clients.size)

        // Verify Consistency: All players must receive the IDENTICAL packet
        // and it must be non-zero (actual game data)
        if (currentReceived.isNotEmpty()) {
          val first = currentReceived[0]
          currentReceived.forEach {
            expect.that(it).isEqualTo(first)
            expect.that(it).isNotEqualTo(ByteArray(it.size) { 0 })
          }
        }
      }
    }
    println("3-player variable delay loop complete.")
    println("Shutting down...")
    // If player 1 quits, it closes the game. reverse() to make sure player 1 is last to quit.
    clients.reversed().forEach {
      it.quitGame()
      it.quit()
    }
  }

  @Test
  fun test4PlayerCachedGameDataSync() {
    println("Initializing 4 clients with OUTGOING CACHE ENABLED...")
    val p1 = Client(1, "P1", this, useOutgoingCache = true)
    val p2 = Client(2, "P2", this, useOutgoingCache = true)
    val p3 = Client(3, "P3", this, useOutgoingCache = true)
    val p4 = Client(4, "P4", this, useOutgoingCache = true)
    val clients = listOf(p1, p2, p3, p4)

    println("Logging in clients...")
    clients.forEach { it.login() }

    // P1 Create Game
    println("P1 creating game...")
    p1.createGame()

    // Others Join
    println("Others joining game...")
    p2.joinGame(1)
    p3.joinGame(1)
    p4.joinGame(1)

    // P1 Start Game
    println("P1 starting game...")
    p1.startGame()

    // Wait for GameStarted
    println("Waiting for GameStarted...")
    clients.filter { it != p1 }.forEach { it.waitForGameStarted() }

    // Send Ready
    println("Sending Ready...")
    clients.forEach { it.sendReady() }
    println("Waiting for AllReady...")
    clients.forEach { it.waitForReady() }

    // Advance iterators to ensure unique data streams
    println("Advancing iterators...")
    p1.advanceIterator(100)
    p2.advanceIterator(200)
    p3.advanceIterator(300)
    p4.advanceIterator(400)

    // History of COMBINED packets (P1+P2+P3+P4)
    val sentHistory = java.util.ArrayList<ByteArray>()

    println("Starting 4-player CACHED loop...")
    for (i in 1..1000) {
      // 1. Collect Input for this frame
      val inputs = clients.map { it.nextInput() }

      // 2. Calculate Combined Packet for History
      // Server fans out: [P1_Data][P2_Data][P3_Data][P4_Data]
      val combinedPacket = java.io.ByteArrayOutputStream()
      inputs.forEach { combinedPacket.write(it.toByteArray()) }
      val expectedCombinedData = combinedPacket.toByteArray()
      sentHistory.add(expectedCombinedData)

      // 3. Send Data (All players send their part, potentially as CachedGameData)
      clients.forEachIndexed { index, client -> client.sendGameData(inputs[index]) }

      // 4. Receive Data (All players receive the SAME combined packet)
      clients.forEach { client ->
        val received = client.receiveGameData()
        val receivedBytes = received.toByteArray()

        if (i <= 6) {
          // Phase 1: Buffering
          expect.that(receivedBytes.size).isEqualTo(expectedCombinedData.size)
          expect.that(receivedBytes).isEqualTo(ByteArray(receivedBytes.size) { 0 })
        } else if (i <= 12) {
          // Phase 2: Draining
          val historicIndex = i - 7
          val expectedData = sentHistory[historicIndex]
          expect.that(receivedBytes).isEqualTo(expectedData)
        } else {
          // Phase 3: Synced
          expect.that(receivedBytes).isEqualTo(expectedCombinedData)
        }
      }
    }
    println("4-player CACHED loop complete.")
    println("Shutting down...")
    // If player 1 quits, it closes the game. reverse() to make sure player 1 is last to quit.
    clients.reversed().forEach {
      //      it.quitGame()
      it.quit()
    }
  }

  @Test
  fun test4PlayerGameWithP4Drop() {
    println("Initializing 4 clients...")
    val p1 = Client(1, "P1", this)
    val p2 = Client(2, "P2", this)
    val p3 = Client(3, "P3", this)
    val p4 = Client(4, "P4", this)
    val clients = listOf(p1, p2, p3, p4)

    println("Logging in clients...")
    clients.forEach { it.login() }

    // P1 Create Game
    p1.createGame()

    // Others Join
    p2.joinGame(1)
    p3.joinGame(1)
    p4.joinGame(1)

    // P1 Start Game
    p1.startGame()

    // Wait for GameStarted
    clients.filter { it != p1 }.forEach { it.waitForGameStarted() }

    // Send Ready
    clients.forEach { it.sendReady() }
    clients.forEach { it.waitForReady() }

    // Advance iterators
    p1.advanceIterator(100)
    p2.advanceIterator(200)
    p3.advanceIterator(300)
    p4.advanceIterator(400)

    val remainingClients = listOf(p1, p2, p3)

    // Run for 100 frames with all 4 players
    println("Running 4-player loop (pre-drop)...")
    for (i in 1..100) {
      val inputs = clients.map { it.nextInput() }
      clients.forEachIndexed { index, client -> client.sendGameData(inputs[index]) }
      clients.forEach { client -> client.receiveGameData() }
    }

    // P4 Drops
    println("P4 Dropping...")
    p4.quitGame()

    // Remaining players continue
    println("Running 3-player loop (post-drop)...")
    val zeroData = ByteArray(p4.nextInput().toByteArray().size) { 0 }

    for (i in 1..100) {
      // 1. Collect Input for this frame (only remaining players)
      val inputs = remainingClients.map { it.nextInput() }

      // 2. Send Data
      remainingClients.forEachIndexed { index, client -> client.sendGameData(inputs[index]) }

      // 3. Receive Data
      // We expect P4's data to be ZEROS in the combined packet
      remainingClients.forEach { client ->
        val received = client.receiveGameData()
        val receivedBytes = received.toByteArray()

        // Verify the packet structure: [P1][P2][P3][P4(Zeros)]
        // We know the sizes from the inputs.
        val p1Size = inputs[0].toByteArray().size
        val p2Size = inputs[1].toByteArray().size
        val p3Size = inputs[2].toByteArray().size
        val p4Size = zeroData.size

        val receivedP1 = receivedBytes.copyOfRange(0, p1Size)
        val receivedP2 = receivedBytes.copyOfRange(p1Size, p1Size + p2Size)
        val receivedP3 = receivedBytes.copyOfRange(p1Size + p2Size, p1Size + p2Size + p3Size)
        val receivedP4 = receivedBytes.copyOfRange(p1Size + p2Size + p3Size, receivedBytes.size)

        expect.that(receivedP1).isEqualTo(inputs[0].toByteArray())
        expect.that(receivedP2).isEqualTo(inputs[1].toByteArray())
        expect.that(receivedP3).isEqualTo(inputs[2].toByteArray())
        expect.that(receivedP4).isEqualTo(zeroData)
      }
    }
  }

  fun pump() {
    channel.runPendingTasks()
    while (true) {
      val response: DatagramPacket = channel.readOutbound<DatagramPacket>() ?: break
      val port = response.recipient().port
      val queue = clientQueues[port]
      if (queue == null) {
        response.release()
        continue
      }

      val content = response.content()
      val connectMessage = ConnectMessage.parse(content)
      if (connectMessage.isSuccess) {
        queue.add(
          OutgoingMsg.ConnectionMessage(
            connectMessage.getOrThrow() as RequestPrivateKailleraPortResponse
          )
        )
        response.release()
        continue
      }

      content.resetReaderIndex()
      val client = clientMap[port]
      if (client == null) {
        response.release()
        continue
      }

      val lastId = client.lastMessageNumberReceived
      try {
        // IMPORTANT: We must NOT pass lastMessageID here if we want to emulate V0.86 client
        // behavior properly
        // or if we do, we must ensure it matches.
        // For simplicity, let's parse assuming lastId is tracked correctly by the client helper.
        val bundle = V086Bundle.parse(content, lastMessageID = lastId)
        queue.add(OutgoingMsg.Bundle(bundle))
      } catch (e: Exception) {
        // ignore
      } finally {
        response.release()
      }
    }
  }

  sealed interface OutgoingMsg {
    data class Bundle(val message: V086Bundle) : OutgoingMsg

    data class ConnectionMessage(val message: RequestPrivateKailleraPortResponse) : OutgoingMsg
  }

  class Client(
    port: Int,
    val username: String,
    val testInstance: GameDataE2ETest,
    val useOutgoingCache: Boolean = false,
    val connectionType: ConnectionType = ConnectionType.LAN,
    val loginDelay: Duration = Duration.ZERO,
  ) {
    var lastMessageNumberReceived = -1
    var lastMessageNumber = -1
    val sender = InetSocketAddress("127.0.0.1", port)
    private val queue = ArrayBlockingQueue<OutgoingMsg>(100)

    // Iterator for real game data
    private val inputIterator = buildIterator()

    init {
      testInstance.clientQueues[port] = queue
      // Register this client in the test instance map so pump() can find it
      testInstance.clientMap[port] = this
    }

    fun nextInput(): ByteBuf = inputIterator.next()

    fun advanceIterator(count: Int) {
      repeat(count) { inputIterator.next() }
    }

    private val messageIterator = iterator {
      while (true) {
        testInstance.pump()

        while (true) {
          val msg = queue.poll() ?: break
          when (msg) {
            is OutgoingMsg.Bundle -> {
              when (val inner = msg.message) {
                is V086Bundle.Single ->
                  yield(inner.message.also { lastMessageNumberReceived = it.messageNumber })

                is V086Bundle.Multi -> {
                  for (m in inner.messages.filterNotNull().sortedBy { it.messageNumber }) {
                    yield(m.also { lastMessageNumberReceived = it.messageNumber })
                  }
                }
              }
            }

            is OutgoingMsg.ConnectionMessage -> {}
          }
        }
        yield(null)
      }
    }

    fun login() {
      val request = RequestPrivateKailleraPortRequest("0.83")
      val buffer =
        Unpooled.buffer(request.bodyBytesPlusMessageIdType).apply { request.writeTo(this) }
      testInstance.channel.writeInbound(DatagramPacket(buffer, RECIPIENT, sender))

      var loggedIn = false
      while (!loggedIn) {
        testInstance.pump()
        val msg = queue.poll()
        if (msg is OutgoingMsg.ConnectionMessage) {
          loggedIn = true
        } else if (msg != null) {
          // ignore
        } else {
          Thread.yield()
        }
      }

      sendBundle(
        V086Bundle.Single(
          UserInformation(++lastMessageNumber, username, "Test Client", connectionType)
        )
      )
      consumeUntil {
        if (it is ServerAck) {
          // Simulate latency for ping calculation
          if (loginDelay > Duration.ZERO) {
            Thread.sleep(loginDelay.inWholeMilliseconds)
          }
          sendBundle(V086Bundle.Single(ClientAck(++lastMessageNumber)))
          false
        } else {
          it is UserJoined && it.username == this.username
        }
      }
    }

    fun createGame(): Int {
      sendBundle(V086Bundle.Single(CreateGameRequest(++lastMessageNumber, "Test Game")))
      var gameId = -1
      consumeUntil {
        if (it is CreateGameNotification && it.username == this.username) {
          gameId = it.gameId
          true
        } else false
      }
      return gameId
    }

    fun startGame() {
      sendBundle(V086Bundle.Single(StartGameRequest(++lastMessageNumber)))
      consumeUntil { it is StartGameNotification }
    }

    fun sendReady() {
      sendBundle(V086Bundle.Single(AllReady(++lastMessageNumber)))
    }

    fun waitForReady() {
      consumeUntil { it is AllReady }
    }

    fun logout() {
      sendBundle(V086Bundle.Single(QuitRequest(++lastMessageNumber, "test done")))
      consumeUntil { it is QuitNotification && it.username == this.username }
    }

    fun quitGame() {
      sendBundle(V086Bundle.Single(QuitGameRequest(++lastMessageNumber)))
      consumeUntil { it is QuitGameNotification && it.username == this.username }
    }

    fun joinGame(gameId: Int) {
      sendBundle(V086Bundle.Single(JoinGameRequest(++lastMessageNumber, gameId, connectionType)))
      consumeUntil {
        it is JoinGameNotification && it.username == this.username && it.gameId == gameId
      }
    }

    fun waitForGameStarted() {
      consumeUntil { it is StartGameNotification }
    }

    private val outgoingCache = FastGameDataCache(256)

    fun sendGameData(data: ByteBuf) {
      if (useOutgoingCache) {
        val index = outgoingCache.indexOf(data)

        if (index != -1) {
          sendBundle(V086Bundle.Single(CachedGameData(++lastMessageNumber, index)))
        } else {
          outgoingCache.add(data)
          sendBundle(V086Bundle.Single(GameData(++lastMessageNumber, data)))
        }
      } else {
        sendBundle(V086Bundle.Single(GameData(++lastMessageNumber, data)))
      }
    }

    private val cache = FastGameDataCache(256) // Standard Kaillera cache size

    fun receiveGameData(timeout: Duration = 1.seconds): ByteBuf {
      val deadline = System.nanoTime() + timeout.inWholeNanoseconds
      while (System.nanoTime() < deadline) {
        val msg = nextMessage()
        if (msg == null) {
          Thread.yield()
          continue
        }
        when (msg) {
          is GameData -> {
            println(
              "DEBUG: Client ${this.username} received GameData. bytes=${msg.gameData.readableBytes()}"
            )
            cache.add(msg.gameData)
            return msg.gameData
          }

          is CachedGameData -> {
            val cached = cache[msg.key]
            println(
              "DEBUG: Client ${this.username} received CachedGameData key=${msg.key} found=${cached != null}"
            )
            return cached
          }
        }
      }
      throw RuntimeException("Timed out waiting for GameData")
    }

    private fun nextMessage(): V086Message? = messageIterator.next()

    fun consumeUntil(timeoutMs: Long = 5000, predicate: (V086Message) -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        val msg =
          nextMessage()
            ?: run {
              Thread.yield()
              null
            }
        if (msg != null && predicate(msg)) return
      }
      throw RuntimeException("Timeout waiting for message")
    }

    fun sendBundle(bundle: V086Bundle) {
      val buffer = Unpooled.buffer(1024).apply { bundle.writeTo(this) }
      val packet = DatagramPacket(buffer, RECIPIENT, sender)
      testInstance.channel.writeInbound(packet)
    }

    fun quit() {
      sendBundle(V086Bundle.Single(QuitRequest(++lastMessageNumber, "Test Client Shutdown")))
    }
  }

  companion object {
    val RECIPIENT = InetSocketAddress("127.0.0.1", 27888)

    val LINES: List<String> by lazy {
      val inputStream =
        GameDataE2ETest::class.java.getResourceAsStream("/ssb_p1_out.txt")
          ?: throw java.io.FileNotFoundException("ssb_p1_out.txt not found in classpath")
      inputStream.bufferedReader().readLines()
    }

    private fun buildIterator() =
      iterator<ByteBuf> {
        lateinit var previousLine: String
        while (true) {
          for (line in LINES) {
            if (line.startsWith("x")) {
              val times = line.removePrefix("x").toInt()
              repeat(times) { yield(Unpooled.wrappedBuffer(previousLine.decodeHex())) }
            } else {
              yield(Unpooled.wrappedBuffer(line.decodeHex()))
            }
            previousLine = line
          }
        }
      }
  }
}

private fun String.decodeHex(): ByteArray {
  check(length % 2 == 0) { "Must have an even length" }
  return chunked(2).map { it.lowercase().toInt(16).toByte() }.toByteArray()
}

private fun ByteBuf.toByteArray(): ByteArray {
  val arr = ByteArray(readableBytes())
  getBytes(readerIndex(), arr)
  return arr
}
