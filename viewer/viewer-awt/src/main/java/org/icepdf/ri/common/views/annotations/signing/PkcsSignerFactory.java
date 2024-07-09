package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs11SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.Pkcs12SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.io.File;
import java.util.prefs.Preferences;

import static org.icepdf.ri.common.preferences.SigningPreferencesPanel.PKCS_11_TYPE;
import static org.icepdf.ri.common.preferences.SigningPreferencesPanel.PKCS_12_TYPE;

public class PkcsSignerFactory {

    private PkcsSignerFactory() {
    }

    public static SignerHandler getInstance(PasswordDialogCallbackHandler passwordDialogCallbackHandler) {
        ViewerPropertiesManager propertiesManager = ViewerPropertiesManager.getInstance();
        Preferences preferences = propertiesManager.getPreferences();
        String keyStoreType = preferences.get(ViewerPropertiesManager.PROPERTY_PKCS_KEYSTORE_TYPE, "");
        if (keyStoreType.equals(PKCS_12_TYPE)) {
            passwordDialogCallbackHandler.setType(PKCS_12_TYPE);
            String keyStorePath = preferences.get(ViewerPropertiesManager.PROPERTY_PKCS12_PROVIDER_KEYSTORE_PATH, "");
            File keystoreFile = new File(keyStorePath);
            return new Pkcs12SignerHandler(keystoreFile, null, passwordDialogCallbackHandler);
        } else if (keyStoreType.equals(PKCS_11_TYPE)) {
            passwordDialogCallbackHandler.setType(PKCS_11_TYPE);
            String configPath = preferences.get(ViewerPropertiesManager.PROPERTY_PKCS11_PROVIDER_CONFIG_PATH, "");
            return new Pkcs11SignerHandler(configPath, null, passwordDialogCallbackHandler);
        }
        return null;
    }
}
