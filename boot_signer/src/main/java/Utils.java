/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.verity;

import java.lang.reflect.Constructor;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.Console;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.util.encoders.Base64;

public class Utils {

    private static final Map<String, String> ID_TO_ALG;
    private static final Map<String, String> ALG_TO_ID;

    static {
        ID_TO_ALG = new HashMap<String, String>();
        ALG_TO_ID = new HashMap<String, String>();

        ID_TO_ALG.put(X9ObjectIdentifiers.ecdsa_with_SHA256.getId(), "SHA256withECDSA");
        ID_TO_ALG.put(X9ObjectIdentifiers.ecdsa_with_SHA384.getId(), "SHA384withECDSA");
        ID_TO_ALG.put(X9ObjectIdentifiers.ecdsa_with_SHA512.getId(), "SHA512withECDSA");
        ID_TO_ALG.put(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId(), "SHA1withRSA");
        ID_TO_ALG.put(PKCSObjectIdentifiers.sha256WithRSAEncryption.getId(), "SHA256withRSA");
        ID_TO_ALG.put(PKCSObjectIdentifiers.sha512WithRSAEncryption.getId(), "SHA512withRSA");

        ALG_TO_ID.put("SHA256withECDSA", X9ObjectIdentifiers.ecdsa_with_SHA256.getId());
        ALG_TO_ID.put("SHA384withECDSA", X9ObjectIdentifiers.ecdsa_with_SHA384.getId());
        ALG_TO_ID.put("SHA512withECDSA", X9ObjectIdentifiers.ecdsa_with_SHA512.getId());
        ALG_TO_ID.put("SHA1withRSA", PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        ALG_TO_ID.put("SHA256withRSA", PKCSObjectIdentifiers.sha256WithRSAEncryption.getId());
        ALG_TO_ID.put("SHA512withRSA", PKCSObjectIdentifiers.sha512WithRSAEncryption.getId());
    }

    private static void loadProviderIfNecessary(String providerClassName) {
        if (providerClassName == null) {
            return;
        }

        final Class<?> klass;
        try {
            final ClassLoader sysLoader = ClassLoader.getSystemClassLoader();
            if (sysLoader != null) {
                klass = sysLoader.loadClass(providerClassName);
            } else {
                klass = Class.forName(providerClassName);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }

        Constructor<?> constructor = null;
        for (Constructor<?> c : klass.getConstructors()) {
            if (c.getParameterTypes().length == 0) {
                constructor = c;
                break;
            }
        }
        if (constructor == null) {
            System.err.println("No zero-arg constructor found for " + providerClassName);
            System.exit(1);
            return;
        }

        final Object o;
        try {
            o = constructor.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }
        if (!(o instanceof Provider)) {
            System.err.println("Not a Provider class: " + providerClassName);
            System.exit(1);
        }

        Security.insertProviderAt((Provider) o, 1);
    }

    static byte[] pemToDer(String pem) throws Exception {
        pem = pem.replaceAll("^-.*", "");
        String base64_der = pem.replaceAll("-.*$", "");
        return Base64.decode(base64_der);
    }

    private static PKCS8EncodedKeySpec decryptPrivateKey(byte[] encryptedPrivateKey)
        throws GeneralSecurityException {
        EncryptedPrivateKeyInfo epkInfo;
        try {
            epkInfo = new EncryptedPrivateKeyInfo(encryptedPrivateKey);
        } catch (IOException ex) {
            // Probably not an encrypted key.
            return null;
        }

        char[] password = System.console().readPassword("Password for the private key file: ");

        SecretKeyFactory skFactory = SecretKeyFactory.getInstance(epkInfo.getAlgName());
        Key key = skFactory.generateSecret(new PBEKeySpec(password));
        Arrays.fill(password, '\0');

        Cipher cipher = Cipher.getInstance(epkInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, key, epkInfo.getAlgParameters());

        try {
            return epkInfo.getKeySpec(cipher);
        } catch (InvalidKeySpecException ex) {
            System.err.println("Password may be bad.");
            throw ex;
        }
    }

    static PrivateKey loadDERPrivateKey(byte[] der) throws Exception {
        PKCS8EncodedKeySpec spec = decryptPrivateKey(der);

        if (spec == null) {
            spec = new PKCS8EncodedKeySpec(der);
        }

        ASN1InputStream bIn = new ASN1InputStream(new ByteArrayInputStream(spec.getEncoded()));
        PrivateKeyInfo pki = PrivateKeyInfo.getInstance(bIn.readObject());
        String algOid = pki.getPrivateKeyAlgorithm().getAlgorithm().getId();

        return KeyFactory.getInstance(algOid).generatePrivate(spec);
    }

    static PrivateKey loadPEMPrivateKey(byte[] pem) throws Exception {
        byte[] der = pemToDer(new String(pem));
        return loadDERPrivateKey(der);
    }

    static PrivateKey loadPEMPrivateKeyFromFile(String keyFname) throws Exception {
        return loadPEMPrivateKey(read(keyFname));
    }

    static PrivateKey loadDERPrivateKeyFromFile(String keyFname) throws Exception {
        return loadDERPrivateKey(read(keyFname));
    }

    static PublicKey loadDERPublicKey(byte[] der) throws Exception {
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(der);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(publicKeySpec);
    }

    static PublicKey loadPEMPublicKey(byte[] pem) throws Exception {
        byte[] der = pemToDer(new String(pem));
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(der);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(publicKeySpec);
    }

    static PublicKey loadPEMPublicKeyFromFile(String keyFname) throws Exception {
        return loadPEMPublicKey(read(keyFname));
    }

    static PublicKey loadDERPublicKeyFromFile(String keyFname) throws Exception {
        return loadDERPublicKey(read(keyFname));
    }

    static X509Certificate loadPEMCertificate(String fname) throws Exception {
        try (FileInputStream fis = new FileInputStream(fname)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(fis);
        }
    }

    private static String getSignatureAlgorithm(Key key) throws Exception {
        if ("EC".equals(key.getAlgorithm())) {
            int curveSize;
            KeyFactory factory = KeyFactory.getInstance("EC");

            if (key instanceof PublicKey) {
                ECPublicKeySpec spec = factory.getKeySpec(key, ECPublicKeySpec.class);
                curveSize = spec.getParams().getCurve().getField().getFieldSize();
            } else if (key instanceof PrivateKey) {
                ECPrivateKeySpec spec = factory.getKeySpec(key, ECPrivateKeySpec.class);
                curveSize = spec.getParams().getCurve().getField().getFieldSize();
            } else {
                throw new InvalidKeySpecException();
            }

            if (curveSize <= 256) {
                return "SHA256withECDSA";
            } else if (curveSize <= 384) {
                return "SHA384withECDSA";
            } else {
                return "SHA512withECDSA";
            }
        } else if ("RSA".equals(key.getAlgorithm())) {
            return "SHA256withRSA";
        } else {
            throw new IllegalArgumentException("Unsupported key type " + key.getAlgorithm());
        }
    }

    static AlgorithmIdentifier getSignatureAlgorithmIdentifier(Key key) throws Exception {
        String id = ALG_TO_ID.get(getSignatureAlgorithm(key));

        if (id == null) {
            throw new IllegalArgumentException("Unsupported key type " + key.getAlgorithm());
        }

        return new AlgorithmIdentifier(new ASN1ObjectIdentifier(id));
    }

    static boolean verify(PublicKey key, byte[] input, byte[] signature,
            AlgorithmIdentifier algId) throws Exception {
        String algName = ID_TO_ALG.get(algId.getObjectId().getId());

        if (algName == null) {
            throw new IllegalArgumentException("Unsupported algorithm " + algId.getObjectId());
        }

        Signature verifier = Signature.getInstance(algName);
        verifier.initVerify(key);
        verifier.update(input);

        return verifier.verify(signature);
    }

    static byte[] sign(PrivateKey privateKey, byte[] input) throws Exception {
        Signature signer = Signature.getInstance(getSignatureAlgorithm(privateKey));
        signer.initSign(privateKey);
        signer.update(input);
        return signer.sign();
    }

    static byte[] read(String fname) throws Exception {
        long offset = 0;
        File f = new File(fname);
        long length = f.length();
        byte[] image = new byte[(int)length];
        FileInputStream fis = new FileInputStream(f);
        while (offset < length) {
            offset += fis.read(image, (int)offset, (int)(length - offset));
        }
        fis.close();
        return image;
    }

    static void write(byte[] data, String fname) throws Exception{
        FileOutputStream out = new FileOutputStream(fname);
        out.write(data);
        out.close();
    }
}
