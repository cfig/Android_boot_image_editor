package org.bouncycastle.jcajce.provider.symmetric;

import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
// Android-removed: Unsupported algorithms
// import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.jcajce.provider.config.ConfigurableProvider;
import org.bouncycastle.jcajce.provider.symmetric.util.BCPBEKey;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseAlgorithmParameters;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseSecretKeyFactory;
import org.bouncycastle.jcajce.provider.symmetric.util.PBE;
import org.bouncycastle.jcajce.provider.util.AlgorithmProvider;
import org.bouncycastle.jcajce.spec.PBKDF2KeySpec;

public class PBEPBKDF2
{
    private PBEPBKDF2()
    {

    }

    // BEGIN Android-removed: Unsupported algorithms
    /*
    public static class AlgParams
        extends BaseAlgorithmParameters
    {
        PBKDF2Params params;

        protected byte[] engineGetEncoded()
        {
            try
            {
                return params.getEncoded(ASN1Encoding.DER);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Oooops! " + e.toString());
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
            Class paramSpec)
            throws InvalidParameterSpecException
        {
            if (paramSpec == PBEParameterSpec.class)
            {
                return new PBEParameterSpec(params.getSalt(),
                                params.getIterationCount().intValue());
            }

            throw new InvalidParameterSpecException("unknown parameter spec passed to PBKDF2 PBE parameters object.");
        }

        protected void engineInit(
            AlgorithmParameterSpec paramSpec)
            throws InvalidParameterSpecException
        {
            if (!(paramSpec instanceof PBEParameterSpec))
            {
                throw new InvalidParameterSpecException("PBEParameterSpec required to initialise a PBKDF2 PBE parameters algorithm parameters object");
            }

            PBEParameterSpec    pbeSpec = (PBEParameterSpec)paramSpec;

            this.params = new PBKDF2Params(pbeSpec.getSalt(),
                                pbeSpec.getIterationCount());
        }

        protected void engineInit(
            byte[] params)
            throws IOException
        {
            this.params = PBKDF2Params.getInstance(ASN1Primitive.fromByteArray(params));
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

            throw new IOException("Unknown parameters format in PBKDF2 parameters object");
        }

        protected String engineToString()
        {
            return "PBKDF2 Parameters";
        }
    }
    */
    // END Android-removed: Unsupported algorithms

    public static class BasePBKDF2
        extends BaseSecretKeyFactory
    {
        private int scheme;
        // BEGIN Android-added: Allow to specify a key using only the password.
        private int keySizeInBits;
        private int ivSizeInBits;
        // END Android-added: Allow to specify a key using only the password.
        private int defaultDigest;

        public BasePBKDF2(String name, int scheme)
        {
            this(name, scheme, SHA1);
        }

        // BEGIN Android-changed: Allow to specify a key using only the password.
        // public BasePBKDF2(String name, int scheme, int defaultDigest)
        private BasePBKDF2(
                String name, int scheme, int digest, int keySizeInBits, int ivSizeInBits)
        // END Android-changed: Allow to specify a key using only the password.
        {
            super(name, PKCSObjectIdentifiers.id_PBKDF2);

            this.scheme = scheme;
            // BEGIN Android-added: Support key-restricted versions.
            this.keySizeInBits = keySizeInBits;
            this.ivSizeInBits = ivSizeInBits;
            // END Android-added: Support key-restricted versions.
            this.defaultDigest = digest;
        }

        // BEGIN Android-added: Allow to specify a key using only the password.
        private BasePBKDF2(String name, int scheme, int digest) {
            this(name, scheme, digest, 0, 0);
        }
        // END Android-added: Allow to specify a key using only the password.

        protected SecretKey engineGenerateSecret(
            KeySpec keySpec)
            throws InvalidKeySpecException
        {
            if (keySpec instanceof PBEKeySpec)
            {
                PBEKeySpec pbeSpec = (PBEKeySpec)keySpec;

                // BEGIN Android-added: Allow to specify a key using only the password.
                // The key will be generated later when other parameters are known.
                if (pbeSpec.getSalt() == null
                        && pbeSpec.getIterationCount() == 0
                        && pbeSpec.getKeyLength() == 0
                        && pbeSpec.getPassword().length > 0
                        && keySizeInBits != 0) {
                    return new BCPBEKey(
                            this.algName, this.algOid, scheme, defaultDigest, keySizeInBits,
                            ivSizeInBits, pbeSpec,
                            // cipherParameters, to be generated when the PBE parameters are known.
                            null);
                }
                // END Android-added: Allow to specify a key using only the password.

                if (pbeSpec.getSalt() == null)
                {
                    throw new InvalidKeySpecException("missing required salt");
                }

                if (pbeSpec.getIterationCount() <= 0)
                {
                    throw new InvalidKeySpecException("positive iteration count required: "
                        + pbeSpec.getIterationCount());
                }

                if (pbeSpec.getKeyLength() <= 0)
                {
                    throw new InvalidKeySpecException("positive key length required: "
                        + pbeSpec.getKeyLength());
                }

                if (pbeSpec.getPassword().length == 0)
                {
                    throw new IllegalArgumentException("password empty");
                }

                if (pbeSpec instanceof PBKDF2KeySpec)
                {
                    PBKDF2KeySpec spec = (PBKDF2KeySpec)pbeSpec;

                    int digest = getDigestCode(spec.getPrf().getAlgorithm());
                    int keySize = pbeSpec.getKeyLength();
                    int ivSize = -1;    // JDK 1,2 and earlier does not understand simplified version.
                    CipherParameters param = PBE.Util.makePBEMacParameters(pbeSpec, scheme, digest, keySize);

                    return new BCPBEKey(this.algName, this.algOid, scheme, digest, keySize, ivSize, pbeSpec, param);
                }
                else
                {
                    int digest = defaultDigest;
                    int keySize = pbeSpec.getKeyLength();
                    int ivSize = -1;    // JDK 1,2 and earlier does not understand simplified version.
                    CipherParameters param = PBE.Util.makePBEMacParameters(pbeSpec, scheme, digest, keySize);

                    return new BCPBEKey(this.algName, this.algOid, scheme, digest, keySize, ivSize, pbeSpec, param);
                }
            }

            throw new InvalidKeySpecException("Invalid KeySpec");
        }


        private int getDigestCode(ASN1ObjectIdentifier algorithm)
            throws InvalidKeySpecException
        {
            // BEGIN Android-removed: Unsupported algorithms
            /*
            if (algorithm.equals(CryptoProObjectIdentifiers.gostR3411Hmac))
            {
                return GOST3411;
            }
            else
            */
            // END Android-removed: Unsupported algorithms
            if (algorithm.equals(PKCSObjectIdentifiers.id_hmacWithSHA1))
            {
                return SHA1;
            }
            else if (algorithm.equals(PKCSObjectIdentifiers.id_hmacWithSHA256))
            {
                return SHA256;
            }
            else if (algorithm.equals(PKCSObjectIdentifiers.id_hmacWithSHA224))
            {
                return SHA224;
            }
            else if (algorithm.equals(PKCSObjectIdentifiers.id_hmacWithSHA384))
            {
                return SHA384;
            }
            else if (algorithm.equals(PKCSObjectIdentifiers.id_hmacWithSHA512))
            {
                return SHA512;
            }

            throw new InvalidKeySpecException("Invalid KeySpec: unknown PRF algorithm " + algorithm);
        }
    }

    // BEGIN Android-removed: Unsupported algorithms
    /*
    public static class PBKDF2withUTF8
        extends BasePBKDF2
    {
        public PBKDF2withUTF8()
        {
            super("PBKDF2", PKCS5S2_UTF8);
        }
    }

    public static class PBKDF2withSHA224
        extends BasePBKDF2
    {
        public PBKDF2withSHA224()
        {
            super("PBKDF2", PKCS5S2_UTF8, SHA224);
        }
    }

    public static class PBKDF2withSHA256
        extends BasePBKDF2
    {
        public PBKDF2withSHA256()
        {
            super("PBKDF2", PKCS5S2_UTF8, SHA256);
        }
    }

    public static class PBKDF2withSHA384
        extends BasePBKDF2
    {
        public PBKDF2withSHA384()
        {
            super("PBKDF2", PKCS5S2_UTF8, SHA384);
        }
    }
    public static class PBKDF2withSHA512
        extends BasePBKDF2
    {
        public PBKDF2withSHA512()
        {
            super("PBKDF2", PKCS5S2_UTF8, SHA512);
        }
    }

    public static class PBKDF2with8BIT
        extends BasePBKDF2
    {
        public PBKDF2with8BIT()
        {
            super("PBKDF2", PKCS5S2);
        }
    }
    */
    // END Android-removed: Unsupported algorithms

    // BEGIN Android-added: Android implementations of PBKDF2 algorithms.
    // See note in Mappings below.
    public static class BasePBKDF2WithHmacSHA1 extends BasePBKDF2 {
        public BasePBKDF2WithHmacSHA1(String name, int scheme)
        {
            super(name, scheme, SHA1);
        }
    }

    public static class PBKDF2WithHmacSHA1UTF8
            extends BasePBKDF2WithHmacSHA1
    {
        public PBKDF2WithHmacSHA1UTF8()
        {
            super("PBKDF2WithHmacSHA1", PKCS5S2_UTF8);
        }
    }

    public static class PBKDF2WithHmacSHA18BIT
            extends BasePBKDF2WithHmacSHA1
    {
        public PBKDF2WithHmacSHA18BIT()
        {
            super("PBKDF2WithHmacSHA1And8bit", PKCS5S2);
        }
    }

    public static class BasePBKDF2WithHmacSHA224 extends BasePBKDF2 {
        public BasePBKDF2WithHmacSHA224(String name, int scheme)
        {
            super(name, scheme, SHA224);
        }
    }

    public static class PBKDF2WithHmacSHA224UTF8
            extends BasePBKDF2WithHmacSHA224
    {
        public PBKDF2WithHmacSHA224UTF8()
        {
            super("PBKDF2WithHmacSHA224", PKCS5S2_UTF8);
        }
    }

    public static class BasePBKDF2WithHmacSHA256 extends BasePBKDF2 {
        public BasePBKDF2WithHmacSHA256(String name, int scheme)
        {
            super(name, scheme, SHA256);
        }
    }

    public static class PBKDF2WithHmacSHA256UTF8
            extends BasePBKDF2WithHmacSHA256
    {
        public PBKDF2WithHmacSHA256UTF8()
        {
            super("PBKDF2WithHmacSHA256", PKCS5S2_UTF8);
        }
    }


    public static class BasePBKDF2WithHmacSHA384 extends BasePBKDF2 {
        public BasePBKDF2WithHmacSHA384(String name, int scheme)
        {
            super(name, scheme, SHA384);
        }
    }

    public static class PBKDF2WithHmacSHA384UTF8
            extends BasePBKDF2WithHmacSHA384
    {
        public PBKDF2WithHmacSHA384UTF8()
        {
            super("PBKDF2WithHmacSHA384", PKCS5S2_UTF8);
        }
    }

    public static class BasePBKDF2WithHmacSHA512 extends BasePBKDF2 {
        public BasePBKDF2WithHmacSHA512(String name, int scheme)
        {
            super(name, scheme, SHA512);
        }
    }

    public static class PBKDF2WithHmacSHA512UTF8
            extends BasePBKDF2WithHmacSHA512
    {
        public PBKDF2WithHmacSHA512UTF8()
        {
            super("PBKDF2WithHmacSHA512", PKCS5S2_UTF8);
        }
    }

    public static class PBEWithHmacSHA1AndAES_128
            extends BasePBKDF2 {
        public PBEWithHmacSHA1AndAES_128() {
            super("PBEWithHmacSHA1AndAES_128", PKCS5S2_UTF8, SHA1, 128, 128);
        }
    }

    public static class PBEWithHmacSHA224AndAES_128
            extends BasePBKDF2 {
        public PBEWithHmacSHA224AndAES_128() {
            super("PBEWithHmacSHA224AndAES_128", PKCS5S2_UTF8, SHA224, 128, 128);
        }
    }

    public static class PBEWithHmacSHA256AndAES_128
            extends BasePBKDF2 {
        public PBEWithHmacSHA256AndAES_128() {
            super("PBEWithHmacSHA256AndAES_128", PKCS5S2_UTF8, SHA256, 128, 128);
        }
    }

    public static class PBEWithHmacSHA384AndAES_128
            extends BasePBKDF2 {
        public PBEWithHmacSHA384AndAES_128() {
            super("PBEWithHmacSHA384AndAES_128", PKCS5S2_UTF8, SHA384, 128, 128);
        }
    }

    public static class PBEWithHmacSHA512AndAES_128
            extends BasePBKDF2 {
        public PBEWithHmacSHA512AndAES_128() {
            super("PBEWithHmacSHA512AndAES_128", PKCS5S2_UTF8, SHA512, 128, 128);
        }
    }


    public static class PBEWithHmacSHA1AndAES_256
            extends BasePBKDF2 {
        public PBEWithHmacSHA1AndAES_256() {
            super("PBEWithHmacSHA1AndAES_256", PKCS5S2_UTF8, SHA1, 256, 128);
        }
    }

    public static class PBEWithHmacSHA224AndAES_256
            extends BasePBKDF2 {
        public PBEWithHmacSHA224AndAES_256() {
            super("PBEWithHmacSHA224AndAES_256", PKCS5S2_UTF8, SHA224, 256, 128);
        }
    }

    public static class PBEWithHmacSHA256AndAES_256
            extends BasePBKDF2 {
        public PBEWithHmacSHA256AndAES_256() {
            super("PBEWithHmacSHA256AndAES_256", PKCS5S2_UTF8, SHA256, 256, 128);
        }
    }

    public static class PBEWithHmacSHA384AndAES_256
            extends BasePBKDF2 {
        public PBEWithHmacSHA384AndAES_256() {
            super("PBEWithHmacSHA384AndAES_256", PKCS5S2_UTF8, SHA384, 256, 128);
        }
    }

    public static class PBEWithHmacSHA512AndAES_256
            extends BasePBKDF2 {
        public PBEWithHmacSHA512AndAES_256() {
            super("PBEWithHmacSHA512AndAES_256", PKCS5S2_UTF8, SHA512, 256, 128);
        }
    }
    // END Android-added: Android implementations of PBKDF2 algorithms.

    public static class Mappings
        extends AlgorithmProvider
    {
        private static final String PREFIX = PBEPBKDF2.class.getName();

        public Mappings()
        {
        }

        public void configure(ConfigurableProvider provider)
        {
            // Android-note: Provided classes differ significantly from upstream.
            // Before BC 1.56, this class was omitted in Android and the algorithms we desired
            // were provided in org.bouncycastle.jcajce.provider.digest.SHA1.  During that
            // time, Android added some additional versions of these algorithms for fixed key sizes.
            // BC eventually consolidated the algorithms into this class.  As a result, when
            // upgrading to BC 1.56, we added this class but replaced its contents with
            // our versions.
            // BEGIN Android-removed: Bouncy Castle versions of algorithms.
            /*
            provider.addAlgorithm("AlgorithmParameters.PBKDF2", PREFIX + "$AlgParams");
            provider.addAlgorithm("Alg.Alias.AlgorithmParameters." + PKCSObjectIdentifiers.id_PBKDF2, "PBKDF2");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2", PREFIX + "$PBKDF2withUTF8");
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory.PBKDF2WITHHMACSHA1", "PBKDF2");
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory.PBKDF2WITHHMACSHA1ANDUTF8", "PBKDF2");
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory." + PKCSObjectIdentifiers.id_PBKDF2, "PBKDF2");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WITHASCII", PREFIX + "$PBKDF2with8BIT");
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory.PBKDF2WITH8BIT", "PBKDF2WITHASCII");
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory.PBKDF2WITHHMACSHA1AND8BIT", "PBKDF2WITHASCII");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WITHHMACSHA224", PREFIX + "$PBKDF2withSHA224");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WITHHMACSHA256", PREFIX + "$PBKDF2withSHA256");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WITHHMACSHA384", PREFIX + "$PBKDF2withSHA384");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WITHHMACSHA512", PREFIX + "$PBKDF2withSHA512");
            */
            // END Android-removed: Bouncy Castle versions of algorithms.
            // BEGIN Android-added: Android versions of algorithms.
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory.PBKDF2WithHmacSHA1AndUTF8", "PBKDF2WithHmacSHA1");
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory.PBKDF2with8BIT", "PBKDF2WithHmacSHA1And8BIT");
            provider.addAlgorithm("Alg.Alias.SecretKeyFactory.PBKDF2withASCII", "PBKDF2WithHmacSHA1And8BIT");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WithHmacSHA1", PREFIX + "$PBKDF2WithHmacSHA1UTF8");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WithHmacSHA224", PREFIX + "$PBKDF2WithHmacSHA224UTF8");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WithHmacSHA256", PREFIX + "$PBKDF2WithHmacSHA256UTF8");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WithHmacSHA384", PREFIX + "$PBKDF2WithHmacSHA384UTF8");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WithHmacSHA512", PREFIX + "$PBKDF2WithHmacSHA512UTF8");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA1AndAES_128", PREFIX + "$PBEWithHmacSHA1AndAES_128");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA224AndAES_128", PREFIX + "$PBEWithHmacSHA224AndAES_128");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA256AndAES_128", PREFIX + "$PBEWithHmacSHA256AndAES_128");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA384AndAES_128", PREFIX + "$PBEWithHmacSHA384AndAES_128");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA512AndAES_128", PREFIX + "$PBEWithHmacSHA512AndAES_128");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA1AndAES_256", PREFIX + "$PBEWithHmacSHA1AndAES_256");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA224AndAES_256", PREFIX + "$PBEWithHmacSHA224AndAES_256");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA256AndAES_256", PREFIX + "$PBEWithHmacSHA256AndAES_256");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA384AndAES_256", PREFIX + "$PBEWithHmacSHA384AndAES_256");
            provider.addAlgorithm("SecretKeyFactory.PBEWithHmacSHA512AndAES_256", PREFIX + "$PBEWithHmacSHA512AndAES_256");
            provider.addAlgorithm("SecretKeyFactory.PBKDF2WithHmacSHA1And8BIT", PREFIX + "$PBKDF2WithHmacSHA18BIT");
            // END Android-added: Android versions of algorithms.
        }
    }
}
