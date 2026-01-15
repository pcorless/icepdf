package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs11SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.io.File;
import java.util.prefs.Preferences;

import static org.icepdf.ri.common.preferences.SigningPreferencesPanel.PKCS_11_TYPE;
import static org.icepdf.ri.common.preferences.SigningPreferencesPanel.PKCS_12_TYPE;

/**
 * Factory class for creating a SignerHandler instance based on the keystore type.
 *
 * @since 7.3
 */
public class PkcsSignerFactory {

    private PkcsSignerFactory() {
    }

    /**
     * Factory method for creating a SignerHandler instance based on the keystore type. Two instance types are supported
     * PKCS12 and PKCS11.
     *
     * @param passwordDialogCallbackHandler callback handler for password requests
     * @return SignerHandler instance based on the keystore type.  Null if the keystore type is not supported.
     */
    public static SignerHandler getInstance(PasswordDialogCallbackHandler passwordDialogCallbackHandler) {
        ViewerPropertiesManager propertiesManager = ViewerPropertiesManager.getInstance();
        Preferences preferences = propertiesManager.getPreferences();
        String keyStoreType = preferences.get(ViewerPropertiesManager.PROPERTY_PKCS_KEYSTORE_TYPE, "");
        String tsaUrl = preferences.get(ViewerPropertiesManager.PROPERTY_SIGNATURE_TSA_URL, "");
        if (keyStoreType.equals(PKCS_12_TYPE)) {
            passwordDialogCallbackHandler.setType(PKCS_12_TYPE);
            String keyStorePath = preferences.get(ViewerPropertiesManager.PROPERTY_PKCS12_PROVIDER_KEYSTORE_PATH, "");
            File keystoreFile = new File(keyStorePath);
            return new Pkcs12SignerHandler(tsaUrl, keystoreFile, null, passwordDialogCallbackHandler);
        } else if (keyStoreType.equals(PKCS_11_TYPE)) {
            passwordDialogCallbackHandler.setType(PKCS_11_TYPE);
            String configPath = preferences.get(ViewerPropertiesManager.PROPERTY_PKCS11_PROVIDER_CONFIG_PATH, "");
            return new Pkcs11SignerHandler(tsaUrl, configPath, null, passwordDialogCallbackHandler);
        }
        return null;
    }
}
