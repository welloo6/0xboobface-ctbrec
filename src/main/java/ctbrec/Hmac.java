package ctbrec;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hmac {

    private static final transient Logger LOG = LoggerFactory.getLogger(Hmac.class);

    public static byte[] generateKey() {
        LOG.debug("Generating key");
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32];
        random.nextBytes(key);
        return key;
    }

    public static String calculate(String msg, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException, IllegalStateException, UnsupportedEncodingException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(keySpec);
        byte[] result = mac.doFinal(msg.getBytes("UTF-8"));
        String hmac = bytesToHex(result);
        return hmac;
    }

    public static boolean validate(String msg, byte[] key, String hmacToCheck) throws InvalidKeyException, NoSuchAlgorithmException, IllegalStateException, UnsupportedEncodingException {
        return Hmac.calculate(msg, key).equals(hmacToCheck);
    }

    /**
     * Converts a byte array to a string
     *
     * @param hash
     * @return string
     */
    static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
