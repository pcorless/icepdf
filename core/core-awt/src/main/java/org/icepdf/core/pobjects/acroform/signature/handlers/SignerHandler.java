package org.icepdf.core.pobjects.acroform.signature.handlers;

import org.bouncycastle.cms.CMSException;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public interface SignerHandler {

    byte[] signData(byte[] data) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException,
            CertificateException, OperatorCreationException, CMSException, IOException;
}
