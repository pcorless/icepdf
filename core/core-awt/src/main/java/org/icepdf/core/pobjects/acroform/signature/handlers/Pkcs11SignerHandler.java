package org.icepdf.core.pobjects.acroform.signature.handlers;

import java.security.*;

/**
 * <p>Pkcs11SignerHandler tries to do some of the boilerplate work to set up a Pkcs#11 provider.  The configuration file
 * must point at a shared-object library (.so on linux) and dynamic-link library (.dll on windows or .dylib on macOS).
 * Consult the vendors of the PKCS#11 cryptographic API documentation for more information.
 * More information can be found at https://docs.oracle.com/en/java/javase/11/security/pkcs11-reference-guide1
 * .html#GUID-97F1E537-CB59-4C7F-AB6B-05D4DBD69AC0
 * </p>
 * A sample config file might be as follows:
 * <pre>
 *     # /opt/bar/cfg/pkcs11.cfg
 *     name = vendor_name
 *     library = /opt/foo/lib/libPKCS11.so
 *     slot = 0
 * </pre>
 *
 * <p>The SunPKCS11 provider, in contrast to most other providers, does not implement cryptographic algorithms itself.
 * Instead, it acts as a bridge between the Java JCA and JCE APIs and the native PKCS#11 cryptographic API, translating
 * the calls and conventions between the two.
 * </p>
 *
 * <p>This means that Java applications calling standard JCA and JCE APIs can, without modification, take advantage of
 * algorithms offered by the underlying PKCS#11 implementations, such as, for example,</p>
 * <ul>
 *     <li>Cryptographic smartcards,</li>
 *     <li>Hardware cryptographic accelerators, and </li>
 *     <li>High performance software implementations.</li>
 * </ul>
 * <p>For additional debugging info, users can start or restart the Java processes with one of the following
 * options:</p>
 * <ul>
 *     <li>For general SunPKCS11 provider debugging info: -Djava.security.debug=sunpkcs11</li>
 *     <li>For PKCS#11 keystore specific debugging info: -Djava.security.debug=pkcs11keystore</p></li>
 * </ul>
 */
public class Pkcs11SignerHandler extends SignerHandler {

    private String configName;

    public Pkcs11SignerHandler(String configName, String certAlias, PasswordCallbackHandler callbackHandler) {
        super(certAlias, callbackHandler);
        this.configName = configName;
    }

    @Override
    protected KeyStore buildKeyStore() throws KeyStoreException {
        Provider provider = Security.getProvider("SunPKCS11");
        provider.configure(this.configName);
        KeyStore.CallbackHandlerProtection chp = new KeyStore.CallbackHandlerProtection(this.callbackHandler);
        KeyStore.Builder builder = KeyStore.Builder.newInstance("PKCS11", provider, chp);
        return builder.getKeyStore();
    }

    @Override
    protected PrivateKey getPrivateKey(KeyStore keyStore) throws KeyStoreException, UnrecoverableKeyException,
            NoSuchAlgorithmException {
        return (PrivateKey) keyStore.getKey(certAlias, null); // should pull password from callbackHandler
//        return (PrivateKey) keyStore.getKey(certAlias, callbackHandler.getPassword());
    }

}
