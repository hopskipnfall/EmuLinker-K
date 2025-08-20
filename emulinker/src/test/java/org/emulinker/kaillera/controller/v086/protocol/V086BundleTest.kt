package org.emulinker.kaillera.controller.v086.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.netty.buffer.Unpooled
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds
import org.emulinker.kaillera.controller.v086.V086Utils
import org.emulinker.kaillera.controller.v086.protocol.V086Message.Companion.SERIALIZERS
import org.emulinker.kaillera.model.ConnectionType
import org.emulinker.kaillera.model.GameStatus
import org.emulinker.kaillera.model.UserStatus
import org.emulinker.kaillera.pico.AppModule
import org.emulinker.testing.LoggingRule
import org.emulinker.util.VariableSizeByteArray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

data class Params(
  val hexString: String,
  val expectedMessages: List<V086Message?>,
  val lastMessageNumber: Int,
)

private fun wrapInArray(vararg a: Params) = a.map { arrayOf(it as Object) }.toList()

@RunWith(Parameterized::class)
class V086BundleTestShiftJis {
  @get:Rule val logging = LoggingRule()

  @Parameterized.Parameter lateinit var params: Params

  @Test
  fun parse_byteBuffer() {
    val buffer = V086Utils.hexStringToByteBuffer(params.hexString)

    when (val parsedBundle = V086Bundle.parse(buffer, params.lastMessageNumber)) {
      is V086Bundle.Single -> {
        assertThat(parsedBundle.message).isEqualTo(params.expectedMessages.single())
      }
      is V086Bundle.Multi -> {
        assertThat(parsedBundle.messages.toList())
          .containsExactlyElementsIn(params.expectedMessages)
          .inOrder()
      }
    }
  }

  @Test
  fun parse_byteBuf() {
    val buf = Unpooled.buffer(4096)
    buf.writeBytes(V086Utils.hexStringToByteBuffer(params.hexString))

    when (val parsedBundle = V086Bundle.parse(buf, params.lastMessageNumber)) {
      is V086Bundle.Single -> {
        assertThat(parsedBundle.message).isEqualTo(params.expectedMessages.single())
      }
      is V086Bundle.Multi -> {
        assertThat(parsedBundle.messages.toList())
          .containsExactlyElementsIn(params.expectedMessages)
          .inOrder()
      }
    }
  }

  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): List<Array<Object>> {
      AppModule.charsetDoNotUse = Charset.forName("Shift_JIS")

      return wrapInArray(
        Params(
          hexString =
            "01 00 00 24 00 03 EA 4B 00 50 72 6F 6A 65 63 74 20 36 34 6B 20 30 2E 31 33 20 28 30 31 20 41 75 67 20 32 30 30 33 29 00 01",
          expectedMessages =
            listOf(
              UserInformation(
                messageNumber = 0,
                username = "鵺",
                clientType = "Project 64k 0.13 (01 Aug 2003)",
                connectionType = ConnectionType.LAN,
              )
            ),
          lastMessageNumber = -1,
        ),
        Params(
          hexString =
            "03 06 00 11 00 17 53 65 72 76 65 72 00 92 e8 88 f5 33 30 96 bc 00 05 00 0b 00 02 6a 6a 00 8b 01 1f 00 00 00 01 04 00 74 00 04 00 01 00 00 00 01 00 00 00 74 65 73 74 00 13 00 00 00 02 88 01 01 4e 69 6e 74 65 6e 64 6f 20 41 6c 6c 2d 53 74 61 72 21 20 44 61 69 72 61 6e 74 6f 75 20 53 6d 61 73 68 20 42 72 6f 74 68 65 72 73 20 28 4a 29 00 3d 00 00 00 50 72 6f 6a 65 63 74 20 36 34 6b 20 30 2e 31 33 20 28 30 31 20 41 75 67 20 32 30 30 33 29 00 74 65 73 74 00 31 2f 32 00 02",
          expectedMessages =
            listOf(
              InformationMessage(messageNumber = 6, source = "Server", message = "定員30名"),
              UserJoined(
                messageNumber = 5,
                username = "jj",
                userId = 395,
                ping = 31.milliseconds,
                connectionType = ConnectionType.LAN,
              ),
              ServerStatus(
                messageNumber = 4,
                users =
                  listOf(
                    ServerStatus.User(
                      username = "test",
                      ping = 19.milliseconds,
                      status = UserStatus.CONNECTING,
                      userId = 392,
                      connectionType = ConnectionType.LAN,
                    )
                  ),
                games =
                  listOf(
                    ServerStatus.Game(
                      romName = "Nintendo All-Star! Dairantou Smash Brothers (J)",
                      gameId = 61,
                      clientType = "Project 64k 0.13 (01 Aug 2003)",
                      username = "test",
                      playerCountOutOfMax = "1/2",
                      status = GameStatus.PLAYING,
                    )
                  ),
              ),
            ),
          lastMessageNumber = -1,
        ),
        Params(
          hexString =
            "03 06 00 11 00 17 53 65 72 76 65 72 00 92 e8 88 f5 33 30 96 bc 00 05 00 0b 00 02 6a 6a 00 8b 01 1f 00 00 00 01 04 00 74 00 04 00 01 00 00 00 01 00 00 00 74 65 73 74 00 13 00 00 00 02 88 01 01 4e 69 6e 74 65 6e 64 6f 20 41 6c 6c 2d 53 74 61 72 21 20 44 61 69 72 61 6e 74 6f 75 20 53 6d 61 73 68 20 42 72 6f 74 68 65 72 73 20 28 4a 29 00 3d 00 00 00 50 72 6f 6a 65 63 74 20 36 34 6b 20 30 2e 31 33 20 28 30 31 20 41 75 67 20 32 30 30 33 29 00 74 65 73 74 00 31 2f 32 00 02",
          expectedMessages =
            listOf(
              InformationMessage(messageNumber = 6, source = "Server", message = "定員30名"),
              UserJoined(
                messageNumber = 5,
                username = "jj",
                userId = 395,
                ping = 31.milliseconds,
                connectionType = ConnectionType.LAN,
              ),
              ServerStatus(
                messageNumber = 4,
                users =
                  listOf(
                    ServerStatus.User(
                      username = "test",
                      ping = 19.milliseconds,
                      status = UserStatus.CONNECTING,
                      userId = 392,
                      connectionType = ConnectionType.LAN,
                    )
                  ),
                games =
                  listOf(
                    ServerStatus.Game(
                      romName = "Nintendo All-Star! Dairantou Smash Brothers (J)",
                      gameId = 61,
                      clientType = "Project 64k 0.13 (01 Aug 2003)",
                      username = "test",
                      playerCountOutOfMax = "1/2",
                      status = GameStatus.PLAYING,
                    )
                  ),
              ),
            ),
          lastMessageNumber = -1,
        ),
        Params(
          hexString =
            "03 09 00 1C 00 12 00 18 00 10 20 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 00 1C 00 12 00 18 00 10 24 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 FF 00 00 00 00 00 07 00 02 00 15 00",
          expectedMessages =
            listOf(
              GameData(
                messageNumber = 9,
                VariableSizeByteArray(
                  byteArrayOf(
                    16,
                    32,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                  ) // HERE
                ),
              ),
              GameData(
                messageNumber = 8,
                VariableSizeByteArray(
                  byteArrayOf(
                    16,
                    36,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    -1,
                    0,
                    0,
                    0,
                    0,
                    0,
                  )
                ),
              ),
              // Technically AllReady(messageNumber = 7) is also here, but discarded because already
              // seen.
              null,
            ),
          lastMessageNumber = 7,
        ),
        Params(
          hexString =
            "01,AD,89,1C,00,12,00,18,00,10,20,00,00,00,00,00,00,01,00,00,00,00,00,FC,03,00,00,00,00,00,00,00,00,AC,89,1C,00,12,00,18,00,10,20,00,00,00,00,00,00,01,00,00,00,00,00,FC,03,00,00,00,00,00,00,00,00",
          expectedMessages =
            listOf(
              GameData(
                messageNumber = 35245,
                gameData =
                  VariableSizeByteArray(
                    "10,20,00,00,00,00,00,00,01,00,00,00,00,00,FC,03,00,00,00,00,00,00,00,00"
                      .replace(",", "")
                      .hexToByteArray(HexFormat.UpperCase)
                  ),
              )
              //              GameData(
              //                messageNumber = 35244,
              //                gameData =
              //                  VariableSizeByteArray(
              //
              // "10,20,00,00,00,00,00,00,01,00,00,00,00,00,FC,03,00,00,00,00,00,00,00,00"
              //                      .replace(",", "")
              //                      .hexToByteArray(HexFormat.UpperCase)
              //                  ),
              //              ),
            ),
          lastMessageNumber = -1,
        ),
        Params(
          hexString = "03,07,00,02,00,09,00,06,00,02,00,09,00,05,00,02,00,09,00",
          expectedMessages =
            listOf(
              KeepAlive(messageNumber = 7, value = 0),
              KeepAlive(messageNumber = 6, value = 0),
              KeepAlive(messageNumber = 5, value = 0),
            ),
          lastMessageNumber = -1,
        ),
      )
    }
  }
}

@RunWith(Parameterized::class)
class V086BundleTestShiftUtf8 {
  @get:Rule val logging = LoggingRule()

  @Parameterized.Parameter lateinit var params: Params

  @Test
  fun parse_byteBuffer() {
    val buffer = V086Utils.hexStringToByteBuffer(params.hexString)

    when (val parsedBundle = V086Bundle.parse(buffer, params.lastMessageNumber)) {
      is V086Bundle.Single -> {
        assertThat(parsedBundle.message).isEqualTo(params.expectedMessages.single())
      }
      is V086Bundle.Multi -> {
        assertThat(parsedBundle.messages.toList())
          .containsExactlyElementsIn(params.expectedMessages)
          .inOrder()
      }
    }
  }

  @Test
  fun parse_byteBuf() {
    val buf = Unpooled.buffer(4096)
    buf.writeBytes(V086Utils.hexStringToByteBuffer(params.hexString))

    when (val parsedBundle = V086Bundle.parse(buf, params.lastMessageNumber)) {
      is V086Bundle.Single -> {
        assertThat(parsedBundle.message).isEqualTo(params.expectedMessages.single())
      }
      is V086Bundle.Multi -> {
        assertThat(parsedBundle.messages.toList())
          .containsExactlyElementsIn(params.expectedMessages)
          .inOrder()
      }
    }
  }

  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): List<Array<Object>> {
      AppModule.charsetDoNotUse = StandardCharsets.UTF_8

      return wrapInArray(
        Params(
          hexString =
            "02 01 00 12 00 06 00 00 00 00 00 01 00 00 00 02 00 00 00 03 00 00 00 00 00 24 00 03 EA 4B 00 50 72 6F 6A 65 63 74 20 36 34 6B 20 30 2E 31 33 20 28 30 31 20 41 75 67 20 32 30 30 33 29 00 01",
          expectedMessages =
            listOf(
              ClientAck(messageNumber = 1) // there might be a null here too?
            ),
          lastMessageNumber = 0,
        ),
        Params(
          hexString =
            "03 0A 00 17 00 0A 00 53 6D 61 73 68 52 65 6D 69 78 30 2E 39 2E 37 00 00 FF FF FF FF 09 00 04 00 0B 00 FF FF 08 00 37 00 0A 00 4E 69 6E 74 65 6E 64 6F 20 41 6C 6C 2D 53 74 61 72 21 20 44 61 69 72 61 6E 74 6F 75 20 53 6D 61 73 68 20 42 72 6F 74 68 65 72 73 20 28 4A 29 00 00 FF FF FF FF",
          expectedMessages = listOf(CreateGameRequest(messageNumber = 10, "SmashRemix0.9.7")),
          lastMessageNumber = 9,
        ),
      )
    }
  }
}

class V086BundleTest {
  @get:Rule val logging = LoggingRule()

  @Test
  fun hexStringToByteBuffer() {
    val hexInput =
      "01 00 00 24 00 03 EA 4B 00 50 72 6F 6A 65 63 74 20 36 34 6B 20 30 2E 31 33 20 28 30 31 20 41 75 67 20 32 30 30 33 29 00 01"
    val byteBuffer = V086Utils.hexStringToByteBuffer(hexInput)
    assertThat(V086Utils.toHex(byteBuffer)).isEqualTo(hexInput.replace(" ", ""))
  }

  // TODO(nue): Move this into ServerStatusTest.kt. For some reason it will not pass if I do that.
  // I suspect a Kotlin bug related to nested data classes....
  @Test
  fun serverStatus_bodyLength() {
    assertThat(
        ServerStatus(
            messageNumber = 4,
            users =
              listOf(
                ServerStatus.User(
                  username = "test",
                  ping = 19.milliseconds,
                  status = UserStatus.CONNECTING,
                  userId = 392,
                  connectionType = ConnectionType.LAN,
                )
              ),
            games =
              listOf(
                ServerStatus.Game(
                  romName = "Nintendo All-Star! Dairantou Smash Brothers (J)",
                  gameId = 61,
                  clientType = "Project 64k 0.13 (01 Aug 2003)",
                  username = "test",
                  playerCountOutOfMax = "1/2",
                  status = GameStatus.PLAYING,
                )
              ),
          )
          .bodyBytes
      )
      .isEqualTo(115)
  }

  @Test
  fun toJavaAddress() {
    val address = io.ktor.network.sockets.InetSocketAddress("127.2.0.1", 42)

    val converted = address.toJavaAddress()

    assertThat(converted).isEqualTo(converted)
    assertThat(converted.hostname).isEqualTo("127.2.0.1")
    assertThat(converted.port).isEqualTo(42)
  }

  @Test
  fun serializerMapShouldBeExhaustive() {
    assertThat(SERIALIZERS.map { it.value::class })
      .containsExactlyElementsIn(MessageSerializer::class.sealedSubclasses)
  }
}
