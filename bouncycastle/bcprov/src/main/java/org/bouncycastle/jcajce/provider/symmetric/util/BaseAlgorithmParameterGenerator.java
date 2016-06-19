package org.bouncycastle.jcajce.provider.symmetric.util;

import java.security.AlgorithmParameterGeneratorSpi;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import org.bouncycastle.jcajce.util.BCJcaJceHelper;
import org.bouncycastle.jcajce.util.JcaJceHelper;

public abstract class BaseAlgorithmParameterGenerator
    extends AlgorithmParameterGeneratorSpi
{
    private final JcaJceHelper helper = new BCJcaJceHelper();

    protected SecureRandom  random;
    protected int           strength = 1024;

    public BaseAlgorithmParameterGenerator()
    {
    }

    protected final AlgorithmParameters createParametersInstance(String algorithm)
        throws NoSuchAlgorithmException, NoSuchProviderException
    {
        return helper.createAlgorithmParameters(algorithm);
    }

    protected void engineInit(
        int             strength,
        SecureRandom    random)
    {
        this.strength = strength;
        this.random = random;
    }
}
