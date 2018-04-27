/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.security.Security;
import java.util.Locale;

import org.bouncycastle.crypto.Digest;

/**
 * Level of indirection to let us select OpenSSLDigest implementations
 * for libcore but fallback to BouncyCastle ones on the RI.
 */
public final class AndroidDigestFactory {
    private static final AndroidDigestFactoryInterface CONSCRYPT;
    private static final AndroidDigestFactoryInterface BC;

    static {
        BC = new AndroidDigestFactoryBouncyCastle();
        if (Security.getProvider("AndroidOpenSSL") != null) {
            CONSCRYPT = new AndroidDigestFactoryOpenSSL();
        } else {
            if (System.getProperty("java.vendor", "").toLowerCase(Locale.US).contains("android")) {
                throw new AssertionError("Provider AndroidOpenSSL must exist");
            }
            CONSCRYPT = null;
        }
    }

    public static Digest getMD5() {
        if (CONSCRYPT != null) {
            try {
                return CONSCRYPT.getMD5();
            } catch (Exception ignored) {
            }
        }

        return BC.getMD5();
    }

    public static Digest getSHA1() {
        if (CONSCRYPT != null) {
            try {
                return CONSCRYPT.getSHA1();
            } catch (Exception ignored) {
            }
        }

        return BC.getSHA1();
    }

    public static Digest getSHA224() {
        if (CONSCRYPT != null) {
            try {
                return CONSCRYPT.getSHA224();
            } catch (Exception ignored) {
            }
        }

        return BC.getSHA224();
    }

    public static Digest getSHA256() {
        if (CONSCRYPT != null) {
            try {
                return CONSCRYPT.getSHA256();
            } catch (Exception ignored) {
            }
        }

        return BC.getSHA256();
    }

    public static Digest getSHA384() {
        if (CONSCRYPT != null) {
            try {
                return CONSCRYPT.getSHA384();
            } catch (Exception ignored) {
            }
        }

        return BC.getSHA384();
    }

    public static Digest getSHA512() {
        if (CONSCRYPT != null) {
            try {
                return CONSCRYPT.getSHA512();
            } catch (Exception ignored) {
            }
        }

        return BC.getSHA512();
    }
}
