package org.bouncycastle.jcajce.util;

import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
// BEGIN android-removed
// import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
// import org.bouncycastle.asn1.gnu.GNUObjectIdentifiers;
// import org.bouncycastle.asn1.iso.ISOIECObjectIdentifiers;
// END android-removed
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
// BEGIN android-removed
// import org.bouncycastle.asn1.teletrust.TeleTrusTObjectIdentifiers;
// END android-removed

public class MessageDigestUtils
{
    private static Map<ASN1ObjectIdentifier, String> digestOidMap = new HashMap<ASN1ObjectIdentifier, String>();

    static
    {
        // BEGIN android-removed
        // digestOidMap.put(PKCSObjectIdentifiers.md2, "MD2");
        // digestOidMap.put(PKCSObjectIdentifiers.md4, "MD4");
        // END android-removed
        digestOidMap.put(PKCSObjectIdentifiers.md5, "MD5");
        digestOidMap.put(OIWObjectIdentifiers.idSHA1, "SHA-1");
        digestOidMap.put(NISTObjectIdentifiers.id_sha224, "SHA-224");
        digestOidMap.put(NISTObjectIdentifiers.id_sha256, "SHA-256");
        digestOidMap.put(NISTObjectIdentifiers.id_sha384, "SHA-384");
        digestOidMap.put(NISTObjectIdentifiers.id_sha512, "SHA-512");
        // BEGIN android-removed
        // digestOidMap.put(TeleTrusTObjectIdentifiers.ripemd128, "RIPEMD-128");
        // digestOidMap.put(TeleTrusTObjectIdentifiers.ripemd160, "RIPEMD-160");
        // digestOidMap.put(TeleTrusTObjectIdentifiers.ripemd256, "RIPEMD-128");
        // digestOidMap.put(ISOIECObjectIdentifiers.ripemd128, "RIPEMD-128");
        // digestOidMap.put(ISOIECObjectIdentifiers.ripemd160, "RIPEMD-160");
        // digestOidMap.put(CryptoProObjectIdentifiers.gostR3411, "GOST3411");
        // digestOidMap.put(GNUObjectIdentifiers.Tiger_192, "Tiger");
        // digestOidMap.put(ISOIECObjectIdentifiers.whirlpool, "Whirlpool");
        // END android-removed
    }

    /**
     * Attempt to find a standard JCA name for the digest represented by the passed in OID.
     *
     * @param digestAlgOID the OID of the digest algorithm of interest.
     * @return a string representing the standard name - the OID as a string if none available.
     */
    public static String getDigestName(ASN1ObjectIdentifier digestAlgOID)
    {
        String name = (String)digestOidMap.get(digestAlgOID);  // for pre 1.5 JDK
        if (name != null)
        {
            return name;
        }

        return digestAlgOID.getId();
    }
}
