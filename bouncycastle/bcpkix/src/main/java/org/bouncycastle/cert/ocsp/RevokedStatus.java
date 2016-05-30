package org.bouncycastle.cert.ocsp;

import java.util.Date;

import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ocsp.RevokedInfo;
import org.bouncycastle.asn1.x509.CRLReason;

/**
 * wrapper for the RevokedInfo object
 */
public class RevokedStatus
    implements CertificateStatus
{
    RevokedInfo info;

    public RevokedStatus(
        RevokedInfo info)
    {
        this.info = info;
    }
    
    public RevokedStatus(
        Date        revocationDate,
        int         reason)
    {
        this.info = new RevokedInfo(new ASN1GeneralizedTime(revocationDate), CRLReason.lookup(reason));
    }

    public Date getRevocationTime()
    {
        return OCSPUtils.extractDate(info.getRevocationTime());
    }

    public boolean hasRevocationReason()
    {
        return (info.getRevocationReason() != null);
    }

    /**
     * return the revocation reason. Note: this field is optional, test for it
     * with hasRevocationReason() first.
     * @return the revocation reason value.
     * @exception IllegalStateException if a reason is asked for and none is avaliable
     */
    public int getRevocationReason()
    {
        if (info.getRevocationReason() == null)
        {
            throw new IllegalStateException("attempt to get a reason where none is available");
        }

        return info.getRevocationReason().getValue().intValue();
    }
}
