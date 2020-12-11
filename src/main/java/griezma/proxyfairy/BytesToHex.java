package griezma.proxyfairy;

public class BytesToHex {
    private static  final char[] HEX_MAP = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);

       boolean head = true;

        for (int i = 0; i < bytes.length; ++i) {
            if (head && bytes[i] == 0) continue;
            head = false;
            int hi = (bytes[i] & 0xf0) >> 4;
            int lo = bytes[i] & 0x0f;
            sb.append(HEX_MAP[hi]).append(HEX_MAP[lo]);
        }

        return sb.toString();
    }
}
