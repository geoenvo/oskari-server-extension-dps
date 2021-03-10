package wbidp.oskari.util;

import org.apache.commons.codec.binary.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;

/**
 * Provides tools for handling CKAN passwords in Oskari.
 * The methods contained in this class can be used to interpret CKAN's
 * hashed passwords and also to create similar password hashes.
 * 
 * All functionality is based on the way CKAN stores the passwords for user
 * accounts by using the PassLib-library in Python.
 * 
 * The passwords are hashed by using the PBKDF2 algorithm.
 * 
 * CKAN User-model:
 * https://github.com/ckan/ckan/blob/c8d2fef3bb7c1656cec3ae23e30f5e9945b963d7/ckan/model/user.py
 * 
 * More information about PassLib:
 * https://passlib.readthedocs.io/en/stable/lib/passlib.hash.pbkdf2_digest.html#format-algorithm
 * https://passlib.readthedocs.io/en/stable/lib/passlib.utils.binary.html#passlib.utils.binary.ab64_encode
 *  
 */
public class CKANPasswordHandler {
    private static final Logger LOG = LogFactory.getLogger(CKANPasswordHandler.class);

    public static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512";

    // The following constants may be changed without breaking existing hashes.
    public static final int SALT_BYTES = 16;
    public static final int HASH_BYTES = 24;
    public static final int PBKDF2_ITERATIONS = 25000;

    public static final int ITERATION_INDEX = 2;
    public static final int SALT_INDEX = 3;
    public static final int PBKDF2_INDEX = 4;

    /**
     * Returns a salted PBKDF2 hash of the password in the same format that CKAN uses.
     *
     * @param   password    the password to hash
     * @return              a salted PBKDF2 hash of the password
     */
    public static String createHash(String password)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
        return createHash(password.toCharArray());
    }

    /**
     * Returns a salted PBKDF2 hash of the password in the same format that CKAN uses.
     *
     * @param   password    the password to hash
     * @return              a salted PBKDF2 hash of the password in CKAN format
     */
    public static String createHash(char[] password)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Generate a random salt
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);

        // Hash the password
        byte[] hash = pbkdf2(password, salt, PBKDF2_ITERATIONS, HASH_BYTES);
        // format iterations:salt:hash
        return "$pbkdf2-sha512$" + PBKDF2_ITERATIONS + "$" + toBase64(salt) + "$" +  toBase64(hash);
    }

    /**
     * Validates a password using a hash from CKAN.
     *
     * @param   password    the password to check
     * @param   CKANHash    the hash of the valid password from CKAN
     * @return              true if the password is correct, false if not
     */
    public static boolean validatePassword(String password, String CKANHash)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
        return validatePassword(password.toCharArray(), CKANHash);
    }

    /**
     * Validates a password using a hash from CKAN.
     *
     * @param   password    the password to check
     * @param   CKANHash    the hash of the valid password from CKAN
     * @return              true if the password is correct, false if not
     */
    public static boolean validatePassword(char[] password, String CKANHash)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Decode the hash into its parameters
        String[] params = CKANHash.split("\\$");
        int iterations = Integer.parseInt(params[ITERATION_INDEX]);
        byte[] salt = fromBase64(params[SALT_INDEX]);
        byte[] hash = fromBase64(params[PBKDF2_INDEX]);
        
        // Compute the hash of the provided password, using the same salt, 
        // iteration count, and hash length
        byte[] testHash = pbkdf2(password, salt, iterations, hash.length);
        
        LOG.debug("RESULT: " + "$pbkdf2-sha512$" + PBKDF2_ITERATIONS + "$" + toBase64(salt) + "$" +  toBase64(testHash));
        
        // Compare the hashes in constant time. The password is correct if
        // both hashes match.
        return slowEquals(hash, testHash);
    }

    /**
     * Compares two byte arrays in length-constant time. This comparison method
     * is used so that password hashes cannot be extracted from an on-line 
     * system using a timing attack and then attacked off-line.
     * 
     * @param   a       the first byte array
     * @param   b       the second byte array 
     * @return          true if both byte arrays are the same, false if not
     */
    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for(int i = 0; i < a.length && i < b.length; i++)
            diff |= a[i] ^ b[i];
        return diff == 0;
    }

    /**
     *  Computes the PBKDF2 hash of a password.
     *
     * @param   password    the password to hash.
     * @param   salt        the salt
     * @param   iterations  the iteration count (slowness factor)
     * @param   bytes       the length of the hash to compute in bytes
     * @return              the PBDKF2 hash of the password
     */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        return skf.generateSecret(spec).getEncoded();
    }

    /**
     * Converts a string of Base64 characters into a byte array.
     * Decodes from shortened base64 format which omits padding & whitespace.
     * Uses custom ``./`` altchars, but supports decoding normal ``+/`` altchars as well.
     * 
     * It is primarily used by Passlib's custom pbkdf2 hashes (used by CKAN).
     *
     * @param   base64      the base64 string
     * @return              the base64 shortened string with altchars decoded into a byte array
     */
    private static byte[] fromBase64(String base64) {
        byte[] decodedStringBytes = null;
        decodedStringBytes = Base64.decodeBase64(base64.replace(".", "+"));
        return decodedStringBytes;
    }

    /**
     * Converts a byte array into a Base64 string.
     * Encodes using shortened base64 format which omits padding & whitespace.
     * Uses custom ``./`` altchars.
     * 
     * It is primarily used by Passlib's custom pbkdf2 hashes (used by CKAN).
     *
     * @param   array       the byte array to convert
     * @return              a shortened string with altchars encoding the byte array
     */
    private static String toBase64(byte[] array) {
        String encodedString = Base64.encodeBase64String(array);
        encodedString = encodedString.replace("=", "").replace("+", ".").replaceAll("\\s", "");
        return encodedString;
    }
}