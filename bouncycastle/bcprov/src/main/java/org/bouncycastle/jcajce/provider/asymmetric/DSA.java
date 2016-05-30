package org.bouncycastle.jcajce.provider.asymmetric;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.DSAUtil;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.KeyFactorySpi;
import org.bouncycastle.jcajce.provider.config.ConfigurableProvider;
import org.bouncycastle.jcajce.provider.util.AsymmetricAlgorithmProvider;
import org.bouncycastle.jcajce.provider.util.AsymmetricKeyInfoConverter;

public class DSA
{
    private static final String PREFIX = "org.bouncycastle.jcajce.provider.asymmetric" + ".dsa.";

    public static class Mappings
        extends AsymmetricAlgorithmProvider
    {
        public Mappings()
        {
        }
        
        public void configure(ConfigurableProvider provider)
        {
            provider.addAlgorithm("AlgorithmParameters.DSA", PREFIX + "AlgorithmParametersSpi");

            provider.addAlgorithm("AlgorithmParameterGenerator.DSA", PREFIX + "AlgorithmParameterGeneratorSpi");

            provider.addAlgorithm("KeyPairGenerator.DSA", PREFIX + "KeyPairGeneratorSpi");
            provider.addAlgorithm("KeyFactory.DSA", PREFIX + "KeyFactorySpi");

            // BEGIN android-changed
            provider.addAlgorithm("Signature.SHA1withDSA", PREFIX + "DSASigner$stdDSA");
            // END android-changed
            provider.addAlgorithm("Signature.NONEWITHDSA", PREFIX + "DSASigner$noneDSA");

            provider.addAlgorithm("Alg.Alias.Signature.RAWDSA", "NONEWITHDSA");

            // BEGIN android-removed
            // provider.addAlgorithm("Signature.DETDSA", PREFIX + "DSASigner$detDSA");
            // provider.addAlgorithm("Signature.SHA1WITHDETDSA", PREFIX + "DSASigner$detDSA");
            // provider.addAlgorithm("Signature.SHA224WITHDETDSA", PREFIX + "DSASigner$detDSA224");
            // provider.addAlgorithm("Signature.SHA256WITHDETDSA", PREFIX + "DSASigner$detDSA256");
            // provider.addAlgorithm("Signature.SHA384WITHDETDSA", PREFIX + "DSASigner$detDSA384");
            // provider.addAlgorithm("Signature.SHA512WITHDETDSA", PREFIX + "DSASigner$detDSA512");
            // END android-removed

            addSignatureAlgorithm(provider, "SHA224", "DSA", PREFIX + "DSASigner$dsa224", NISTObjectIdentifiers.dsa_with_sha224);
            addSignatureAlgorithm(provider, "SHA256", "DSA", PREFIX + "DSASigner$dsa256", NISTObjectIdentifiers.dsa_with_sha256);
            // BEGIN android-removed
            // addSignatureAlgorithm(provider, "SHA384", "DSA", PREFIX + "DSASigner$dsa384", NISTObjectIdentifiers.dsa_with_sha384);
            // addSignatureAlgorithm(provider, "SHA512", "DSA", PREFIX + "DSASigner$dsa512", NISTObjectIdentifiers.dsa_with_sha512);
            // END android-removed

            // BEGIN android-added
            provider.addAlgorithm("Alg.Alias.Signature.DSA", "SHA1withDSA");
            // END android-added
            // BEGIN android-changed
            provider.addAlgorithm("Alg.Alias.Signature.SHA/DSA", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.SHA1withDSA", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.SHA1WITHDSA", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.1.3.14.3.2.26with1.2.840.10040.4.1", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.1.3.14.3.2.26with1.2.840.10040.4.3", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.DSAwithSHA1", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.DSAWITHSHA1", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.SHA1WithDSA", "SHA1withDSA");
            provider.addAlgorithm("Alg.Alias.Signature.DSAWithSHA1", "SHA1withDSA");
            // END android-changed

            // BEGIN android-removed
            // provider.addAlgorithm("Alg.Alias.Signature.1.2.840.10040.4.3", "DSA");
            // END android-removed

            AsymmetricKeyInfoConverter keyFact = new KeyFactorySpi();

            for (int i = 0; i != DSAUtil.dsaOids.length; i++)
            {
                // BEGIN android-changed
                provider.addAlgorithm("Alg.Alias.Signature." + DSAUtil.dsaOids[i], "SHA1withDSA");
                // END android-changed

                registerOid(provider, DSAUtil.dsaOids[i], "DSA", keyFact);
                registerOidAlgorithmParameters(provider, DSAUtil.dsaOids[i], "DSA");
            }
        }
    }
}
