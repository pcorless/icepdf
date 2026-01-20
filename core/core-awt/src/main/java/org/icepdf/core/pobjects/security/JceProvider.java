package org.icepdf.core.pobjects.security;

import org.icepdf.core.util.Defs;

import java.lang.reflect.InvocationTargetException;
import java.security.Provider;
import java.security.Security;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Load Bouncy Castle JCE provider with the option to use a customer provider using the system property
 * -Dorg.icepdf.core.security.jceProvider=myProviderOfChoice.  Bouncy Castle is required for loading
 * some encrypted documents as well as adding digital signatures.
 */
public class JceProvider {

    private static final Logger logger =
            Logger.getLogger(JceProvider.class.toString());

    /**
     * Try and load a JCE provider specified by org.icepdf.core.security.jceProvider system property.  If not set
     * fall back on using BouncyCastleProvider.
     */
    public static void loadProvider() {
        // Load security handler from system property if possible
        String defaultSecurityProvider = "org.bouncycastle.jce.provider.BouncyCastleProvider";

        // check system property security provider
        String customSecurityProvider = Defs.sysProperty("org.icepdf.core.security.jceProvider");

        // if no custom security provider load default security provider
        if (customSecurityProvider != null) {
            defaultSecurityProvider = customSecurityProvider;
        }
        try {
            // try and create a new provider
            Object provider = Class.forName(defaultSecurityProvider).getDeclaredConstructor().newInstance();
            Security.addProvider((Provider) provider);
        } catch (ClassNotFoundException e) {
            logger.log(Level.FINE, "Optional BouncyCastle security provider not found");
        } catch (InstantiationException e) {
            logger.log(Level.FINE, "Optional BouncyCastle security provider could not be instantiated");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.log(Level.FINE, "Optional BouncyCastle security provider could not be created");
        }
    }
}
