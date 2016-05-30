package org.bouncycastle.jce.provider;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.Enumeration;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPrivateKeySpec;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.DHParameter;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.DHDomainParameters;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.params.DHPrivateKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.util.PKCS12BagAttributeCarrierImpl;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;

public class JCEDHPrivateKey
    implements DHPrivateKey, PKCS12BagAttributeCarrier
{
    static final long serialVersionUID = 311058815616901812L;
    
    BigInteger      x;

    private DHParameterSpec dhSpec;
    private PrivateKeyInfo  info;

    private PKCS12BagAttributeCarrier attrCarrier = new PKCS12BagAttributeCarrierImpl();

    protected JCEDHPrivateKey()
    {
    }

    JCEDHPrivateKey(
        DHPrivateKey    key)
    {
        this.x = key.getX();
        this.dhSpec = key.getParams();
    }

    JCEDHPrivateKey(
        DHPrivateKeySpec    spec)
    {
        this.x = spec.getX();
        this.dhSpec = new DHParameterSpec(spec.getP(), spec.getG());
    }

    JCEDHPrivateKey(
        PrivateKeyInfo  info)
        throws IOException
    {
        ASN1Sequence    seq = ASN1Sequence.getInstance(info.getAlgorithmId().getParameters());
        ASN1Integer      derX = ASN1Integer.getInstance(info.parsePrivateKey());
        ASN1ObjectIdentifier id = info.getAlgorithmId().getAlgorithm();

        this.info = info;
        this.x = derX.getValue();

        if (id.equals(PKCSObjectIdentifiers.dhKeyAgreement))
        {
            DHParameter params = DHParameter.getInstance(seq);

            if (params.getL() != null)
            {
                this.dhSpec = new DHParameterSpec(params.getP(), params.getG(), params.getL().intValue());
            }
            else
            {
                this.dhSpec = new DHParameterSpec(params.getP(), params.getG());
            }
        }
        else if (id.equals(X9ObjectIdentifiers.dhpublicnumber))
        {
            DHDomainParameters params = DHDomainParameters.getInstance(seq);

            this.dhSpec = new DHParameterSpec(params.getP().getValue(), params.getG().getValue());
        }
        else
        {
            throw new IllegalArgumentException("unknown algorithm type: " + id);
        }
    }

    JCEDHPrivateKey(
        DHPrivateKeyParameters  params)
    {
        this.x = params.getX();
        this.dhSpec = new DHParameterSpec(params.getParameters().getP(), params.getParameters().getG(), params.getParameters().getL());
    }

    public String getAlgorithm()
    {
        return "DH";
    }

    /**
     * return the encoding format we produce in getEncoded().
     *
     * @return the string "PKCS#8"
     */
    public String getFormat()
    {
        return "PKCS#8";
    }

    /**
     * Return a PKCS8 representation of the key. The sequence returned
     * represents a full PrivateKeyInfo object.
     *
     * @return a PKCS8 representation of the key.
     */
    public byte[] getEncoded()
    {
        try
        {
            if (info != null)
            {
                return info.getEncoded(ASN1Encoding.DER);
            }

            PrivateKeyInfo          info = new PrivateKeyInfo(new AlgorithmIdentifier(PKCSObjectIdentifiers.dhKeyAgreement, new DHParameter(dhSpec.getP(), dhSpec.getG(), dhSpec.getL())), new ASN1Integer(getX()));

            return info.getEncoded(ASN1Encoding.DER);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    public DHParameterSpec getParams()
    {
        return dhSpec;
    }

    public BigInteger getX()
    {
        return x;
    }

    private void readObject(
        ObjectInputStream   in)
        throws IOException, ClassNotFoundException
    {
        x = (BigInteger)in.readObject();

        this.dhSpec = new DHParameterSpec((BigInteger)in.readObject(), (BigInteger)in.readObject(), in.readInt());
    }

    private void writeObject(
        ObjectOutputStream  out)
        throws IOException
    {
        out.writeObject(this.getX());
        out.writeObject(dhSpec.getP());
        out.writeObject(dhSpec.getG());
        out.writeInt(dhSpec.getL());
    }

    public void setBagAttribute(
        ASN1ObjectIdentifier oid,
        ASN1Encodable attribute)
    {
        attrCarrier.setBagAttribute(oid, attribute);
    }

    public ASN1Encodable getBagAttribute(
        ASN1ObjectIdentifier oid)
    {
        return attrCarrier.getBagAttribute(oid);
    }

    public Enumeration getBagAttributeKeys()
    {
        return attrCarrier.getBagAttributeKeys();
    }
}
