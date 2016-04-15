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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.util.ASN1Dump;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 *    AndroidVerifiedBootSignature DEFINITIONS ::=
 *    BEGIN
 *        formatVersion ::= INTEGER
 *        certificate ::= Certificate
 *        algorithmIdentifier ::= SEQUENCE {
 *            algorithm OBJECT IDENTIFIER,
 *            parameters ANY DEFINED BY algorithm OPTIONAL
 *        }
 *        authenticatedAttributes ::= SEQUENCE {
 *            target CHARACTER STRING,
 *            length INTEGER
 *        }
 *        signature ::= OCTET STRING
 *     END
 */

public class BootSignature extends ASN1Object
{
    private ASN1Integer             formatVersion;
    private ASN1Encodable           certificate;
    private AlgorithmIdentifier     algorithmIdentifier;
    private DERPrintableString      target;
    private ASN1Integer             length;
    private DEROctetString          signature;
    private PublicKey               publicKey;

    private static final int FORMAT_VERSION = 1;

    /**
     * Initializes the object for signing an image file
     * @param target Target name, included in the signed data
     * @param length Length of the image, included in the signed data
     */
    public BootSignature(String target, int length) {
        this.formatVersion = new ASN1Integer(FORMAT_VERSION);
        this.target = new DERPrintableString(target);
        this.length = new ASN1Integer(length);
    }

    /**
     * Initializes the object for verifying a signed image file
     * @param signature Signature footer
     */
    public BootSignature(byte[] signature)
            throws Exception {
        ASN1InputStream stream = new ASN1InputStream(signature);
        ASN1Sequence sequence = (ASN1Sequence) stream.readObject();

        formatVersion = (ASN1Integer) sequence.getObjectAt(0);
        if (formatVersion.getValue().intValue() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported format version");
        }

        certificate = sequence.getObjectAt(1);
        byte[] encoded = ((ASN1Object) certificate).getEncoded();
        ByteArrayInputStream bis = new ByteArrayInputStream(encoded);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate c = (X509Certificate) cf.generateCertificate(bis);
        publicKey = c.getPublicKey();

        ASN1Sequence algId = (ASN1Sequence) sequence.getObjectAt(2);
        algorithmIdentifier = new AlgorithmIdentifier(
            (ASN1ObjectIdentifier) algId.getObjectAt(0));

        ASN1Sequence attrs = (ASN1Sequence) sequence.getObjectAt(3);
        target = (DERPrintableString) attrs.getObjectAt(0);
        length = (ASN1Integer) attrs.getObjectAt(1);

        this.signature = (DEROctetString) sequence.getObjectAt(4);
    }

    public ASN1Object getAuthenticatedAttributes() {
        ASN1EncodableVector attrs = new ASN1EncodableVector();
        attrs.add(target);
        attrs.add(length);
        return new DERSequence(attrs);
    }

    public byte[] getEncodedAuthenticatedAttributes() throws IOException {
        return getAuthenticatedAttributes().getEncoded();
    }

    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return algorithmIdentifier;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public byte[] getSignature() {
        return signature.getOctets();
    }

    public void setSignature(byte[] sig, AlgorithmIdentifier algId) {
        algorithmIdentifier = algId;
        signature = new DEROctetString(sig);
    }

    public void setCertificate(X509Certificate cert)
            throws Exception, IOException, CertificateEncodingException {
        ASN1InputStream s = new ASN1InputStream(cert.getEncoded());
        certificate = s.readObject();
    }

    public byte[] generateSignableImage(byte[] image) throws IOException {
        byte[] attrs = getEncodedAuthenticatedAttributes();
        byte[] signable = Arrays.copyOf(image, image.length + attrs.length);
        for (int i=0; i < attrs.length; i++) {
            signable[i+image.length] = attrs[i];
        }
        return signable;
    }

    public byte[] sign(byte[] image, PrivateKey key) throws Exception {
        byte[] signable = generateSignableImage(image);
        return Utils.sign(key, signable);
    }

    public boolean verify(byte[] image) throws Exception {
        if (length.getValue().intValue() != image.length) {
            throw new IllegalArgumentException("Invalid image length");
        }

        byte[] signable = generateSignableImage(image);
        return Utils.verify(publicKey, signable, signature.getOctets(),
                    algorithmIdentifier);
    }

    public ASN1Primitive toASN1Primitive() {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(formatVersion);
        v.add(certificate);
        v.add(algorithmIdentifier);
        v.add(getAuthenticatedAttributes());
        v.add(signature);
        return new DERSequence(v);
    }

    public static int getSignableImageSize(byte[] data) throws Exception {
        if (!Arrays.equals(Arrays.copyOfRange(data, 0, 8),
                "ANDROID!".getBytes("US-ASCII"))) {
            throw new IllegalArgumentException("Invalid image header: missing magic");
        }

        ByteBuffer image = ByteBuffer.wrap(data);
        image.order(ByteOrder.LITTLE_ENDIAN);

        image.getLong(); // magic
        int kernelSize = image.getInt();
        image.getInt(); // kernel_addr
        int ramdskSize = image.getInt();
        image.getInt(); // ramdisk_addr
        int secondSize = image.getInt();
        image.getLong(); // second_addr + tags_addr
        int pageSize = image.getInt();

        int length = pageSize // include the page aligned image header
                + ((kernelSize + pageSize - 1) / pageSize) * pageSize
                + ((ramdskSize + pageSize - 1) / pageSize) * pageSize
                + ((secondSize + pageSize - 1) / pageSize) * pageSize;

        length = ((length + pageSize - 1) / pageSize) * pageSize;

        if (length <= 0) {
            throw new IllegalArgumentException("Invalid image header: invalid length");
        }

        return length;
    }

    public static void doSignature( String target,
                                    String imagePath,
                                    String keyPath,
                                    String certPath,
                                    String outPath) throws Exception {

        byte[] image = Utils.read(imagePath);
        int signableSize = getSignableImageSize(image);

        if (signableSize < image.length) {
            System.err.println("NOTE: truncating file " + imagePath +
                    " from " + image.length + " to " + signableSize + " bytes");
            image = Arrays.copyOf(image, signableSize);
        } else if (signableSize > image.length) {
            throw new IllegalArgumentException("Invalid image: too short, expected " +
                    signableSize + " bytes");
        }

        BootSignature bootsig = new BootSignature(target, image.length);

        X509Certificate cert = Utils.loadPEMCertificate(certPath);
        bootsig.setCertificate(cert);

        PrivateKey key = Utils.loadDERPrivateKeyFromFile(keyPath);
        bootsig.setSignature(bootsig.sign(image, key),
            Utils.getSignatureAlgorithmIdentifier(key));

        byte[] encoded_bootsig = bootsig.getEncoded();
        byte[] image_with_metadata = Arrays.copyOf(image, image.length + encoded_bootsig.length);

        System.arraycopy(encoded_bootsig, 0, image_with_metadata,
                image.length, encoded_bootsig.length);

        Utils.write(image_with_metadata, outPath);
    }

    public static void verifySignature(String imagePath) throws Exception {
        byte[] image = Utils.read(imagePath);
        int signableSize = getSignableImageSize(image);

        if (signableSize >= image.length) {
            throw new IllegalArgumentException("Invalid image: not signed");
        }

        byte[] signature = Arrays.copyOfRange(image, signableSize, image.length);
        BootSignature bootsig = new BootSignature(signature);

        try {
            if (bootsig.verify(Arrays.copyOf(image, signableSize))) {
                System.err.println("Signature is VALID");
                System.exit(0);
            } else {
                System.err.println("Signature is INVALID");
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        System.exit(1);
    }

    /* Example usage for signing a boot image using dev keys:
        java -cp \
            ../../../out/host/common/obj/JAVA_LIBRARIES/BootSignature_intermediates/ \
                classes/com.android.verity.BootSignature \
            /boot \
            ../../../out/target/product/$PRODUCT/boot.img \
            ../../../build/target/product/security/verity.pk8 \
            ../../../build/target/product/security/verity.x509.pem \
            /tmp/boot.img.signed
    */
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        if ("-verify".equals(args[0])) {
            /* args[1] is the path to a signed boot image */
            verifySignature(args[1]);
        } else {
            /* args[0] is the target name, typically /boot
               args[1] is the path to a boot image to sign
               args[2] is the path to a private key
               args[3] is the path to the matching public key certificate
               args[4] is the path where to output the signed boot image
            */
            doSignature(args[0], args[1], args[2], args[3], args[4]);
        }
    }
}
