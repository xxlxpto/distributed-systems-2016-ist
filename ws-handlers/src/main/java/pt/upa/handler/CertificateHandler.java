package pt.upa.handler;

import javax.crypto.Cipher;
import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Set;

import static javax.xml.bind.DatatypeConverter.*;


/**
 * This SOAPHandler outputs the contents of inbound and outbound messages.
 */
public class CertificateHandler implements SOAPHandler<SOAPMessageContext> {

    private static final String CONTEXT_PROPERTY = "my.property";
    private static final String ELEMENT_NAME = "signature";
    private static final String PREFIX = "S";
    private static final String NAMESPACE = "pt.upa.handler";
    private static final String DIGEST_ALGORITHM = "SHA-1";
    private static final String ASSYMETRIC_KEY_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private KeyPair key;

    /*static{
        try {
            KeyHelper.write("pubKeyO.key", "priKeyO.key");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/


    public Set<QName> getHeaders() {
        return null;
    }

    public boolean handleMessage(SOAPMessageContext smc) {
        Boolean outbound = (Boolean) smc
                .get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        try {

            key = KeyHelper.read("pubKeyO.key", "priKeyO.key");
            if (outbound) {
                System.out.println("Outbound SOAP message.");
                addSignatureToSoap(signSoap(getSOAPtoByteArray(smc)), smc.getMessage());

            } else {
                key = KeyHelper.read("pubKeyO.key", "priKeyO.key");
                System.out.println("Inbound SOAP message.");
                byte[] signature = getSignatureToSoap(smc);
                // Don't delete this line or change its place. Xico: for some weird reason this line makes the code work.
                smc.getMessage().writeTo(new OutputStream() { @Override public void write(int b) { } });

                verifySoap(signature, getSOAPtoByteArray(smc));

            }

        } catch (Exception e) {
            System.out.println("Caught exception in handleMessage: ");
            e.printStackTrace();
            System.out.println("Continue normal processing...");
        }
        return true;
    }

    private byte[] getSOAPtoByteArray(SOAPMessageContext smc) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            smc.getMessage().writeTo(out);
        } catch (SOAPException | IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private void verifySoap(byte[] cipherDigest, byte[] soap) throws Exception {
        //KeyPair key = generate();

        // verify the signature
        System.out.println("Verifying ...");
        boolean result = verifyDigitalSignature(cipherDigest, soap, key);
        System.out.println("Signature is " + (result ? "right" : "wrong"));

    }

    private byte[] signSoap(byte[] soap) throws Exception {
        //KeyPair key = generate();

        // make digital signature
        System.out.println("Signing ...");
        byte[] cipherDigest = makeDigitalSignature(soap, key);

        // verify the signature
        System.out.println("Verifying ...");
        boolean result = verifyDigitalSignature(cipherDigest, soap, key);
        System.out.println("Signature is " + (result ? "right" : "wrong"));
        return cipherDigest;

    }

    private void addSignatureToSoap(byte[] signature, SOAPMessage msg) throws SOAPException {
        SOAPPart sp = msg.getSOAPPart();
        SOAPEnvelope se = sp.getEnvelope();

        // add header
        SOAPHeader sh = se.getHeader();
        if (sh == null)
            sh = se.addHeader();

        // add header element (name, namespace prefix, namespace)
        Name name = se.createName(ELEMENT_NAME, PREFIX, NAMESPACE);
        SOAPHeaderElement element = sh.addHeaderElement(name);
       // System.out.println("Adding signature to SOAP...");
        // add header element value
        element.addTextNode(printBase64Binary(signature));
    }

    private byte[] getSignatureToSoap(SOAPMessageContext smc) throws SOAPException {
        // get SOAP envelope header
        SOAPMessage msg = smc.getMessage();
        SOAPPart sp = msg.getSOAPPart();
        SOAPEnvelope se = sp.getEnvelope();
        SOAPHeader sh = se.getHeader();

        // check header
        if (sh == null) {
            System.out.println("Header not found.");
            return null;
            // FIXME: exception
        }

        // get first header element
        Name name = se.createName(ELEMENT_NAME, PREFIX, NAMESPACE);
        Iterator it = sh.getChildElements(name);
        // check header element
        if (!it.hasNext()) {
            System.out.println("Header element not found.");
            return null;
            // FIXME: exception
        }
        SOAPElement element = (SOAPElement) it.next();

        // get header element value
        String valueString = element.getValue();
        byte[] signature = parseBase64Binary(valueString);

        // print received header
       // System.out.println("Signature value is:\n" + printHexBinary(signature));

        // Removing Signature
        it.remove();
        element.removeAttribute(name);
        element.removeContents();
        /*sh.removeAttribute(name);
        se.removeAttribute(name);*/

        // put header in a property context
        smc.put(CONTEXT_PROPERTY, signature);
        // set property scope to application client/server class can access it
        smc.setScope(CONTEXT_PROPERTY, MessageContext.Scope.APPLICATION);
        return signature;
    }

    public boolean handleFault(SOAPMessageContext smc) {

        return true;
    }

    // nothing to clean up
    public void close(MessageContext messageContext) {
    }


    private static String cleanInvalidXmlChars(String text) {
        String xml10pattern = "[^"
                + "\u0009\r\n"
                + "\u0020-\uD7FF"
                + "\uE000-\uFFFD"
                + "\ud800\udc00-\udbff\udfff"
                + "]";
        return text.replaceAll(xml10pattern, "");
    }

    /*Digital Signature */

    /** auxiliary method to generate KeyPair */
    private static KeyPair generate() throws Exception {
        // generate an RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);

        return keyGen.generateKeyPair();
    }

    /** auxiliary method to calculate digest from text and cipher it */
    private byte[] makeDigitalSignature(byte[] bytes, KeyPair keyPair) throws Exception {

        // get a message digest object using the specified algorithm
        MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        // calculate the digest and print it out
        messageDigest.update(bytes);
        byte[] digest = messageDigest.digest();
        System.out.println("Digest:");
        System.out.println(printHexBinary(digest));

        // get an RSA cipher object
        Cipher cipher = Cipher.getInstance(ASSYMETRIC_KEY_ALGORITHM);

        // encrypt the plaintext using the private key
        cipher.init(Cipher.ENCRYPT_MODE, key.getPrivate());
        byte[] cipherDigest = cipher.doFinal(digest);

//        System.out.println("Cipher digest:");
  //      System.out.println(printHexBinary(cipherDigest));

        return cipherDigest;
    }

    /**
     * auxiliary method to calculate new digest from text and compare it to the
     * to deciphered digest
     */
    private boolean verifyDigitalSignature(byte[] cipherDigest, byte[] text, KeyPair keyPair) throws Exception {

        // get a message digest object using the SHA-1 algorithm
        MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        // calculate the digest and print it out
        messageDigest.update(text);
        byte[] digest = messageDigest.digest();
        System.out.println("New digest:");
        System.out.println(printHexBinary(digest));

        // get an RSA cipher object
        Cipher cipher = Cipher.getInstance(ASSYMETRIC_KEY_ALGORITHM);

        // decrypt the ciphered digest using the public key
        cipher.init(Cipher.DECRYPT_MODE, key.getPublic());
        byte[] decipheredDigest = cipher.doFinal(cipherDigest);
        System.out.println("Deciphered digest:");
        System.out.println(printHexBinary(decipheredDigest));

        // compare digests
        if (digest.length != decipheredDigest.length)
            return false;

        for (int i = 0; i < digest.length; i++)
            if (digest[i] != decipheredDigest[i])
                return false;
        return true;
    }

}
