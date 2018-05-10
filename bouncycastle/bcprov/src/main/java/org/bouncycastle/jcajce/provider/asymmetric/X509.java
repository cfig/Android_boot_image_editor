package org.bouncycastle.jcajce.provider.asymmetric;

import org.bouncycastle.jcajce.provider.config.ConfigurableProvider;
import org.bouncycastle.jcajce.provider.util.AsymmetricAlgorithmProvider;

/**
 * For some reason the class path project thinks that such a KeyFactory will exist.
 */
public class X509
{
    public static class Mappings
        extends AsymmetricAlgorithmProvider
    {
        public Mappings()
        {

        }

        public void configure(ConfigurableProvider provider)
        {
            // BEGIN Android-removed: Unsupported algorithms
            // provider.addAlgorithm("KeyFactory.X.509", "org.bouncycastle.jcajce.provider.asymmetric.x509.KeyFactory");
            // provider.addAlgorithm("Alg.Alias.KeyFactory.X509", "X.509");
            // END Android-removed: Unsupported algorithms

            //
            // certificate factories.
            //
            provider.addAlgorithm("CertificateFactory.X.509", "org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory");
            provider.addAlgorithm("Alg.Alias.CertificateFactory.X509", "X.509");
        }
    }
}
