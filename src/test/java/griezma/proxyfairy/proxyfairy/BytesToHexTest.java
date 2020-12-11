package griezma.proxyfairy.proxyfairy;

import griezma.proxyfairy.BytesToHex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BytesToHexTest {
    @Test
    void bytesToHexBasic() {
        assertEquals("cafe", BytesToHex.bytesToHex(bytesOf(0xcafe)));
    }

    private byte[] bytesOf(long number) {
        return BigInteger.valueOf(number).toByteArray();
    }
}
