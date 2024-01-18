package org.bouncycastle.jcajce.provider.asymmetric;

// BEGIN android-added
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.dh.KeyFactorySpi;
// END android-added
import org.bouncycastle.jcajce.provider.config.ConfigurableProvider;
import org.bouncycastle.jcajce.provider.util.AsymmetricAlgorithmProvider;
// BEGIN android-added
import org.bouncycastle.jcajce.provider.util.AsymmetricKeyInfoConverter;
// END android-added

public class DH
{
    private static final String PREFIX = "org.bouncycastle.jcajce.provider.asymmetric" + ".dh.";

    public static class Mappings
        extends AsymmetricAlgorithmProvider
    {
        public Mappings()
        {
        }

        public void configure(ConfigurableProvider provider)
        {
            provider.addAlgorithm("KeyPairGenerator.DH", PREFIX + "KeyPairGeneratorSpi");
            provider.addAlgorithm("Alg.Alias.KeyPairGenerator.DIFFIEHELLMAN", "DH");

            provider.addAlgorithm("KeyAgreement.DH", PREFIX + "KeyAgreementSpi");
            provider.addAlgorithm("Alg.Alias.KeyAgreement.DIFFIEHELLMAN", "DH");

            provider.addAlgorithm("KeyFactory.DH", PREFIX + "KeyFactorySpi");
            provider.addAlgorithm("Alg.Alias.KeyFactory.DIFFIEHELLMAN", "DH");
            // BEGIN android-added
            AsymmetricKeyInfoConverter keyFact = new KeyFactorySpi();
            registerOid(provider, PKCSObjectIdentifiers.dhKeyAgreement, "DH", keyFact);
            // END android-added

            provider.addAlgorithm("AlgorithmParameters.DH", PREFIX + "AlgorithmParametersSpi");
            provider.addAlgorithm("Alg.Alias.AlgorithmParameters.DIFFIEHELLMAN", "DH");

            provider.addAlgorithm("Alg.Alias.AlgorithmParameterGenerator.DIFFIEHELLMAN", "DH");

            provider.addAlgorithm("AlgorithmParameterGenerator.DH", PREFIX + "AlgorithmParameterGeneratorSpi");
            
            // BEGIN android-removed
            // provider.addAlgorithm("Cipher.DHIES", PREFIX + "IESCipher$IES");
            // provider.addAlgorithm("Cipher.DHIESwithAES", PREFIX + "IESCipher$IESwithAES");
            // provider.addAlgorithm("Cipher.DHIESWITHAES", PREFIX + "IESCipher$IESwithAES");
            // provider.addAlgorithm("Cipher.DHIESWITHDESEDE", PREFIX + "IESCipher$IESwithDESede");
            // END android-removed
        }
    }
}
