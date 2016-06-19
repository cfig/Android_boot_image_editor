package org.bouncycastle.jcajce.provider.symmetric;

import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.crypto.CipherKeyGenerator;
import org.bouncycastle.crypto.engines.BlowfishEngine;
// BEGIN android-removed
// import org.bouncycastle.crypto.macs.CMac;
// END android-removed
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.jcajce.provider.config.ConfigurableProvider;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseBlockCipher;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseKeyGenerator;
// BEGIN android-removed
// import org.bouncycastle.jcajce.provider.symmetric.util.BaseMac;
// END android-removed
import org.bouncycastle.jcajce.provider.symmetric.util.IvAlgorithmParameters;
import org.bouncycastle.jcajce.provider.util.AlgorithmProvider;

public final class Blowfish
{
    private Blowfish()
    {
    }
    
    public static class ECB
        extends BaseBlockCipher
    {
        public ECB()
        {
            super(new BlowfishEngine());
        }
    }

    public static class CBC
        extends BaseBlockCipher
    {
        public CBC()
        {
            super(new CBCBlockCipher(new BlowfishEngine()), 64);
        }
    }

    // BEGIN android-removed
    // public static class CMAC
    //     extends BaseMac
    // {
    //     public CMAC()
    //     {
    //         super(new CMac(new BlowfishEngine()));
    //     }
    // }
    // END android-removed

    public static class KeyGen
        extends BaseKeyGenerator
    {
        public KeyGen()
        {
            super("Blowfish", 128, new CipherKeyGenerator());
        }
    }

    public static class AlgParams
        extends IvAlgorithmParameters
    {
        protected String engineToString()
        {
            return "Blowfish IV";
        }
    }

    public static class Mappings
        extends AlgorithmProvider
    {
        private static final String PREFIX = Blowfish.class.getName();

        public Mappings()
        {
        }

        public void configure(ConfigurableProvider provider)
        {
            // BEGIN android-removed
            // provider.addAlgorithm("Mac.BLOWFISHCMAC", PREFIX + "$CMAC");
            // END android-removed
            provider.addAlgorithm("Cipher.BLOWFISH", PREFIX + "$ECB");
            // BEGIN android-removed
            // provider.addAlgorithm("Cipher", MiscObjectIdentifiers.cryptlib_algorithm_blowfish_CBC, PREFIX + "$CBC");
            // END android-removed
            provider.addAlgorithm("KeyGenerator.BLOWFISH", PREFIX + "$KeyGen");
            provider.addAlgorithm("Alg.Alias.KeyGenerator", MiscObjectIdentifiers.cryptlib_algorithm_blowfish_CBC, "BLOWFISH");
            provider.addAlgorithm("AlgorithmParameters.BLOWFISH", PREFIX + "$AlgParams");
            provider.addAlgorithm("Alg.Alias.AlgorithmParameters", MiscObjectIdentifiers.cryptlib_algorithm_blowfish_CBC, "BLOWFISH");

        }
    }
}
