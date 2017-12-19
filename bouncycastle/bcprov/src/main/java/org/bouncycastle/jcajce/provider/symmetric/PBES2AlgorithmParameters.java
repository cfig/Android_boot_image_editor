// BEGIN android-added
// Based on org.bouncycastle.jcajce.provider.symmetric.PBEPKCS12

package org.bouncycastle.jcajce.provider.symmetric;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.EncryptionScheme;
import org.bouncycastle.asn1.pkcs.KeyDerivationFunc;
import org.bouncycastle.asn1.pkcs.PBES2Parameters;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jcajce.provider.config.ConfigurableProvider;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseAlgorithmParameters;
import org.bouncycastle.jcajce.provider.symmetric.util.PBE;
import org.bouncycastle.jcajce.provider.util.AlgorithmProvider;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Enumeration;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEParameterSpec;

public class PBES2AlgorithmParameters
{
    private PBES2AlgorithmParameters()
    {

    }

    private static abstract class BasePBEWithHmacAlgorithmParameters
            extends BaseAlgorithmParameters
    {
        private final AlgorithmIdentifier kdf;
        private final String kdfShortName;
        private final int keySize;
        private final ASN1ObjectIdentifier cipherAlgorithm;
        private final String cipherAlgorithmShortName;

        private PBES2Parameters params;

        private BasePBEWithHmacAlgorithmParameters(
                ASN1ObjectIdentifier kdf,
                String kdfShortName,
                int keySize,
                ASN1ObjectIdentifier cipherAlgorithm,
                String cipherAlgorithmShortName) {
            this.kdf = new AlgorithmIdentifier(kdf, DERNull.INSTANCE);
            this.kdfShortName = kdfShortName;
            this.keySize = keySize;
            this.cipherAlgorithm = cipherAlgorithm;
            this.cipherAlgorithmShortName = cipherAlgorithmShortName;
        }

        protected byte[] engineGetEncoded()
        {
            try
            {
                return new DERSequence(new ASN1Encodable[] {
                        PKCSObjectIdentifiers.id_PBES2,
                        params
                }).getEncoded();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Unable to read PBES2 parameters: " + e.toString());
            }
        }

        protected byte[] engineGetEncoded(
                String format)
        {
            if (this.isASN1FormatString(format))
            {
                return engineGetEncoded();
            }

            return null;
        }

        protected AlgorithmParameterSpec localEngineGetParameterSpec(
                Class parameterSpec)
                throws InvalidParameterSpecException
        {
            if (parameterSpec == PBEParameterSpec.class)
            {
                PBKDF2Params pbeParamSpec =
                        (PBKDF2Params) params.getKeyDerivationFunc().getParameters();
                byte[] iv =  ((ASN1OctetString) params.getEncryptionScheme().getParameters())
                        .getOctets();
                return createPBEParameterSpec(pbeParamSpec.getSalt(),
                        pbeParamSpec.getIterationCount().intValue(),
                        iv);
            }

            throw new InvalidParameterSpecException(
                    "unknown parameter spec passed to PBES2 parameters object.");
        }

        protected void engineInit(
                AlgorithmParameterSpec paramSpec)
                throws InvalidParameterSpecException
        {
            if (!(paramSpec instanceof PBEParameterSpec))
            {
                throw new InvalidParameterSpecException(
                        "PBEParameterSpec required to initialise PBES2 algorithm parameters");
            }

            PBEParameterSpec pbeSpec = (PBEParameterSpec)paramSpec;

            byte[] iv;

            AlgorithmParameterSpec algorithmParameterSpec =
                    PBE.Util.getParameterSpecFromPBEParameterSpec(pbeSpec);
            if (algorithmParameterSpec instanceof IvParameterSpec) {
                iv = ((IvParameterSpec) algorithmParameterSpec).getIV();
            } else {
                throw new IllegalArgumentException("Expecting an IV as a parameter");
            }

            this.params = new PBES2Parameters(
                    new KeyDerivationFunc(
                            PKCSObjectIdentifiers.id_PBKDF2,
                            new PBKDF2Params(
                                    pbeSpec.getSalt(), pbeSpec.getIterationCount(), keySize, kdf)),
                    new EncryptionScheme(cipherAlgorithm, new DEROctetString(iv)));
        }

        protected void engineInit(
                byte[] params)
                throws IOException
        {
            // Dual of engineGetEncoded()
            ASN1Sequence seq = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(params));
            Enumeration seqObjects = seq.getObjects();
            ASN1ObjectIdentifier id = (ASN1ObjectIdentifier) seqObjects.nextElement();
            if (!id.getId().equals(PKCSObjectIdentifiers.id_PBES2.getId())) {
                throw new IllegalArgumentException("Invalid PBES2 parameters");
            }
            this.params = PBES2Parameters.getInstance(seqObjects.nextElement());
        }

        protected void engineInit(
                byte[] params,
                String format)
                throws IOException
        {
            if (this.isASN1FormatString(format))
            {
                engineInit(params);
                return;
            }

            throw new IOException("Unknown parameters format in PBES2 parameters object");
        }

        protected String engineToString()
        {
            return "PBES2 " + kdfShortName + " " + cipherAlgorithmShortName + " Parameters";
        }
    }

    public static class PBEWithHmacSHA1AES128AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA1AES128AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA1,
                    "HmacSHA1",
                    16, /* keySize */
                    NISTObjectIdentifiers.id_aes128_CBC,
                    "AES128");
        }
    }

    public static class PBEWithHmacSHA224AES128AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA224AES128AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA224,
                    "HmacSHA224",
                    16, /* keySize */
                    NISTObjectIdentifiers.id_aes128_CBC,
                    "AES128");
        }
    }

    public static class PBEWithHmacSHA256AES128AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA256AES128AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA256,
                    "HmacSHA256",
                    16, /* keySize */
                    NISTObjectIdentifiers.id_aes128_CBC,
                    "AES128");
        }
    }

    public static class PBEWithHmacSHA384AES128AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA384AES128AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA384,
                    "HmacSHA384",
                    16, /* keySize */
                    NISTObjectIdentifiers.id_aes128_CBC,
                    "AES128");
        }
    }

    public static class PBEWithHmacSHA512AES128AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA512AES128AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA512,
                    "HmacSHA512",
                    16, /* keySize */
                    NISTObjectIdentifiers.id_aes128_CBC,
                    "AES128");
        }
    }

    public static class PBEWithHmacSHA1AES256AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA1AES256AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA1,
                    "HmacSHA1",
                    32, /* keySize */
                    NISTObjectIdentifiers.id_aes256_CBC,
                    "AES256");
        }
    }

    public static class PBEWithHmacSHA224AES256AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA224AES256AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA224,
                    "HmacSHA224",
                    32, /* keySize */
                    NISTObjectIdentifiers.id_aes256_CBC,
                    "AES256");
        }
    }

    public static class PBEWithHmacSHA256AES256AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA256AES256AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA256,
                    "HmacSHA256",
                    32, /* keySize */
                    NISTObjectIdentifiers.id_aes256_CBC,
                    "AES256");
        }
    }

    public static class PBEWithHmacSHA384AES256AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA384AES256AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA384,
                    "HmacSHA384",
                    32, /* keySize */
                    NISTObjectIdentifiers.id_aes256_CBC,
                    "AES256");
        }
    }

    public static class PBEWithHmacSHA512AES256AlgorithmParameters
            extends BasePBEWithHmacAlgorithmParameters {
        public PBEWithHmacSHA512AES256AlgorithmParameters() {
            super(PKCSObjectIdentifiers.id_hmacWithSHA512,
                    "HmacSHA512",
                    32, /* keySize */
                    NISTObjectIdentifiers.id_aes256_CBC,
                    "AES256");
        }
    }


    public static class Mappings
            extends AlgorithmProvider
    {
        private static final String PREFIX = PBES2AlgorithmParameters.class.getName();

        public Mappings()
        {
        }

        public void configure(ConfigurableProvider provider)
        {
            int[] keySizes = { 128, 256 };
            int[] shaVariants = { 1, 224, 256, 384, 512 };
            for (int keySize : keySizes) {
                for (int shaVariant : shaVariants) {
                    provider.addAlgorithm(
                            "AlgorithmParameters.PBEWithHmacSHA" + shaVariant + "AndAES_" + keySize,
                            PREFIX + "$PBEWithHmacSHA" + shaVariant + "AES" + keySize
                                    + "AlgorithmParameters");
                }
            }
        }
    }

    /**
     * Helper method to create a PBEParameterSpec with a parameter specification via reflection, as
     * the constructor became available in Java 1.8 and Bouncycastle is at level 1.5.
     */
    private static PBEParameterSpec createPBEParameterSpec(
            byte[] salt, int iterationCount, byte[] iv) {
        try {
            Class<PBEParameterSpec> pbeParameterSpecClass =
                    (Class<PBEParameterSpec>) PBES2AlgorithmParameters.class.getClassLoader()
                            .loadClass("javax.crypto.spec.PBEParameterSpec");
            Constructor<PBEParameterSpec> constructor =
                    pbeParameterSpecClass.getConstructor(new Class[]{
                            byte[].class, int.class, AlgorithmParameterSpec.class });
            return constructor.newInstance(salt, iterationCount, new IvParameterSpec(iv));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Requested creation PBES2 parameters in an SDK that doesn't support them", e);
        }
    }
}
// END android-added