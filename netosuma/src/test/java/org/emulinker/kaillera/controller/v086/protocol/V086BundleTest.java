package org.emulinker.kaillera.controller.v086.protocol;

import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class V086BundleTest {
  private final static String HEX_STRING = "0123456789abcdef";

  @Test
  public void shouldAnswerWithTrue() throws Exception {
    assertThat(true).isTrue();
    // V086Bundle bundle = V086Bundle.parse(hexStringToByteArray("010000240003EA4B0050726F6A6563742036346B20302E313320283031204175672032303033290001"));
  }

  // http://www.java2s.com/example/java/java.lang/convert-hex-string-to-bytebuffer.html
  public static ByteBuffer hexStringToByteArray(final String hex) {
    boolean hasFullByte = true;
    int b = 0;
    int bufferSize = 0;
    int bytesAdded = 0;

    for (char c : hex.toCharArray()) {
      if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' || c <= 'F')) {
        bufferSize++;
      }
    }

    byte[] bytes = new byte[bufferSize / 2];

    for (char c : hex.toCharArray()) {
      int pos = HEX_STRING.indexOf(Character.toLowerCase(c));

      if (pos > -1) {
        b = (b << 4) | (pos & 0xFF);
        hasFullByte = !hasFullByte;

        if (hasFullByte) {
          bytes[bytesAdded] = (byte) (b & 0xFF);
          b = 0;
          bytesAdded++;
        }
      }
    }

    ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
    buffer.put(bytes);
    return buffer;
  }
}
