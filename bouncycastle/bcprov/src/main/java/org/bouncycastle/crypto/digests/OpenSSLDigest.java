/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bouncycastle.crypto.digests;

import org.bouncycastle.crypto.ExtendedDigest;
import org.bouncycastle.jcajce.provider.keystore.bc.BcKeyStoreSpi;
import java.security.DigestException;
import java.security.MessageDigest;

/**
 * Implements the BouncyCastle Digest interface using OpenSSL's EVP API. This
 * must be an ExtendedDigest for {@link BcKeyStoreSpi} to be able to use it.
 */
public class OpenSSLDigest implements ExtendedDigest {
    private final MessageDigest delegate;

    private final int byteSize;

    public OpenSSLDigest(String algorithm, int byteSize) {
        try {
            delegate = MessageDigest.getInstance(algorithm, "AndroidOpenSSL");
            this.byteSize = byteSize;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getAlgorithmName() {
        return delegate.getAlgorithm();
    }

    public int getDigestSize() {
        return delegate.getDigestLength();
    }

    public int getByteLength() {
        return byteSize;
    }

    public void reset() {
        delegate.reset();
    }

    public void update(byte in) {
        delegate.update(in);
    }

    public void update(byte[] in, int inOff, int len) {
        delegate.update(in, inOff, len);
    }

    public int doFinal(byte[] out, int outOff) {
        try {
            return delegate.digest(out, outOff, out.length - outOff);
        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
    }

    public static class MD5 extends OpenSSLDigest {
        public MD5() { super("MD5", 64); }
    }

    public static class SHA1 extends OpenSSLDigest {
        public SHA1() { super("SHA-1", 64); }
    }

    public static class SHA224 extends OpenSSLDigest {
        public SHA224() { super("SHA-224", 64); }
    }

    public static class SHA256 extends OpenSSLDigest {
        public SHA256() { super("SHA-256", 64); }
    }

    public static class SHA384 extends OpenSSLDigest {
        public SHA384() { super("SHA-384", 128); }
    }

    public static class SHA512 extends OpenSSLDigest {
        public SHA512() { super("SHA-512", 128); }
    }
}
